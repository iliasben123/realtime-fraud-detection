"""
Sinks du job : écriture PostgreSQL (idempotente, via psycopg2 par partition)
et publication des alertes dans le topic Kafka `fraud-alerts`.
"""
from __future__ import annotations

import logging
from typing import Iterable, Sequence

from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from .config import JobConfig
from .schema import ALERT_COLUMNS, TRANSACTION_COLUMNS

log = logging.getLogger("sinks")

SQL_INSERT_TRANSACTIONS = f"""
INSERT INTO fraud.transactions ({", ".join(TRANSACTION_COLUMNS)})
VALUES %s
ON CONFLICT (transaction_id) DO NOTHING
"""

# L'alerte n'est insérée que si la transaction référencée existe déjà (FK) ;
# la contrainte UNIQUE (transaction_id, rule_name) rend l'écriture idempotente
# (replays Spark, fenêtres glissantes mises à jour plusieurs fois).
SQL_INSERT_ALERTS = """
INSERT INTO fraud.fraud_alerts (transaction_id, rule_name, description, severity, risk_score)
SELECT v.transaction_id::uuid, v.rule_name, v.description,
       v.severity::fraud.alert_severity, v.risk_score::numeric
FROM (VALUES %s) AS v(transaction_id, rule_name, description, severity, risk_score)
WHERE EXISTS (SELECT 1 FROM fraud.transactions t WHERE t.transaction_id = v.transaction_id::uuid)
ON CONFLICT (transaction_id, rule_name) DO NOTHING
"""


MAX_RETRIES = 3
ALERT_KEY = ["transaction_id", "rule_name"]


def _insert_partition(rows: Iterable, dsn: str, sql: str, page_size: int = 1000) -> None:
    """
    Exécuté sur chaque partition (worker) : une connexion, insertion par lots.

    Les lignes sont triées sur leurs premières colonnes (clé) pour que les partitions
    concurrentes verrouillent les index dans le même ordre ; en cas d'interblocage
    PostgreSQL malgré tout, la transaction est rejouée (les INSERT sont idempotents).
    """
    import random
    import time

    import psycopg2
    from psycopg2 import errors

    from psycopg2.extras import execute_values

    batch: list[tuple] = sorted((tuple(r) for r in rows), key=lambda t: tuple(str(c) for c in t[:2]))
    if not batch:
        return

    # client_encoding explicite : l'image Spark n'a pas de locale UTF-8, sans cela les
    # descriptions accentuées seraient stockées en double encodage.
    conn = psycopg2.connect(dsn, client_encoding="UTF8")
    try:
        for attempt in range(1, MAX_RETRIES + 1):
            try:
                with conn, conn.cursor() as cur:
                    execute_values(cur, sql, batch, page_size=page_size)
                return
            except (errors.DeadlockDetected, errors.SerializationFailure):
                if attempt == MAX_RETRIES:
                    raise
                time.sleep(0.2 * attempt + random.random() * 0.3)
    finally:
        conn.close()


def write_transactions(df: DataFrame, cfg: JobConfig) -> None:
    dsn = cfg.pg_dsn
    (df.select(*TRANSACTION_COLUMNS)
       .foreachPartition(lambda rows: _insert_partition(rows, dsn, SQL_INSERT_TRANSACTIONS)))


def write_alerts(df: DataFrame, cfg: JobConfig) -> None:
    """
    Les alertes sont peu volumineuses : dédoublonnées sur (transaction, règle) puis écrites
    depuis une seule partition, ce qui supprime toute concurrence sur l'index unique.
    """
    dsn = cfg.pg_dsn
    (df.select(*ALERT_COLUMNS)
       .dropDuplicates(ALERT_KEY)
       .coalesce(1)
       .foreachPartition(lambda rows: _insert_partition(rows, dsn, SQL_INSERT_ALERTS)))


def publish_alerts(df: DataFrame, cfg: JobConfig, payload_columns: Sequence[str]) -> None:
    """Publie chaque alerte en JSON dans le topic `fraud-alerts` (clé = compte émetteur)."""
    (df.select(
            F.col("name_orig").cast("string").alias("key"),
            F.to_json(F.struct(*payload_columns)).alias("value"))
       .write
       .format("kafka")
       .option("kafka.bootstrap.servers", cfg.bootstrap_servers)
       .option("topic", cfg.topic_alerts)
       .save())
