import os
import sys

import pytest
from pyspark.sql import SparkSession

from spark_jobs.config import JobConfig

# Les workers Python de Spark doivent utiliser le même interpréteur que pytest
# (sous Windows, `python3` pointe sinon vers le stub du Microsoft Store).
os.environ.setdefault("PYSPARK_PYTHON", sys.executable)
os.environ.setdefault("PYSPARK_DRIVER_PYTHON", sys.executable)


@pytest.fixture(scope="session")
def spark() -> SparkSession:
    session = (
        SparkSession.builder
        .master("local[2]")
        .appName("fraud-rules-tests")
        .config("spark.sql.shuffle.partitions", "2")
        .config("spark.sql.session.timeZone", "UTC")
        .config("spark.ui.enabled", "false")
        .getOrCreate()
    )
    session.sparkContext.setLogLevel("ERROR")
    yield session
    session.stop()


@pytest.fixture(scope="session")
def cfg() -> JobConfig:
    return JobConfig(
        bootstrap_servers="localhost:9092",
        topic_transactions="transactions",
        topic_alerts="fraud-alerts",
        starting_offsets="latest",
        pg_host="localhost", pg_port=5432, pg_db="frauddb", pg_user="fraud", pg_password="fraud",
        checkpoint_dir="/tmp/checkpoints",
        trigger_interval="5 seconds",
        velocity_trigger_interval="10 seconds",
        shuffle_partitions=2,
        high_amount_threshold=200_000,
        velocity_window="5 minutes",
        velocity_slide="1 minute",
        velocity_watermark="2 minutes",
        velocity_max_tx=3,
        velocity_max_amount=500_000,
    )
