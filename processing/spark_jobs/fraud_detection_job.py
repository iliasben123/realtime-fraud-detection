"""
Job Spark Structured Streaming : détection de fraude en temps réel.

  Kafka `transactions` ──► parsing JSON ──► règles stateless ──► PostgreSQL (transactions + alertes)
                                     │                             └─► Kafka `fraud-alerts`
                                     └──► fenêtre glissante par compte ──► alertes de vélocité
                                                                             (PostgreSQL + Kafka)

Lancement (dans le conteneur `spark`, cf. docker-compose.yml) :
  spark-submit --master local[*] spark_jobs/fraud_detection_job.py

Lancement local (Spark + connecteur Kafka téléchargés par --packages) :
  spark-submit --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.3 spark_jobs/fraud_detection_job.py
"""
from __future__ import annotations

import logging
import os
import sys

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F

# Permet `spark-submit spark_jobs/fraud_detection_job.py` depuis processing/ sans PYTHONPATH
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from spark_jobs.config import JobConfig                                      # noqa: E402
from spark_jobs.rules import (aggregate_velocity, apply_stateless_rules,     # noqa: E402
                              build_stateless_rules, explode_alerts, velocity_alerts)
from spark_jobs.schema import TRANSACTION_SCHEMA                             # noqa: E402
from spark_jobs.sinks import publish_alerts, write_alerts, write_transactions  # noqa: E402

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-7s | %(name)s | %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("fraud-job")

ALERT_PAYLOAD = ["transaction_id", "rule_name", "description", "severity", "risk_score",
                 "event_time", "type", "amount", "name_orig", "name_dest", "is_fraud"]
VELOCITY_PAYLOAD = ["transaction_id", "rule_name", "description", "severity", "risk_score",
                    "name_orig", "window_start", "window_end", "tx_count", "total_amount"]


# ----------------------------------------------------------------------------
# Source
# ----------------------------------------------------------------------------
def build_spark(cfg: JobConfig) -> SparkSession:
    return (
        SparkSession.builder
        .appName("fraud-detection-streaming")
        .config("spark.sql.shuffle.partitions", cfg.shuffle_partitions)
        .config("spark.sql.session.timeZone", "UTC")
        .config("spark.sql.streaming.metricsEnabled", "true")
        .getOrCreate()
    )


def read_transactions(spark: SparkSession, cfg: JobConfig) -> DataFrame:
    raw = (
        spark.readStream
        .format("kafka")
        .option("kafka.bootstrap.servers", cfg.bootstrap_servers)
        .option("subscribe", cfg.topic_transactions)
        .option("startingOffsets", cfg.starting_offsets)
        .option("failOnDataLoss", "false")
        .option("maxOffsetsPerTrigger", 20_000)
        .load()
    )
    return parse_transactions(raw)


def parse_transactions(raw: DataFrame) -> DataFrame:
    """Désérialise la valeur Kafka (JSON) et type les colonnes. Ignore les messages malformés."""
    return (
        raw.select(
            F.from_json(F.col("value").cast("string"), TRANSACTION_SCHEMA).alias("tx"),
            F.col("timestamp").alias("kafka_time"),
            F.col("partition").alias("kafka_partition"),
            F.col("offset").alias("kafka_offset"))
        .select("tx.*", "kafka_time", "kafka_partition", "kafka_offset")
        .filter(F.col("transaction_id").isNotNull())
        .withColumn("event_time", F.coalesce(F.to_timestamp("event_time"), F.col("kafka_time")))
    )


# ----------------------------------------------------------------------------
# Micro-batches
# ----------------------------------------------------------------------------
def make_transactions_batch_handler(cfg: JobConfig):
    rules = build_stateless_rules(cfg)

    def handle(batch_df: DataFrame, batch_id: int) -> None:
        if batch_df.isEmpty():
            return
        batch_df = batch_df.persist()
        try:
            total = batch_df.count()
            write_transactions(batch_df, cfg)

            alerts = explode_alerts(batch_df, rules).persist()
            n_alerts = alerts.count()
            if n_alerts:
                write_alerts(alerts, cfg)
                publish_alerts(alerts, cfg, ALERT_PAYLOAD)
            alerts.unpersist()

            suspicious = batch_df.filter("is_suspicious").count()
            log.info("batch=%d transactions=%d suspectes=%d alertes=%d",
                     batch_id, total, suspicious, n_alerts)
        finally:
            batch_df.unpersist()

    return handle


def make_velocity_batch_handler(cfg: JobConfig):
    def handle(batch_df: DataFrame, batch_id: int) -> None:
        # Une même transaction clôt plusieurs fenêtres glissantes : une seule alerte par règle
        alerts = (velocity_alerts(batch_df, cfg)
                  .dropDuplicates(["transaction_id", "rule_name"])
                  .persist())
        try:
            n = alerts.count()
            if n:
                write_alerts(alerts, cfg)
                publish_alerts(alerts, cfg, VELOCITY_PAYLOAD)
                log.info("velocity batch=%d alertes=%d", batch_id, n)
        finally:
            alerts.unpersist()

    return handle


# ----------------------------------------------------------------------------
# Main
# ----------------------------------------------------------------------------
def main() -> None:
    cfg = JobConfig.from_env()
    log.info("Kafka      : %s (in=%s, out=%s)", cfg.bootstrap_servers, cfg.topic_transactions, cfg.topic_alerts)
    log.info("PostgreSQL : %s:%s/%s", cfg.pg_host, cfg.pg_port, cfg.pg_db)
    log.info("Checkpoint : %s", cfg.checkpoint_dir)
    log.info("Vélocité   : fenêtre=%s pas=%s watermark=%s seuils=%d tx / %.0f",
             cfg.velocity_window, cfg.velocity_slide, cfg.velocity_watermark,
             cfg.velocity_max_tx, cfg.velocity_max_amount)

    spark = build_spark(cfg)
    spark.sparkContext.setLogLevel("WARN")

    transactions = read_transactions(spark, cfg)
    rules = build_stateless_rules(cfg)
    scored = apply_stateless_rules(transactions, rules)

    # Requête 1 : scoring par transaction + persistance
    q_transactions = (
        scored.writeStream
        .queryName("transactions-scoring")
        .foreachBatch(make_transactions_batch_handler(cfg))
        .option("checkpointLocation", f"{cfg.checkpoint_dir}/transactions")
        .trigger(processingTime=cfg.trigger_interval)
        .start()
    )

    # Requête 2 : fenêtre glissante par compte (mode update : chaque fenêtre est ré-émise à chaque changement)
    q_velocity = (
        aggregate_velocity(transactions, cfg).writeStream
        .queryName("velocity-windows")
        .outputMode("update")
        .foreachBatch(make_velocity_batch_handler(cfg))
        .option("checkpointLocation", f"{cfg.checkpoint_dir}/velocity")
        .trigger(processingTime=cfg.velocity_trigger_interval)
        .start()
    )

    log.info("Streaming démarré : %s", [q.name for q in spark.streams.active])
    spark.streams.awaitAnyTermination()
    for q in (q_transactions, q_velocity):
        if q.exception():
            raise q.exception()


if __name__ == "__main__":
    main()
