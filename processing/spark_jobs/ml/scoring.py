"""Application du modèle MLlib sur un DataFrame (batch ou streaming) et règle d'alerte associée."""
from __future__ import annotations

from pyspark.ml import PipelineModel
from pyspark.ml.functions import vector_to_array
from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from .features import DERIVED_FEATURES, PIPELINE_OUTPUT_COLUMNS, add_features
from .registry import ModelInfo

ML_RULE_NAME = "ML_HIGH_RISK"


def apply_ml_scoring(df: DataFrame, model: PipelineModel, info: ModelInfo) -> DataFrame:
    """
    Ajoute `ml_probability` (0..1), `ml_score` (0..100) et `model_version`.
    Le pipeline étant stateless, l'appel est identique sur un DataFrame streaming.
    """
    scored = model.transform(add_features(df))
    scored = (scored
              .withColumn("ml_probability", vector_to_array(F.col("probability"))[1])
              .withColumn("ml_score", (F.col("ml_probability") * 100).cast("decimal(5,2)"))
              .withColumn("model_version", F.lit(info.version)))
    return scored.drop(*PIPELINE_OUTPUT_COLUMNS, *DERIVED_FEATURES)


def without_ml_scoring(df: DataFrame) -> DataFrame:
    """Colonnes ML à NULL lorsqu'aucun modèle n'est disponible (schéma de sortie identique)."""
    return (df.withColumn("ml_probability", F.lit(None).cast("double"))
              .withColumn("ml_score", F.lit(None).cast("decimal(5,2)"))
              .withColumn("model_version", F.lit(None).cast("string")))


def combine_risk_score(df: DataFrame) -> DataFrame:
    """
    Score final = max(score des règles, score ML) — le score ML n'est pris en compte que si la
    règle ML_HIGH_RISK s'est déclenchée (probabilité >= seuil) ; un score ML faible ne rend
    donc pas une transaction suspecte. `is_suspicious` reste piloté par les règles déclenchées.
    À appeler après apply_stateless_rules.
    """
    ml_flag = f"rule_{ML_RULE_NAME.lower()}"
    if ml_flag not in df.columns:
        return df
    ml_contribution = F.when(F.col(ml_flag), F.coalesce(F.col("ml_score"), F.lit(0))).otherwise(F.lit(0))
    return df.withColumn("risk_score",
                         F.greatest(F.col("risk_score"), ml_contribution).cast("decimal(5,2)"))
