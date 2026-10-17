"""
Entraînement batch du modèle de détection de fraude (Spark MLlib, gradient boosting).

  CSV PaySim ──► features (identiques au streaming) ──► GBTClassifier pondéré
             ──► évaluation sur jeu de test (AUC, précision / rappel par seuil)
             ──► comparaison avec les règles métier ──► registre versionné (models/fraud_gbt/<version>)

Lancement dans le conteneur Spark :
  spark-submit /app/spark_jobs/ml/train_model.py --csv /app/data/raw/paysim.csv --out /app/models/fraud_gbt
  (ou scripts/train_model.sh)
"""
from __future__ import annotations

import argparse
import logging
import os
import sys
from datetime import datetime, timezone

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from spark_jobs.config import JobConfig                                       # noqa: E402
from spark_jobs.ml import registry                                            # noqa: E402
from spark_jobs.ml.features import (LABEL_COL, NUMERIC_FEATURES, WEIGHT_COL,  # noqa: E402
                                    add_features, build_pipeline, normalize_paysim)
from spark_jobs.ml.scoring import apply_ml_scoring                            # noqa: E402
from spark_jobs.rules import apply_stateless_rules, build_stateless_rules     # noqa: E402

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)-7s | %(message)s", datefmt="%H:%M:%S")
log = logging.getLogger("train")

THRESHOLDS = [round(0.05 * i, 2) for i in range(1, 20)]      # 0.05 .. 0.95


# ----------------------------------------------------------------------------
# Évaluation
# ----------------------------------------------------------------------------
def precision_recall_by_threshold(scored: DataFrame, prob_col: str, label_col: str) -> list[dict]:
    """Précision / rappel / F1 pour chaque seuil, en une seule passe Spark."""
    aggs = []
    for t in THRESHOLDS:
        pred = F.col(prob_col) >= t
        pos = F.col(label_col) == 1
        aggs += [F.sum((pred & pos).cast("int")).alias(f"tp_{t}"),
                 F.sum((pred & ~pos).cast("int")).alias(f"fp_{t}"),
                 F.sum((~pred & pos).cast("int")).alias(f"fn_{t}")]
    row = scored.agg(*aggs).first()
    out = []
    for t in THRESHOLDS:
        tp, fp, fn = row[f"tp_{t}"], row[f"fp_{t}"], row[f"fn_{t}"]
        precision = tp / (tp + fp) if tp + fp else 0.0
        recall = tp / (tp + fn) if tp + fn else 0.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        out.append({"threshold": t, "tp": tp, "fp": fp, "fn": fn,
                    "precision": round(precision, 4), "recall": round(recall, 4), "f1": round(f1, 4)})
    return out


