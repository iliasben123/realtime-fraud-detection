"""Tests du volet ML : features, entraînement / registre, scoring et intégration avec les règles."""
from __future__ import annotations

import json
from datetime import datetime, timezone

import pytest
from pyspark.sql import Row
from pyspark.sql import functions as F

from spark_jobs.ml import registry
from spark_jobs.ml.features import DERIVED_FEATURES, NUMERIC_FEATURES, add_features, normalize_paysim
from spark_jobs.ml.scoring import apply_ml_scoring, combine_risk_score, without_ml_scoring
from spark_jobs.ml.train_model import train_and_evaluate
from spark_jobs.rules import apply_stateless_rules, build_ml_rule, build_stateless_rules, explode_alerts
from spark_jobs.schema import TRANSACTION_COLUMNS

T0 = datetime(2026, 1, 1, 12, 0, 0, tzinfo=timezone.utc)


def legit(i: int, rng) -> dict:
    amount = round(rng.uniform(100, 50_000), 2)
    old = round(rng.uniform(amount, 500_000), 2)
    tx_type = rng.choice(["PAYMENT", "CASH_OUT", "TRANSFER", "CASH_IN", "DEBIT"])
    new = old + amount if tx_type == "CASH_IN" else old - amount
    return dict(transaction_id=f"l{i}", event_time=T0, step=i % 743 + 1, type=tx_type, amount=amount,
                name_orig=f"C{i}", old_balance_orig=old, new_balance_orig=round(new, 2),
                name_dest=("M" if tx_type == "PAYMENT" else "C") + str(10_000 + i),
                old_balance_dest=1000.0, new_balance_dest=1000.0 + amount,
                is_fraud=False, is_flagged_fraud=False)


def fraud(i: int, rng) -> dict:
    old = round(rng.uniform(10_000, 1_000_000), 2)
    return dict(transaction_id=f"f{i}", event_time=T0, step=i % 743 + 1,
                type=rng.choice(["TRANSFER", "CASH_OUT"]), amount=old,
                name_orig=f"F{i}", old_balance_orig=old, new_balance_orig=0.0,
                name_dest=f"C{90_000 + i}", old_balance_dest=0.0, new_balance_dest=0.0,
                is_fraud=True, is_flagged_fraud=old > 200_000)


@pytest.fixture(scope="module")
def dataset(spark):
    import random
    rng = random.Random(7)
    rows = [legit(i, rng) for i in range(600)] + [fraud(i, rng) for i in range(60)]
    return spark.createDataFrame([Row(**r) for r in rows])


@pytest.fixture(scope="module")
def trained(spark, cfg, dataset, tmp_path_factory):
    out = tmp_path_factory.mktemp("registry")
    info = train_and_evaluate(spark, dataset, str(out), cfg, max_iter=5, max_depth=3, source="synthetic")
    return out, info


# ----------------------------------------------------------------------------
# Features
# ----------------------------------------------------------------------------
def test_add_features_values(spark):
    row = Row(step=25, type="TRANSFER", amount=1_000.0, name_orig="C1", old_balance_orig=4_000.0,
              new_balance_orig=2_500.0, name_dest="M9", old_balance_dest=10.0, new_balance_dest=10.0,
              is_fraud=False, is_flagged_fraud=True)
    f = add_features(spark.createDataFrame([row])).first()

    assert f["error_balance_orig"] == pytest.approx(500.0)       # 4000 - 1000 - 2500
    assert f["error_balance_dest"] == pytest.approx(1_000.0)     # 10 + 1000 - 10
    assert f["drain_ratio"] == pytest.approx(0.25)
    assert f["amount_over_balance"] == 0
    assert f["orig_emptied"] == 0
    assert f["dest_is_merchant"] == 1
    assert f["dest_balance_unchanged"] == 1
    assert f["flagged_by_source"] == 1
    assert f["hour_of_day"] == 1


def test_cash_in_error_balance_uses_credit_formula(spark):
    row = Row(step=1, type="CASH_IN", amount=500.0, name_orig="C1", old_balance_orig=1_000.0,
              new_balance_orig=1_500.0, name_dest="C2", old_balance_dest=0.0, new_balance_dest=0.0,
              is_fraud=False, is_flagged_fraud=False)
    assert add_features(spark.createDataFrame([row])).first()["error_balance_orig"] == pytest.approx(0.0)


def test_features_have_no_nulls(spark, dataset):
    f = add_features(dataset)
    nulls = f.select([F.sum(F.col(c).isNull().cast("int")).alias(c) for c in NUMERIC_FEATURES]).first().asDict()
    assert all(v == 0 for v in nulls.values()), nulls


