# ============================================================================
#  Image du producer Kafka (CSV PaySim -> topic `transactions`)
# ============================================================================
FROM python:3.12-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY producer ./producer

# Le CSV est monté depuis ./data (cf. docker-compose.yml)
ENV CSV_PATH=/app/data/raw/paysim.csv \
    PYTHONUNBUFFERED=1

ENTRYPOINT ["python", "-m", "producer.transaction_producer"]
