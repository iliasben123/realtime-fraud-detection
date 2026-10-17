# ============================================================================
#  Image du job Spark Structured Streaming (détection de fraude)
#  Base : Apache Spark 3.5.3 + Python 3 ; connecteur Kafka pré-installé
# ============================================================================
FROM apache/spark:3.5.3-python3

USER root

ARG SPARK_VERSION=3.5.3
ARG SCALA_VERSION=2.12
ARG KAFKA_CLIENTS_VERSION=3.4.1
ARG COMMONS_POOL2_VERSION=2.11.1
ARG MAVEN=https://repo1.maven.org/maven2

# Connecteur Kafka et ses dépendances transitives (évite --packages / Ivy au démarrage)
ADD ${MAVEN}/org/apache/spark/spark-sql-kafka-0-10_${SCALA_VERSION}/${SPARK_VERSION}/spark-sql-kafka-0-10_${SCALA_VERSION}-${SPARK_VERSION}.jar /opt/spark/jars/
ADD ${MAVEN}/org/apache/spark/spark-token-provider-kafka-0-10_${SCALA_VERSION}/${SPARK_VERSION}/spark-token-provider-kafka-0-10_${SCALA_VERSION}-${SPARK_VERSION}.jar /opt/spark/jars/
ADD ${MAVEN}/org/apache/kafka/kafka-clients/${KAFKA_CLIENTS_VERSION}/kafka-clients-${KAFKA_CLIENTS_VERSION}.jar /opt/spark/jars/
ADD ${MAVEN}/org/apache/commons/commons-pool2/${COMMONS_POOL2_VERSION}/commons-pool2-${COMMONS_POOL2_VERSION}.jar /opt/spark/jars/
RUN chmod 644 /opt/spark/jars/*.jar

RUN pip install --no-cache-dir psycopg2-binary==2.9.10 python-dotenv==1.0.1 pytest==8.3.3

WORKDIR /app
COPY spark_jobs ./spark_jobs
COPY tests ./tests
RUN mkdir -p /app/checkpoints && chown -R spark:spark /app

# /opt/spark/python : pyspark est fourni par l'image (non installé via pip) -> nécessaire pour pytest
ENV PYTHONPATH=/app:/opt/spark/python:/opt/spark/python/lib/py4j-0.10.9.7-src.zip \
    PYSPARK_PYTHON=python3 \
    SPARK_CHECKPOINT_DIR=/app/checkpoints

USER spark
EXPOSE 4040

ENTRYPOINT ["/opt/spark/bin/spark-submit"]
CMD ["--master", "local[*]", "--conf", "spark.driver.memory=1g", "/app/spark_jobs/fraud_detection_job.py"]
