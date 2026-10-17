#!/usr/bin/env bash
# ============================================================================
#  Entraîne le modèle MLlib dans le conteneur Spark puis redémarre le job streaming
#  pour qu'il charge la nouvelle version (models/fraud_gbt/latest).
#
#  Usage : scripts/train_model.sh [--csv data/raw/paysim.csv] [--sample 0.2] [--max-iter 60] [--no-restart]
#  Les options sont transmises à spark_jobs/ml/train_model.py (chemins relatifs à la racine du projet).
# ============================================================================
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash : ne pas convertir les chemins /app/... passés aux conteneurs
cd "$(dirname "$0")/.."

CSV="data/raw/paysim.csv"; RESTART=1; EXTRA=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --csv) CSV="$2"; shift 2 ;;
    --no-restart) RESTART=0; shift ;;
    *) EXTRA+=("$1"); shift ;;
  esac
done
[[ -f "$CSV" ]] || { echo "CSV introuvable : $CSV"; exit 1; }
mkdir -p models

echo "==> Entraînement sur $CSV"
docker compose --profile app run --rm --no-deps spark \
  --driver-memory 2g /app/spark_jobs/ml/train_model.py \
  --csv "/app/${CSV}" --out /app/models/fraud_gbt "${EXTRA[@]}"

version=$(tr -d '\r\n' < models/fraud_gbt/latest)
echo "==> Version publiée : $version"
python -X utf8 -c "
import json,sys; m=json.load(open('models/fraud_gbt/$version/metadata.json', encoding='utf-8'))['metrics']
print(f\"AUC-ROC {m['auc_roc']}  AUC-PR {m['auc_pr']}\")
t=m['at_threshold']; b=m['rules_baseline']
print(f\"Modèle  (seuil {t['threshold']}) : précision {t['precision']}  rappel {t['recall']}  F1 {t['f1']}\")
print(f\"Règles seules            : précision {b['precision']}  rappel {b['recall']}  F1 {b['f1']}\")
" 2>/dev/null || cat "models/fraud_gbt/$version/metadata.json"

if [[ $RESTART -eq 1 ]] && docker compose --profile app ps --status running spark 2>/dev/null | grep -q fraud-spark; then
  echo "==> Redémarrage du job streaming (recréation : prend en compte image et volumes à jour)"
  docker compose --profile app up -d --force-recreate --no-build spark
fi
