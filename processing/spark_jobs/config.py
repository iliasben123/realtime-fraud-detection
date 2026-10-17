"""Configuration du job Spark, chargée depuis les variables d'environnement / fichier .env."""
from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

# Racine du projet : 2 niveaux au-dessus de ce fichier (processing/spark_jobs/config.py)
PROJECT_ROOT = Path(__file__).resolve().parents[2]
load_dotenv(PROJECT_ROOT / ".env")


@dataclass(frozen=True)
class JobConfig:
    # Kafka
    bootstrap_servers: str
    topic_transactions: str
    topic_alerts: str
    starting_offsets: str
    # PostgreSQL
    pg_host: str
    pg_port: int
    pg_db: str
    pg_user: str
    pg_password: str
    # Spark
    checkpoint_dir: str
    trigger_interval: str
    velocity_trigger_interval: str
    shuffle_partitions: int
    # Règles
    high_amount_threshold: float
    velocity_window: str
    velocity_slide: str
    velocity_watermark: str
    velocity_max_tx: int
    velocity_max_amount: float

    @property
    def pg_dsn(self) -> str:
        return (f"host={self.pg_host} port={self.pg_port} dbname={self.pg_db} "
                f"user={self.pg_user} password={self.pg_password}")

    @classmethod
    def from_env(cls) -> "JobConfig":
        return cls(
            bootstrap_servers=os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
            topic_transactions=os.getenv("KAFKA_TOPIC_TRANSACTIONS", "transactions"),
            topic_alerts=os.getenv("KAFKA_TOPIC_ALERTS", "fraud-alerts"),
            starting_offsets=os.getenv("SPARK_STARTING_OFFSETS", "latest"),
            pg_host=os.getenv("POSTGRES_HOST", "localhost"),
            pg_port=int(os.getenv("POSTGRES_PORT", "5432")),
            pg_db=os.getenv("POSTGRES_DB", "frauddb"),
            pg_user=os.getenv("POSTGRES_USER", "fraud"),
            pg_password=os.getenv("POSTGRES_PASSWORD", "fraud"),
            checkpoint_dir=os.getenv("SPARK_CHECKPOINT_DIR", str(PROJECT_ROOT / "processing/checkpoints")),
            trigger_interval=os.getenv("SPARK_TRIGGER_INTERVAL", "5 seconds"),
            velocity_trigger_interval=os.getenv("SPARK_VELOCITY_TRIGGER_INTERVAL", "10 seconds"),
            shuffle_partitions=int(os.getenv("SPARK_SHUFFLE_PARTITIONS", "6")),
            high_amount_threshold=float(os.getenv("RULE_HIGH_AMOUNT_THRESHOLD", "200000")),
            velocity_window=os.getenv("RULE_VELOCITY_WINDOW", "5 minutes"),
            velocity_slide=os.getenv("RULE_VELOCITY_SLIDE", "1 minute"),
            velocity_watermark=os.getenv("RULE_VELOCITY_WATERMARK", "2 minutes"),
            velocity_max_tx=int(os.getenv("RULE_VELOCITY_MAX_TX", "5")),
            velocity_max_amount=float(os.getenv("RULE_VELOCITY_MAX_AMOUNT", "500000")),
        )
