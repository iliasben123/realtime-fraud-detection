### Modèle `v20261017_035224`

- Source : `paysim.csv`, 1 909 740 lignes (2 437 fraudes), jeu de test 20% soit 381 768 lignes
- Algorithme : GBTClassifier (maxIter=60, maxDepth=5), entraîné le 2026-10-17
- AUC-ROC **1.0000**, AUC-PR **0.9995**

| Détecteur | Précision | Rappel | F1 | Vrais positifs | Faux positifs | Fraudes manquées |
|---|---|---|---|---|---|---|
| Règles métier seules | 0.3 % | 99.6 % | 0.007 | 449 | 135 035 | 2 |
| Modèle GBT (seuil 0.75) | 100.0 % | 99.8 % | 0.999 | 450 | 0 | 1 |

Compromis précision / rappel selon le seuil de probabilité (jeu de test) :

| Seuil | Précision | Rappel | F1 | Faux positifs |
|---|---|---|---|---|
| 0.10 | 69.8 % | 100.0 % | 0.822 | 195 |
| 0.20 | 85.9 % | 99.8 % | 0.923 | 74 |
| 0.30 | 94.9 % | 99.8 % | 0.973 | 24 |
| 0.40 | 97.6 % | 99.8 % | 0.987 | 11 |
| 0.50 | 99.3 % | 99.8 % | 0.996 | 3 |
| 0.60 | 100.0 % | 99.8 % | 0.999 | 0 |
| 0.70 | 100.0 % | 99.8 % | 0.999 | 0 |
| 0.80 | 100.0 % | 99.8 % | 0.999 | 0 |
| 0.90 | 100.0 % | 99.8 % | 0.999 | 0 |

Importance des features (gain total, normalisé) :

| Feature | Importance |
|---|---|
| `orig_emptied` | 0.518 █████████████████████ |
| `error_balance_orig` | 0.299 ████████████ |
| `amount_over_balance` | 0.140 ██████ |
| `error_balance_dest` | 0.022 █ |
| `amount` | 0.009  |
| `dest_balance_unchanged` | 0.004  |
| `old_balance_dest` | 0.003  |
| `old_balance_orig` | 0.002  |
| `hour_of_day` | 0.001  |
| `type` | 0.001  |
