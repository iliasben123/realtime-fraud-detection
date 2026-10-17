"""Tests unitaires des règles de détection (Spark local, données en mémoire)."""
from __future__ import annotations

import json
from datetime import datetime, timedelta, timezone

from pyspark.sql import Row
from pyspark.sql import functions as F

from spark_jobs.fraud_detection_job import parse_transactions
from spark_jobs.rules import (aggregate_velocity, apply_stateless_rules, build_stateless_rules,
                              explode_alerts, velocity_alerts)

T0 = datetime(2026, 1, 1, 12, 0, 0, tzinfo=timezone.utc)


def tx(**overrides) -> dict:
    """Transaction légitime par défaut, surchargée par les kwargs."""
    base = dict(
        transaction_id="00000000-0000-0000-0000-000000000001",
        event_time=T0,
        step=1,
        type="PAYMENT",
        amount=1_000.0,
        name_orig="C1",
        old_balance_orig=10_000.0,
        new_balance_orig=9_000.0,
        name_dest="M1",
        old_balance_dest=0.0,
        new_balance_dest=0.0,
        is_fraud=False,
        is_flagged_fraud=False,
    )
    base.update(overrides)
    return base


def score(spark, cfg, rows: list[dict]):
    df = spark.createDataFrame([Row(**r) for r in rows])
    return apply_stateless_rules(df, build_stateless_rules(cfg))


def triggered(spark, cfg, row: dict) -> set[str]:
    out = score(spark, cfg, [row]).select("triggered_rules").first()
    return set(out["triggered_rules"])


# ----------------------------------------------------------------------------
# Règles stateless
# ----------------------------------------------------------------------------
def test_legit_payment_triggers_nothing(spark, cfg):
    out = score(spark, cfg, [tx()]).first()
    assert out["triggered_rules"] == []
    assert out["is_suspicious"] is False
    assert float(out["risk_score"]) == 0


def test_high_amount(spark, cfg):
    row = tx(amount=250_000.0, old_balance_orig=1_000_000.0, new_balance_orig=750_000.0)
    assert triggered(spark, cfg, row) == {"HIGH_AMOUNT"}


def test_account_drain_transfer(spark, cfg):
    row = tx(type="TRANSFER", amount=50_000.0, old_balance_orig=50_000.0, new_balance_orig=0.0,
             name_dest="C9", old_balance_dest=1_000.0, new_balance_dest=51_000.0)
    assert triggered(spark, cfg, row) == {"ACCOUNT_DRAIN"}


def test_account_drain_not_for_payment(spark, cfg):
    row = tx(type="PAYMENT", amount=50_000.0, old_balance_orig=50_000.0, new_balance_orig=0.0)
    assert "ACCOUNT_DRAIN" not in triggered(spark, cfg, row)


def test_transfer_to_empty_account(spark, cfg):
    row = tx(type="TRANSFER", amount=5_000.0, old_balance_orig=20_000.0, new_balance_orig=15_000.0,
             name_dest="C9", old_balance_dest=0.0, new_balance_dest=0.0)
    assert triggered(spark, cfg, row) == {"TRANSFER_TO_EMPTY_ACCOUNT"}


def test_balance_inconsistency(spark, cfg):
    row = tx(type="CASH_OUT", amount=1_000.0, old_balance_orig=10_000.0, new_balance_orig=5_000.0,
             name_dest="C9", old_balance_dest=100.0, new_balance_dest=1_100.0)
    assert triggered(spark, cfg, row) == {"BALANCE_INCONSISTENCY"}


def test_cash_in_never_balance_inconsistent(spark, cfg):
    row = tx(type="CASH_IN", amount=1_000.0, old_balance_orig=10_000.0, new_balance_orig=11_000.0,
             name_dest="C9", old_balance_dest=100.0, new_balance_dest=100.0)
    assert "BALANCE_INCONSISTENCY" not in triggered(spark, cfg, row)


def test_flagged_by_source(spark, cfg):
    assert triggered(spark, cfg, tx(is_flagged_fraud=True)) == {"FLAGGED_BY_SOURCE"}


def test_paysim_fraud_pattern_combines_rules_and_caps_score(spark, cfg):
    # Fraude PaySim typique : TRANSFER qui vide le compte vers un compte mule, gros montant
    row = tx(type="TRANSFER", amount=1_000_000.0, old_balance_orig=1_000_000.0, new_balance_orig=0.0,
             name_dest="C9", old_balance_dest=0.0, new_balance_dest=0.0, is_flagged_fraud=True)
    out = score(spark, cfg, [row]).first()
    assert set(out["triggered_rules"]) == {
        "HIGH_AMOUNT", "ACCOUNT_DRAIN", "TRANSFER_TO_EMPTY_ACCOUNT", "FLAGGED_BY_SOURCE"}
    # max(85) + 3 règles supplémentaires * 10 = 115 -> borné à 100
    assert float(out["risk_score"]) == 100
    assert out["is_suspicious"] is True