def choose_threshold(curve: list[dict]) -> dict:
    """
    Seuil maximisant le F1 ; si plusieurs seuils sont à égalité (plateau), on prend le milieu
    du plateau plutôt qu'une extrémité, plus robuste à la dérive des données.
    """
    best_f1 = max(m["f1"] for m in curve)
    plateau = [m for m in curve if m["f1"] == best_f1]
    return plateau[len(plateau) // 2]


def binary_metrics(df: DataFrame, pred_col: str, label_col: str) -> dict:
    row = df.agg(
        F.sum((F.col(pred_col) & (F.col(label_col) == 1)).cast("int")).alias("tp"),
        F.sum((F.col(pred_col) & (F.col(label_col) == 0)).cast("int")).alias("fp"),
        F.sum((~F.col(pred_col) & (F.col(label_col) == 1)).cast("int")).alias("fn"),
    ).first()
    tp, fp, fn = row["tp"], row["fp"], row["fn"]
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {"tp": tp, "fp": fp, "fn": fn, "precision": round(precision, 4),
            "recall": round(recall, 4), "f1": round(f1, 4)}


def rules_baseline(test: DataFrame, cfg: JobConfig) -> dict:
    """Performance des règles stateless seules sur le même jeu de test."""
    scored = apply_stateless_rules(test, build_stateless_rules(cfg))
    return binary_metrics(scored, "is_suspicious", LABEL_COL)


# ----------------------------------------------------------------------------
# Entraînement
# ----------------------------------------------------------------------------
def prepare(df: DataFrame) -> DataFrame:
    """Label entier + poids de classe (compense le déséquilibre ~0.1 % de fraudes)."""
    df = add_features(df).withColumn(LABEL_COL, F.col("is_fraud").cast("int"))
    counts = df.groupBy(LABEL_COL).count().collect()
    n = {r[LABEL_COL]: r["count"] for r in counts}
    n_pos, n_neg = n.get(1, 0), n.get(0, 0)
    if n_pos == 0:
        raise ValueError("Aucune fraude dans le jeu d'entraînement : impossible d'apprendre")
    pos_weight = min(n_neg / n_pos, 500.0)
    log.info("Jeu de données : %d légitimes, %d fraudes (poids fraude = %.1f)", n_neg, n_pos, pos_weight)
    return df.withColumn(WEIGHT_COL, F.when(F.col(LABEL_COL) == 1, F.lit(pos_weight)).otherwise(F.lit(1.0)))


def train_and_evaluate(spark: SparkSession, data: DataFrame, out_dir: str, cfg: JobConfig, *,
                       max_iter: int = 60, max_depth: int = 5, test_fraction: float = 0.2,
                       seed: int = 42, source: str = "unknown") -> registry.ModelInfo:
    from pyspark.ml.evaluation import BinaryClassificationEvaluator

    prepared = prepare(data).cache()
    train, test = prepared.randomSplit([1 - test_fraction, test_fraction], seed=seed)
    log.info("Entraînement GBT (maxIter=%d, maxDepth=%d) sur %d lignes...", max_iter, max_depth, train.count())
    model = build_pipeline(max_iter=max_iter, max_depth=max_depth, seed=seed).fit(train)

    log.info("Évaluation sur %d lignes de test...", test.count())
    predictions = model.transform(test).cache()
    auc_roc = BinaryClassificationEvaluator(labelCol=LABEL_COL, metricName="areaUnderROC").evaluate(predictions)
    auc_pr = BinaryClassificationEvaluator(labelCol=LABEL_COL, metricName="areaUnderPR").evaluate(predictions)

    from pyspark.ml.functions import vector_to_array
    with_prob = predictions.withColumn("ml_probability", vector_to_array(F.col("probability"))[1])
    curve = precision_recall_by_threshold(with_prob, "ml_probability", LABEL_COL)
    best = choose_threshold(curve)
    baseline = rules_baseline(test, cfg)

    gbt_model = model.stages[-1]
    assembler_inputs = NUMERIC_FEATURES + ["type_vec"]
    importances = gbt_model.featureImportances.toArray().tolist()
    # les colonnes one-hot du type sont regroupées en une seule importance
    n_numeric = len(NUMERIC_FEATURES)
    importance_by_feature = {name: round(importances[i], 4) for i, name in enumerate(NUMERIC_FEATURES)}
    importance_by_feature["type"] = round(sum(importances[n_numeric:]), 4)

    metadata = {
        "algorithm": "GBTClassifier",
        "params": {"maxIter": max_iter, "maxDepth": max_depth, "seed": seed, "test_fraction": test_fraction},
        "trained_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "source": source,
        "rows": {"train": train.count(), "test": test.count(),
                 "fraud_total": prepared.filter(F.col(LABEL_COL) == 1).count()},
        "features": assembler_inputs,
        "feature_importance": dict(sorted(importance_by_feature.items(), key=lambda kv: -kv[1])),
        "metrics": {"auc_roc": round(auc_roc, 4), "auc_pr": round(auc_pr, 4),
                    "at_threshold": best, "rules_baseline": baseline},
        "threshold": best["threshold"],
        "pr_curve": curve,
    }
    prepared.unpersist()
    predictions.unpersist()

    info = registry.register(model, out_dir, registry.new_version(), metadata)
    log.info("AUC-ROC=%.4f AUC-PR=%.4f | seuil=%.2f précision=%.3f rappel=%.3f F1=%.3f | règles : précision=%.3f rappel=%.3f",
             auc_roc, auc_pr, best["threshold"], best["precision"], best["recall"], best["f1"],
             baseline["precision"], baseline["recall"])
    return info


# ----------------------------------------------------------------------------
# CLI
# ----------------------------------------------------------------------------
def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Entraîne le modèle MLlib de détection de fraude")
    p.add_argument("--csv", required=True, help="CSV au format PaySim")
    p.add_argument("--out", default=None, help="répertoire du registre (défaut: ML_MODEL_DIR)")
    p.add_argument("--max-iter", type=int, default=60)
    p.add_argument("--max-depth", type=int, default=5)
    p.add_argument("--test-fraction", type=float, default=0.2)
    p.add_argument("--sample", type=float, default=1.0, help="fraction du CSV à utiliser (ex. 0.2 pour le vrai PaySim)")
    p.add_argument("--seed", type=int, default=42)
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    cfg = JobConfig.from_env()
    out_dir = args.out or cfg.ml_model_dir

    spark = (SparkSession.builder.appName("fraud-model-training")
             .config("spark.sql.shuffle.partitions", "8").getOrCreate())
    spark.sparkContext.setLogLevel("WARN")

    raw = spark.read.csv(args.csv, header=True, inferSchema=True)
    if args.sample < 1.0:
        raw = raw.sample(fraction=args.sample, seed=args.seed)
    data = normalize_paysim(raw)

    info = train_and_evaluate(spark, data, out_dir, cfg, max_iter=args.max_iter, max_depth=args.max_depth,
                              test_fraction=args.test_fraction, seed=args.seed, source=os.path.basename(args.csv))

    # Contrôle : le modèle rechargé depuis le registre score correctement un échantillon
    reloaded = registry.load(registry.resolve(out_dir))
    sample = apply_ml_scoring(data.limit(5), reloaded, info).select("type", "amount", "is_fraud", "ml_score", "model_version")
    sample.show(truncate=False)
    log.info("Version publiée : %s (seuil %.2f)", info.version, info.threshold)
    spark.stop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
