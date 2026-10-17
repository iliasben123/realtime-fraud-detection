"""
Règles de détection d'anomalies appliquées au flux de transactions.

Deux familles :
  * règles « stateless » : évaluées transaction par transaction (montant élevé, vidage de compte, ...)
  * règles « stateful »  : agrégations sur fenêtre glissante par compte émetteur (vélocité)

Chaque règle produit une alerte (rule_name, severity, risk_score, description).
Le score de risque d'une transaction = max des scores déclenchés + bonus par règle supplémentaire, borné à 100.
"""
from __future__ import annotations

from dataclasses import dataclass

from pyspark.sql import Column, DataFrame
from pyspark.sql import functions as F

from .config import JobConfig

CASH_MOVING_TYPES = ("TRANSFER", "CASH_OUT")
EXTRA_RULE_BONUS = 10          # points ajoutés par règle supplémentaire déclenchée


@dataclass(frozen=True)
class Rule:
    name: str
    severity: str                       # LOW | MEDIUM | HIGH | CRITICAL
    score: int                          # 0..100
    description: Column                 # expression Spark produisant le texte de l'alerte
    predicate: Column                   # expression booléenne

    @property
    def flag_col(self) -> str:
        return f"rule_{self.name.lower()}"


# ----------------------------------------------------------------------------
# Règles stateless
# ----------------------------------------------------------------------------
def build_stateless_rules(cfg: JobConfig) -> list[Rule]:
    amount = F.col("amount")
    tx_type = F.col("type")
    old_orig, new_orig = F.col("old_balance_orig"), F.col("new_balance_orig")
    old_dest, new_dest = F.col("old_balance_dest"), F.col("new_balance_dest")

    return [
        Rule(
            name="HIGH_AMOUNT",
            severity="HIGH",
            score=60,
            predicate=amount > cfg.high_amount_threshold,
            description=F.format_string(
                "Montant %.2f supérieur au seuil %.2f", amount, F.lit(float(cfg.high_amount_threshold))),
        ),
        Rule(
            name="ACCOUNT_DRAIN",
            severity="CRITICAL",
            score=85,
            # TRANSFER / CASH_OUT qui vide intégralement le compte émetteur (pattern de fraude PaySim)
            predicate=(tx_type.isin(*CASH_MOVING_TYPES)
                       & (old_orig > 0)
                       & (new_orig <= 0.01)
                       & (amount >= old_orig - 0.01)),
            description=F.format_string(
                "%s de %.2f vidant intégralement le compte (solde initial %.2f)", tx_type, amount, old_orig),
        ),
        Rule(
            name="TRANSFER_TO_EMPTY_ACCOUNT",
            severity="MEDIUM",
            score=45,
            # Transfert vers un compte dont le solde n'évolue pas : compte « mule » typique
            predicate=((tx_type == "TRANSFER") & (amount > 0) & (old_dest == 0) & (new_dest == 0)),
            description=F.format_string(
                "Transfert de %.2f vers le compte %s sans mouvement de solde côté destinataire",
                amount, F.col("name_dest")),
        ),
        Rule(
            name="BALANCE_INCONSISTENCY",
            severity="LOW",
            score=25,
            # Le solde après opération ne correspond pas à solde avant - montant
            predicate=((tx_type != "CASH_IN")
                       & (old_orig >= amount)
                       & (F.abs(old_orig - amount - new_orig) > 1.0)),
            description=F.format_string(
                "Solde incohérent : %.2f - %.2f != %.2f", old_orig, amount, new_orig),
        ),
        Rule(
            name="FLAGGED_BY_SOURCE",
            severity="HIGH",
            score=70,
            predicate=F.col("is_flagged_fraud") == F.lit(True),
            description=F.lit("Transaction signalée par le système source (isFlaggedFraud)"),
        ),
    ]


def build_ml_rule(threshold: float, model_version: str) -> Rule:
    """Règle alimentée par le modèle MLlib : nécessite la colonne `ml_probability` (cf. ml/scoring.py)."""
    prob = F.col("ml_probability")
    return Rule(
        name="ML_HIGH_RISK",
        severity="HIGH",
        score=75,
        predicate=prob >= threshold,
        description=F.format_string("Probabilité de fraude %.3f >= seuil %.2f (modèle %s)",
                                    prob, F.lit(float(threshold)), F.lit(model_version)),
    )


def apply_stateless_rules(df: DataFrame, rules: list[Rule]) -> DataFrame:
    """Ajoute à chaque transaction : un flag par règle, triggered_rules, risk_score, is_suspicious."""
    for rule in rules:
        df = df.withColumn(rule.flag_col, F.coalesce(rule.predicate, F.lit(False)))

    triggered = F.array_compact(F.array(*[
        F.when(F.col(r.flag_col), F.lit(r.name)) for r in rules
    ]))
    max_score = F.greatest(*[
        F.when(F.col(r.flag_col), F.lit(r.score)).otherwise(F.lit(0)) for r in rules
    ])
    n_rules = F.size(triggered)

    return (
        df.withColumn("triggered_rules", triggered)
          .withColumn("is_suspicious", n_rules > 0)
          .withColumn(
              "risk_score",
              F.when(n_rules > 0,
                     F.least(F.lit(100), max_score + (n_rules - 1) * EXTRA_RULE_BONUS))
               .otherwise(F.lit(0))
               .cast("decimal(5,2)"))
    )


