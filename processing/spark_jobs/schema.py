"""Schéma Spark de l'événement JSON publié par le producer dans le topic `transactions`."""
from pyspark.sql.types import (BooleanType, DoubleType, IntegerType, StringType,
                               StructField, StructType)

TRANSACTION_SCHEMA = StructType([
    StructField("transaction_id",   StringType(),  nullable=False),
    StructField("event_time",       StringType(),  nullable=False),   # ISO-8601, casté en timestamp ensuite
    StructField("step",             IntegerType(), nullable=False),
    StructField("type",             StringType(),  nullable=False),
    StructField("amount",           DoubleType(),  nullable=False),
    StructField("name_orig",        StringType(),  nullable=False),
    StructField("old_balance_orig", DoubleType(),  nullable=False),
    StructField("new_balance_orig", DoubleType(),  nullable=False),
    StructField("name_dest",        StringType(),  nullable=False),
    StructField("old_balance_dest", DoubleType(),  nullable=False),
    StructField("new_balance_dest", DoubleType(),  nullable=False),
    StructField("is_fraud",         BooleanType(), nullable=False),
    StructField("is_flagged_fraud", BooleanType(), nullable=False),
])

# Colonnes insérées dans fraud.transactions (ordre = ordre de l'INSERT)
TRANSACTION_COLUMNS = [
    "transaction_id", "step", "event_time", "type", "amount",
    "name_orig", "old_balance_orig", "new_balance_orig",
    "name_dest", "old_balance_dest", "new_balance_dest",
    "is_fraud", "is_flagged_fraud", "risk_score", "is_suspicious",
    "ml_score", "model_version",
]

# Colonnes insérées dans fraud.fraud_alerts
ALERT_COLUMNS = ["transaction_id", "rule_name", "description", "severity", "risk_score"]
