"""
Producer Kafka : lit un CSV au format PaySim et publie chaque ligne en JSON
dans le topic `transactions` pour simuler un flux temps réel.

Colonnes PaySim attendues :
  step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,nameDest,
  oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud

Usage (depuis le dossier ingestion/) :
  python -m producer.transaction_producer                 # config via .env
  python -m producer.transaction_producer --rate 200 --limit 10000
  python -m producer.transaction_producer --csv ../data/raw/sample.csv --rate 0
"""
from __future__ import annotations

import argparse
import csv
import json
import logging
import signal
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterator

from confluent_kafka import KafkaException, Producer

from .config import ProducerConfig

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-7s | %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("producer")

PAYSIM_COLUMNS = {
    "step", "type", "amount", "nameOrig", "oldbalanceOrg", "newbalanceOrig",
    "nameDest", "oldbalanceDest", "newbalanceDest", "isFraud", "isFlaggedFraud",
}


# ----------------------------------------------------------------------------
# Lecture / transformation du CSV
# ----------------------------------------------------------------------------
def read_transactions(csv_path: Path, limit: int = 0) -> Iterator[dict]:
    """Itère sur le CSV ligne par ligne (streaming, pas de chargement complet en mémoire)."""
    if not csv_path.exists():
        raise FileNotFoundError(
            f"CSV introuvable : {csv_path}\n"
            "  -> Télécharge PaySim (https://www.kaggle.com/datasets/ealaxi/paysim1) "
            "ou génère un échantillon : python -m producer.generate_sample_data"
        )

    with csv_path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        missing = PAYSIM_COLUMNS - set(reader.fieldnames or [])
        if missing:
            raise ValueError(f"Colonnes manquantes dans le CSV : {sorted(missing)}")

        for i, row in enumerate(reader, start=1):
            yield to_event(row)
            if limit and i >= limit:
                break


def to_event(row: dict) -> dict:
    """Normalise une ligne PaySim en événement JSON (snake_case, types corrects)."""
    return {
        "transaction_id": str(uuid.uuid4()),
        "event_time": datetime.now(timezone.utc).isoformat(timespec="milliseconds"),
        "step": int(row["step"]),
        "type": row["type"].strip().upper(),
        "amount": float(row["amount"]),
        "name_orig": row["nameOrig"],
        "old_balance_orig": float(row["oldbalanceOrg"]),
        "new_balance_orig": float(row["newbalanceOrig"]),
        "name_dest": row["nameDest"],
        "old_balance_dest": float(row["oldbalanceDest"]),
        "new_balance_dest": float(row["newbalanceDest"]),
        "is_fraud": row["isFraud"].strip() == "1",
        "is_flagged_fraud": row["isFlaggedFraud"].strip() == "1",
    }


# ----------------------------------------------------------------------------
# Producer Kafka
# ----------------------------------------------------------------------------
class TransactionProducer:
    def __init__(self, cfg: ProducerConfig) -> None:
        self.cfg = cfg
        self.producer = Producer(
            {
                "bootstrap.servers": cfg.bootstrap_servers,
                "client.id": cfg.client_id,
                "acks": "all",                    # durabilité : attendre l'ack du broker
                "enable.idempotence": True,       # pas de doublons en cas de retry
                "linger.ms": 10,                  # micro-batching
                "batch.num.messages": 1000,
                "compression.type": "snappy",
            }
        )
        self.sent = 0
        self.failed = 0
        self._stop = False
        signal.signal(signal.SIGINT, self._on_signal)
        signal.signal(signal.SIGTERM, self._on_signal)

    def _on_signal(self, *_: object) -> None:
        log.warning("Arrêt demandé, flush des messages en attente...")
        self._stop = True

    def _on_delivery(self, err, msg) -> None:
        if err is not None:
            self.failed += 1
            log.error("Échec de livraison [%s]: %s", msg.key(), err)
        else:
            self.sent += 1

    def run(self) -> None:
        cfg = self.cfg
        log.info("Kafka  : %s", cfg.bootstrap_servers)
        log.info("Topic  : %s", cfg.topic)
        log.info("CSV    : %s", cfg.csv_path)
        log.info("Débit  : %s msg/s", cfg.rate_per_sec or "max")
        log.info("Limite : %s lignes", cfg.limit or "toutes")

        interval = 1.0 / cfg.rate_per_sec if cfg.rate_per_sec > 0 else 0.0
        started = time.perf_counter()
        next_tick = started
        last_report = started

        try:
            for event in read_transactions(cfg.csv_path, cfg.limit):
                if self._stop:
                    break

                # Clé = compte émetteur : garantit l'ordre par compte au sein d'une partition
                # (nécessaire pour les fenêtres glissantes par client côté Spark)
                self.producer.produce(
                    topic=cfg.topic,
                    key=event["name_orig"],
                    value=json.dumps(event, separators=(",", ":")),
                    on_delivery=self._on_delivery,
                )
                self.producer.poll(0)   # déclenche les callbacks de livraison

                # Limitation de débit
                if interval:
                    next_tick += interval
                    sleep_for = next_tick - time.perf_counter()
                    if sleep_for > 0:
                        time.sleep(sleep_for)

                # Rapport périodique toutes les 5 s
                now = time.perf_counter()
                if now - last_report >= 5:
                    elapsed = now - started
                    log.info("envoyés=%d  échecs=%d  débit=%.1f msg/s",
                             self.sent, self.failed, self.sent / elapsed if elapsed else 0)
                    last_report = now

        except BufferError:
            log.error("File interne Kafka pleine, flush forcé")
            self.producer.flush()
        except KafkaException as exc:
            log.error("Erreur Kafka : %s", exc)
        finally:
            remaining = self.producer.flush(timeout=30)
            if remaining:
                log.warning("%d message(s) non livrés après flush", remaining)
            elapsed = time.perf_counter() - started
            log.info("Terminé : %d envoyés, %d échecs en %.1fs (%.1f msg/s)",
                     self.sent, self.failed, elapsed, self.sent / elapsed if elapsed else 0)


# ----------------------------------------------------------------------------
# CLI
# ----------------------------------------------------------------------------
def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Publie un CSV PaySim dans Kafka")
    p.add_argument("--csv", type=Path, help="chemin du CSV (défaut: CSV_PATH du .env)")
    p.add_argument("--rate", type=float, help="messages/seconde, 0 = max (défaut: PRODUCER_RATE)")
    p.add_argument("--limit", type=int, help="nombre max de lignes, 0 = toutes (défaut: PRODUCER_LIMIT)")
    p.add_argument("--topic", help="topic Kafka (défaut: KAFKA_TOPIC_TRANSACTIONS)")
    p.add_argument("--bootstrap", help="bootstrap servers (défaut: KAFKA_BOOTSTRAP_SERVERS)")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    base = ProducerConfig.from_env()
    cfg = ProducerConfig(
        bootstrap_servers=args.bootstrap or base.bootstrap_servers,
        topic=args.topic or base.topic,
        csv_path=(args.csv.resolve() if args.csv else base.csv_path),
        rate_per_sec=args.rate if args.rate is not None else base.rate_per_sec,
        limit=args.limit if args.limit is not None else base.limit,
    )
    try:
        TransactionProducer(cfg).run()
    except (FileNotFoundError, ValueError) as exc:
        log.error("%s", exc)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