def test_risk_score_single_rule_equals_rule_score(spark, cfg):
    row = tx(amount=250_000.0, old_balance_orig=1_000_000.0, new_balance_orig=750_000.0)
    assert float(score(spark, cfg, [row]).first()["risk_score"]) == 60


def test_explode_alerts_one_row_per_rule(spark, cfg):
    rules = build_stateless_rules(cfg)
    rows = [
        tx(transaction_id="a", type="TRANSFER", amount=300_000.0, old_balance_orig=300_000.0,
           new_balance_orig=0.0, name_dest="C9", old_balance_dest=1.0, new_balance_dest=300_001.0),
        tx(transaction_id="b"),   # légitime -> aucune alerte
    ]
    alerts = explode_alerts(score(spark, cfg, rows), rules)
    collected = {(r["transaction_id"], r["rule_name"], r["severity"], float(r["risk_score"]))
                 for r in alerts.collect()}
    assert collected == {("a", "HIGH_AMOUNT", "HIGH", 60.0), ("a", "ACCOUNT_DRAIN", "CRITICAL", 85.0)}
    desc = alerts.filter("rule_name = 'ACCOUNT_DRAIN'").first()["description"]
    assert "TRANSFER de 300000.00" in desc


# ----------------------------------------------------------------------------
# Fenêtre glissante (vélocité) - évaluée en batch sur les mêmes expressions
# ----------------------------------------------------------------------------
def test_velocity_count_alert_on_burst(spark, cfg):
    burst = [tx(transaction_id=f"c1-{i}", name_orig="C_HOT", event_time=T0 + timedelta(seconds=10 * i))
             for i in range(4)]                                                  # 4 tx en 30 s (seuil 3)
    quiet = [tx(transaction_id=f"c2-{i}", name_orig="C_QUIET", event_time=T0 + timedelta(minutes=10 * i))
             for i in range(3)]                                                  # 3 tx étalées sur 20 min
    df = spark.createDataFrame([Row(**r) for r in burst + quiet])

    alerts = velocity_alerts(aggregate_velocity(df, cfg), cfg).collect()

    accounts = {a["name_orig"] for a in alerts}
    assert accounts == {"C_HOT"}
    assert {a["rule_name"] for a in alerts} == {"VELOCITY_COUNT"}
    # La dernière transaction du burst porte l'alerte
    assert all(a["transaction_id"] == "c1-3" for a in alerts)
    assert all(a["tx_count"] == 4 for a in alerts)
    # 5 fenêtres glissantes de 1 min de pas contiennent la rafale de 30 s
    assert len(alerts) == 5


def test_velocity_amount_requires_several_transactions(spark, cfg):
    single = [tx(transaction_id="big", name_orig="C_ONE", amount=900_000.0,
                 old_balance_orig=2_000_000.0, new_balance_orig=1_100_000.0, event_time=T0)]
    df = spark.createDataFrame([Row(**r) for r in single])
    assert velocity_alerts(aggregate_velocity(df, cfg), cfg).count() == 0


def test_velocity_amount_alert(spark, cfg):
    rows = [tx(transaction_id=f"x{i}", name_orig="C_BIG", amount=190_000.0,
               old_balance_orig=1_000_000.0, new_balance_orig=810_000.0,
               event_time=T0 + timedelta(seconds=i)) for i in range(3)]        # 570 000 cumulés (seuil 500 000)
    df = spark.createDataFrame([Row(**r) for r in rows])
    rules = {a["rule_name"] for a in velocity_alerts(aggregate_velocity(df, cfg), cfg).collect()}
    assert rules == {"VELOCITY_COUNT", "VELOCITY_AMOUNT"}


# ----------------------------------------------------------------------------
# Parsing du message Kafka
# ----------------------------------------------------------------------------
def test_parse_transactions_from_kafka_payload(spark):
    event = tx()
    event["event_time"] = "2026-01-01T12:00:00.000+00:00"
    kafka_rows = [
        Row(value=bytearray(json.dumps(event).encode()), timestamp=T0, partition=0, offset=1),
        Row(value=bytearray(b"not json"), timestamp=T0, partition=0, offset=2),   # ignoré
    ]
    raw = spark.createDataFrame(kafka_rows)
    parsed = parse_transactions(raw).collect()

    assert len(parsed) == 1
    p = parsed[0]
    assert p["transaction_id"] == event["transaction_id"]
    assert p["amount"] == 1_000.0
    assert p["event_time"] == T0.replace(tzinfo=None)      # Spark renvoie un datetime naïf en UTC
    assert p["kafka_offset"] == 1
