"""Configuration du producer, chargée depuis les variables d'environnement / fichier .env."""
from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

# Racine du projet : 2 niveaux au-dessus de ce fichier (ingestion/producer/config.py)
PROJECT_ROOT = Path(__file__).resolve().parents[2]
load_dotenv(PROJECT_ROOT / ".env")


@dataclass(frozen=True)
class ProducerConfig:
    bootstrap_servers: str
    topic: str
    csv_path: Path
    rate_per_sec: float   # 0 => aucune limitation de débit
    limit: int            # 0 => toutes les lignes du CSV
    client_id: str = "paysim-producer"

    @classmethod
    def from_env(cls) -> "ProducerConfig":
        csv_path = Path(os.getenv("CSV_PATH", "data/raw/paysim.csv"))
        if not csv_path.is_absolute():
            csv_path = PROJECT_ROOT / csv_path
        return cls(
            bootstrap_servers=os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
            topic=os.getenv("KAFKA_TOPIC_TRANSACTIONS", "transactions"),
            csv_path=csv_path,
            rate_per_sec=float(os.getenv("PRODUCER_RATE", "50")),
            limit=int(os.getenv("PRODUCER_LIMIT", "0")),
        )
