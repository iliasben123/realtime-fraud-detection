"""
Ingénierie des features : UNE SEULE définition, utilisée à l'identique par
l'entraînement batch (train_model.py) et par le scoring en streaming (scoring.py).
Toutes les features sont calculables à partir d'une transaction isolée (pas d'état),
ce qui les rend applicables sur un flux.
"""
from __future__ import annotations

from pyspark.ml import Pipeline
from pyspark.ml.classification import GBTClassifier
from pyspark.ml.feature import OneHotEncoder, StringIndexer, VectorAssembler
from pyspark.sql import DataFrame
from pyspark.sql import functions as F

LABEL_COL = "label"
WEIGHT_COL = "weight"

# Colonnes numériques produites par add_features (le type est encodé one-hot par le pipeline)
NUMERIC_FEATURES = [
    "amount",
    "log_amount",
    "old_balance_orig",
    "new_balance_orig",
    "old_balance_dest",
    "new_balance_dest",
    "error_balance_orig",      # incohérence du solde émetteur : très discriminant sur PaySim
    "error_balance_dest",      # incohérence du solde destinataire
    "drain_ratio",             # part du solde émetteur déplacée
    "amount_over_balance",     # montant supérieur au solde disponible
    "orig_emptied",            # compte émetteur vidé
    "dest_is_merchant",        # destinataire marchand (M...) : jamais fraudé dans PaySim
    "dest_balance_unchanged",  # solde destinataire inchangé (compte mule / non tracé)
    "flagged_by_source",
    "hour_of_day",
]

# Features dérivées (absentes de l'événement d'origine) : retirées après scoring
RAW_EVENT_COLUMNS = {"amount", "old_balance_orig", "new_balance_orig", "old_balance_dest", "new_balance_dest"}
DERIVED_FEATURES = [c for c in NUMERIC_FEATURES if c not in RAW_EVENT_COLUMNS]

# Colonnes PaySim brutes (CSV) -> colonnes de l'événement Kafka
PAYSIM_TO_EVENT = {
    "nameOrig": "name_orig", "oldbalanceOrg": "old_balance_orig", "newbalanceOrig": "new_balance_orig",
    "nameDest": "name_dest", "oldbalanceDest": "old_balance_dest", "newbalanceDest": "new_balance_dest",
    "isFraud": "is_fraud", "isFlaggedFraud": "is_flagged_fraud",
}


def normalize_paysim(df: DataFrame) -> DataFrame:
    """Aligne un DataFrame lu depuis le CSV PaySim sur le schéma des événements du flux."""
    for src, dst in PAYSIM_TO_EVENT.items():
        if src in df.columns:
            df = df.withColumnRenamed(src, dst)
    return (df.withColumn("is_fraud", F.col("is_fraud").cast("int") == 1)
              .withColumn("is_flagged_fraud", F.col("is_flagged_fraud").cast("int") == 1)
              .withColumn("type", F.upper(F.trim(F.col("type"))))
              .withColumn("step", F.col("step").cast("int"))
              .select("step", "type", F.col("amount").cast("double").alias("amount"), "name_orig",
                      F.col("old_balance_orig").cast("double").alias("old_balance_orig"),
                      F.col("new_balance_orig").cast("double").alias("new_balance_orig"), "name_dest",
                      F.col("old_balance_dest").cast("double").alias("old_balance_dest"),
                      F.col("new_balance_dest").cast("double").alias("new_balance_dest"),
                      "is_fraud", "is_flagged_fraud"))


def add_features(df: DataFrame) -> DataFrame:
    """Ajoute les colonnes NUMERIC_FEATURES à un DataFrame au schéma des événements."""
    amount = F.col("amount")
    old_orig, new_orig = F.col("old_balance_orig"), F.col("new_balance_orig")
    old_dest, new_dest = F.col("old_balance_dest"), F.col("new_balance_dest")
    is_cash_in = F.col("type") == "CASH_IN"

    return (
        df.withColumn("log_amount", F.log1p(F.greatest(amount, F.lit(0.0))))
          .withColumn("error_balance_orig",
                      F.when(is_cash_in, old_orig + amount - new_orig).otherwise(old_orig - amount - new_orig))
          .withColumn("error_balance_dest", old_dest + amount - new_dest)
          .withColumn("drain_ratio", F.when(old_orig > 0, F.least(amount / old_orig, F.lit(10.0))).otherwise(F.lit(0.0)))
          .withColumn("amount_over_balance", (amount > old_orig).cast("int"))
          .withColumn("orig_emptied", ((new_orig <= 0.01) & (old_orig > 0)).cast("int"))
          .withColumn("dest_is_merchant", F.col("name_dest").startswith("M").cast("int"))
          .withColumn("dest_balance_unchanged", (F.abs(old_dest - new_dest) < 0.01).cast("int"))
          .withColumn("flagged_by_source", F.col("is_flagged_fraud").cast("int"))
          .withColumn("hour_of_day", (F.col("step") % 24).cast("int"))
    )


def build_pipeline(max_iter: int = 60, max_depth: int = 5, seed: int = 42) -> Pipeline:
    """Pipeline MLlib complet : encodage du type, assemblage, gradient boosting pondéré."""
    type_indexer = StringIndexer(inputCol="type", outputCol="type_idx", handleInvalid="keep")
    type_encoder = OneHotEncoder(inputCols=["type_idx"], outputCols=["type_vec"], handleInvalid="keep")
    assembler = VectorAssembler(inputCols=NUMERIC_FEATURES + ["type_vec"], outputCol="features",
                                handleInvalid="keep")
    gbt = GBTClassifier(featuresCol="features", labelCol=LABEL_COL, weightCol=WEIGHT_COL,
                        maxIter=max_iter, maxDepth=max_depth, stepSize=0.1, subsamplingRate=0.8,
                        seed=seed)
    return Pipeline(stages=[type_indexer, type_encoder, assembler, gbt])


# Colonnes intermédiaires ajoutées par le pipeline, à retirer après scoring
PIPELINE_OUTPUT_COLUMNS = ["type_idx", "type_vec", "features", "rawPrediction", "probability", "prediction"]