def test_normalize_paysim_matches_event_schema(spark):
    raw = spark.createDataFrame([Row(step="1", type=" transfer", amount="181.0", nameOrig="C1",
                                     oldbalanceOrg="181.0", newbalanceOrig="0.0", nameDest="C2",
                                     oldbalanceDest="0.0", newbalanceDest="0.0", isFraud="1", isFlaggedFraud="0")])
    n = normalize_paysim(raw).first()
    assert n["type"] == "TRANSFER"
    assert n["is_fraud"] is True and n["is_flagged_fraud"] is False
    assert n["old_balance_orig"] == 181.0 and n["step"] == 1


# ----------------------------------------------------------------------------
# Entraînement + registre
# ----------------------------------------------------------------------------
def test_training_registers_model_with_metadata(trained):
    out, info = trained
    assert (out / "latest").read_text().strip() == info.version
    assert (out / info.version / "model").is_dir()
    meta = json.loads((out / info.version / "metadata.json").read_text(encoding="utf-8"))
    assert meta["algorithm"] == "GBTClassifier"
    assert 0 <= meta["metrics"]["auc_pr"] <= 1
    assert 0 < meta["threshold"] < 1
    assert set(meta["metrics"]["at_threshold"]) >= {"precision", "recall", "f1", "threshold"}
    assert set(meta["metrics"]["rules_baseline"]) >= {"precision", "recall", "f1"}
    assert set(meta["feature_importance"]) == set(NUMERIC_FEATURES) | {"type"}
    # le pattern synthétique est parfaitement séparable : le modèle doit l'apprendre
    assert meta["metrics"]["at_threshold"]["recall"] >= 0.9
    assert meta["metrics"]["at_threshold"]["precision"] >= 0.9


def test_registry_resolve_and_missing(trained, tmp_path):
    out, info = trained
    resolved = registry.resolve(out)
    assert resolved is not None and resolved.version == info.version
    assert resolved.threshold == info.threshold
    assert registry.resolve(tmp_path / "empty") is None
    assert registry.resolve(out, "v_inexistante") is None


# ----------------------------------------------------------------------------
# Scoring streaming + intégration règles
# ----------------------------------------------------------------------------
def test_scoring_adds_ml_columns_and_keeps_event_columns(spark, trained, dataset):
    out, info = trained
    model = registry.load(registry.resolve(out))
    scored = apply_ml_scoring(dataset, model, info)

    assert {"ml_probability", "ml_score", "model_version"} <= set(scored.columns)
    assert not (set(DERIVED_FEATURES) & set(scored.columns))          # features dérivées retirées
    assert {"amount", "old_balance_orig", "name_dest", "is_fraud"} <= set(scored.columns)
    probs = scored.groupBy("is_fraud").agg(F.avg("ml_probability").alias("p")).collect()
    p = {r["is_fraud"]: r["p"] for r in probs}
    assert p[True] > 0.8 > 0.2 > p[False]
    assert scored.first()["model_version"] == info.version


def test_ml_rule_and_combined_score_feed_alerts(spark, cfg, trained):
    out, info = trained
    model = registry.load(registry.resolve(out))
    import random
    rng = random.Random(3)
    # fraude ML "pure" : TRANSFER vidant le compte mais sans déclencher ACCOUNT_DRAIN (new > 0.01)
    rows = [fraud(1, rng), legit(2, rng)]
    df = spark.createDataFrame([Row(**r) for r in rows])

    rules = build_stateless_rules(cfg) + [build_ml_rule(info.threshold, info.version)]
    scored = combine_risk_score(apply_stateless_rules(apply_ml_scoring(df, model, info), rules))
    by_id = {r["transaction_id"]: r for r in scored.collect()}

    assert "ML_HIGH_RISK" in by_id["f1"]["triggered_rules"]
    assert float(by_id["f1"]["risk_score"]) >= float(by_id["f1"]["ml_score"])
    assert by_id["l2"]["triggered_rules"] == []
    assert by_id["l2"]["is_suspicious"] is False

    alerts = explode_alerts(scored, rules).collect()
    ml_alerts = [a for a in alerts if a["rule_name"] == "ML_HIGH_RISK"]
    assert len(ml_alerts) == 1
    assert info.version in ml_alerts[0]["description"]
    assert ml_alerts[0]["model_version"] == info.version

    # toutes les colonnes attendues par PostgreSQL sont présentes
    assert set(TRANSACTION_COLUMNS) <= set(scored.columns)


def test_without_model_keeps_output_schema(spark, cfg, dataset):
    scored = combine_risk_score(apply_stateless_rules(without_ml_scoring(dataset), build_stateless_rules(cfg)))
    assert set(TRANSACTION_COLUMNS) <= set(scored.columns)
    assert scored.filter(F.col("ml_score").isNotNull()).count() == 0
    # sans ML, les fraudes synthétiques sont attrapées par ACCOUNT_DRAIN
    assert scored.filter("is_fraud and not is_suspicious").count() == 0