def explode_alerts(df: DataFrame, rules: list[Rule]) -> DataFrame:
    """
    Transforme un DataFrame scoré en une ligne par (transaction, règle déclenchée).
    Colonnes : transaction_id, rule_name, description, severity, risk_score + contexte pour Kafka.
    """
    alerts = F.array_compact(F.array(*[
        F.when(F.col(r.flag_col), F.struct(
            F.lit(r.name).alias("rule_name"),
            r.description.alias("description"),
            F.lit(r.severity).alias("severity"),
            F.lit(r.score).cast("decimal(5,2)").alias("risk_score"),
        )) for r in rules
    ]))
    triggered_names = F.array_compact(F.array(*[F.when(F.col(r.flag_col), F.lit(r.name)) for r in rules]))
    for col in ("ml_score", "model_version"):
        if col not in df.columns:
            df = df.withColumn(col, F.lit(None))
    return (
        df.filter(F.size(triggered_names) > 0)
          .select(
              "transaction_id", "event_time", "type", "amount", "name_orig", "name_dest",
              "is_fraud", "ml_score", "model_version", F.explode(alerts).alias("alert"))
          .select(
              "transaction_id",
              F.col("alert.rule_name").alias("rule_name"),
              F.col("alert.description").alias("description"),
              F.col("alert.severity").alias("severity"),
              F.col("alert.risk_score").alias("risk_score"),
              "event_time", "type", "amount", "name_orig", "name_dest", "is_fraud", "ml_score", "model_version")
    )


# ----------------------------------------------------------------------------
# Règles stateful : fenêtre glissante par compte émetteur
# ----------------------------------------------------------------------------
def aggregate_velocity(df: DataFrame, cfg: JobConfig) -> DataFrame:
    """
    Fenêtre glissante (ex. 5 min, pas de 1 min) par compte émetteur, avec watermark sur event_time.
    Retourne par (fenêtre, compte) : nb de transactions, montant total, dernière transaction.
    """
    return (
        df.withWatermark("event_time", cfg.velocity_watermark)
          .groupBy(
              F.window("event_time", cfg.velocity_window, cfg.velocity_slide).alias("window"),
              F.col("name_orig"))
          .agg(
              F.count("*").alias("tx_count"),
              F.sum("amount").alias("total_amount"),
              F.max_by("transaction_id", "event_time").alias("last_transaction_id"),
              F.max("event_time").alias("last_event_time"))
    )


def velocity_alerts(agg: DataFrame, cfg: JobConfig) -> DataFrame:
    """Transforme les fenêtres agrégées en alertes VELOCITY_COUNT / VELOCITY_AMOUNT."""
    count_rule = F.col("tx_count") >= cfg.velocity_max_tx
    # La vélocité concerne une accumulation : au moins 2 transactions dans la fenêtre
    # (une transaction isolée de gros montant relève de HIGH_AMOUNT)
    amount_rule = (F.col("tx_count") >= 2) & (F.col("total_amount") >= cfg.velocity_max_amount)

    alerts = F.array_compact(F.array(
        F.when(count_rule, F.struct(
            F.lit("VELOCITY_COUNT").alias("rule_name"),
            F.format_string("%d transactions du compte %s en %s (seuil %d)",
                            F.col("tx_count"), F.col("name_orig"),
                            F.lit(cfg.velocity_window), F.lit(cfg.velocity_max_tx)).alias("description"),
            F.lit("HIGH").alias("severity"),
            F.lit(65).cast("decimal(5,2)").alias("risk_score"),
        )),
        F.when(amount_rule, F.struct(
            F.lit("VELOCITY_AMOUNT").alias("rule_name"),
            F.format_string("%.2f cumulés par le compte %s en %s (seuil %.2f)",
                            F.col("total_amount"), F.col("name_orig"),
                            F.lit(cfg.velocity_window), F.lit(float(cfg.velocity_max_amount))).alias("description"),
            F.lit("HIGH").alias("severity"),
            F.lit(70).cast("decimal(5,2)").alias("risk_score"),
        )),
    ))

    return (
        agg.filter(count_rule | amount_rule)
           .select(
               F.col("last_transaction_id").alias("transaction_id"),
               F.col("name_orig"),
               F.col("window.start").alias("window_start"),
               F.col("window.end").alias("window_end"),
               F.col("tx_count"), F.col("total_amount"),
               F.explode(alerts).alias("alert"))
           .select(
               "transaction_id",
               F.col("alert.rule_name").alias("rule_name"),
               F.col("alert.description").alias("description"),
               F.col("alert.severity").alias("severity"),
               F.col("alert.risk_score").alias("risk_score"),
               "name_orig", "window_start", "window_end", "tx_count", "total_amount")
    )
