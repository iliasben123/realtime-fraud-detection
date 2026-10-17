#!/usr/bin/env bash
# ============================================================================
#  Démo de bout en bout : infra + pipeline + API, rejoue un lot de transactions
#  et affiche les statistiques de détection.
#
#  Usage : scripts/demo.sh [--limit N] [--rate N] [--no-build] [--train] [--down]
#    --limit N    nombre de transactions à rejouer (défaut 5000)
#    --rate N     débit en msg/s, 0 = max (défaut 0)
#    --no-build   ne pas reconstruire les images
#    --train      réentraîner le modèle MLlib même si une version existe
#    --down       arrêter et purger la plateforme (volumes compris) puis quitter
# ============================================================================
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash : ne pas convertir les chemins /app/... passés aux conteneurs
cd "$(dirname "$0")/.."

LIMIT=5000; RATE=0; BUILD="--build"; TRAIN=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --limit) LIMIT="$2"; shift 2 ;;
    --rate) RATE="$2"; shift 2 ;;
    --no-build) BUILD=""; shift ;;
    --train) TRAIN=1; shift ;;
    --down) docker compose --profile app --profile tools down -v; exit 0 ;;
    *) echo "option inconnue : $1"; exit 1 ;;
  esac
done

[[ -f .env ]] || cp .env.example .env
# Lecture ciblée (certaines valeurs du .env contiennent des espaces, ex. "5 minutes")
env_value() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- | tr -d '\r' | xargs; }
API_PORT="$(env_value API_PORT)"; API_PORT="${API_PORT:-8080}"
FRONT_PORT="$(env_value FRONT_PORT)"; FRONT_PORT="${FRONT_PORT:-3000}"
API="http://localhost:${API_PORT}"

step() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

step "Données"
if [[ ! -f data/raw/paysim.csv ]]; then
  echo "Pas de CSV : génération d'un échantillon synthétique (50 000 lignes)"
  docker compose --profile app run --rm --no-deps --entrypoint python producer \
    -m producer.generate_sample_data --rows 50000 --out /app/data/raw/paysim.csv 2>/dev/null \
    || { echo "Génération via Python local"; (cd ingestion && python -m producer.generate_sample_data --rows 50000); }
fi
echo "CSV : data/raw/paysim.csv ($(($(wc -l < data/raw/paysim.csv) - 1)) lignes)"

step "Modèle MLlib"
if [[ $TRAIN -eq 1 || ! -f models/fraud_gbt/latest ]]; then
  echo "Entraînement du modèle sur data/raw/paysim.csv (quelques minutes)"
  [[ -n "$BUILD" ]] && docker compose --profile app build spark
  bash scripts/train_model.sh --no-restart
else
  echo "Modèle courant : $(tr -d '\r\n' < models/fraud_gbt/latest) (forcer un réentraînement : --train)"
fi

step "Infrastructure + Spark + API"
docker compose --profile app up -d $BUILD kafka kafka-init kafka-ui postgres spark api frontend

step "Attente de l'API (${API})"
for i in $(seq 1 60); do
  if curl -fsS "${API}/actuator/health" 2>/dev/null | grep -q '"UP"'; then echo "API saine"; break; fi
  [[ $i -eq 60 ]] && { echo "L'API ne répond pas"; docker compose logs --tail 30 api; exit 1; }
  sleep 3
done

step "Attente du job Spark"
for i in $(seq 1 40); do
  if docker logs fraud-spark 2>&1 | grep -q "Streaming démarré"; then echo "Spark en écoute"; break; fi
  [[ $i -eq 40 ]] && { echo "Spark ne démarre pas"; docker compose logs --tail 30 spark; exit 1; }
  sleep 3
done

step "Rejeu de ${LIMIT} transactions (débit ${RATE:-max} msg/s)"
docker compose --profile app run --rm producer --limit "$LIMIT" --rate "$RATE" 2>&1 | grep -E "Terminé|ERROR" || true

step "Traitement par Spark"
before=$(curl -fsS "${API}/api/v1/stats/overview" | python -c "import sys,json;print(json.load(sys.stdin)['totalTransactions'])" 2>/dev/null || echo 0)
for i in $(seq 1 40); do
  sleep 5
  now=$(curl -fsS "${API}/api/v1/stats/overview" | python -c "import sys,json;print(json.load(sys.stdin)['totalTransactions'])" 2>/dev/null || echo 0)
  printf '\r  transactions en base : %s' "$now"
  if [[ "$now" -ge $((before + LIMIT)) ]]; then break; fi
done
echo

step "Statistiques (${API}/api/v1/stats/overview)"
curl -fsS "${API}/api/v1/stats/overview" | python -X utf8 -m json.tool

step "Exemple d'alerte critique ouverte"
curl -fsS "${API}/api/v1/alerts?status=OPEN&severity=CRITICAL&size=1" \
  | python -X utf8 -c "import sys,json; d=json.load(sys.stdin); a=d['content'][0]['alert'] if d['content'] else {}; print(json.dumps(a, indent=2, ensure_ascii=False))"

cat <<EOF

Plateforme prête :
  Console    : http://localhost:${FRONT_PORT}  (analyst / analyst123, supervisor / supervisor123)
  Swagger UI : ${API}/swagger-ui.html
  Kafka UI   : http://localhost:8085
  Spark UI   : http://localhost:4040
Arrêt : scripts/demo.sh --down
EOF
