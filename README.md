# Plateforme de détection de fraude en temps réel

[![CI](https://github.com/iliasben123/realtime-fraud-detection/actions/workflows/ci.yml/badge.svg)](https://github.com/iliasben123/realtime-fraud-detection/actions/workflows/ci.yml)

Projet portfolio Data Engineering / Backend simulant une plateforme de détection de fraudes
bancaires en streaming, à partir du dataset [PaySim](https://www.kaggle.com/datasets/ealaxi/paysim1).

## Architecture

```
 CSV PaySim ──► Producer Python ──► Kafka (transactions) ──► Spark Structured Streaming
                                                                      │
                                              règles stateless + fenêtres glissantes par compte
                                                                      │
                                         ┌────────────────────────────┴───────────────┐
                                         ▼                                            ▼
                                 Kafka (fraud-alerts)                           PostgreSQL
                                                                         transactions + fraud_alerts
                                                                                      │
                                                                                      ▼
                                                                    API REST Java Spring Boot
                                                                 (architecture hexagonale)
```

| Brique | Techno | Dossier | Statut |
|---|---|---|---|
| Infrastructure | Docker Compose : Kafka (KRaft), Kafka UI, PostgreSQL 16, pgAdmin | `docker-compose.yml`, `docker/` | fait |
| Ingestion | Python 3.12, confluent-kafka | `ingestion/` | fait |
| Traitement | PySpark 3.5 Structured Streaming, psycopg2 | `processing/spark_jobs/` | fait |
| Machine learning | Spark MLlib (GBTClassifier), registre de modèles versionné | `processing/spark_jobs/ml/` | fait |
| API métier | Java 21, Spring Boot 3.5, JPA, springdoc, ArchUnit | `api-fraud/` | fait |

> Kafka tourne en mode KRaft (sans Zookeeper), supporté depuis Kafka 3.3 et seul mode possible en 4.x.

## Prérequis

- Docker Desktop
- Python 3.12+ (producer, génération de données)
- Java 21 + Maven 3.9 (pour développer / tester l'API hors Docker)

## Démarrage rapide

### 1. Infrastructure

```bash
cp .env.example .env          # adapter si besoin (ports, identifiants, seuils des règles)
docker compose up -d
docker compose ps
```

Services exposés :

| Service | URL / port | Identifiants |
|---|---|---|
| Kafka (hôte) | `localhost:9092` | – |
| Kafka (réseau Docker) | `kafka:29092` | – |
| Kafka UI | http://localhost:8085 | – |
| PostgreSQL | `localhost:5433`, base `frauddb` | `fraud` / `fraud` |
| pgAdmin (optionnel, profil `tools`) | http://localhost:8086 | `admin@fraud.local` / `admin` |

Topics créés automatiquement : `transactions` (3 partitions) et `fraud-alerts` (3 partitions).
Le schéma SQL (`docker/postgres/init/01_schema.sql`) est appliqué au premier démarrage du volume.
Si le schéma change, recréer le volume : `docker compose down -v && docker compose up -d`.

### 2. Données

Soit le vrai dataset PaySim (Kaggle, ~470 Mo) placé dans `data/raw/paysim.csv`,
soit un échantillon synthétique au même format :

```bash
cd ingestion
python -m venv .venv
.venv\Scripts\activate            # Linux/macOS : source .venv/bin/activate
pip install -r requirements.txt
python -m producer.generate_sample_data --rows 50000
```

Le générateur injecte quelques comptes « chauds » (2 % du trafic) pour que les règles de vélocité
par fenêtre glissante aient de quoi se déclencher.

### 3. Démo en une commande

```bash
scripts/demo.sh                 # build + démarrage, rejoue 5 000 transactions, affiche les statistiques
scripts/demo.sh --limit 20000 --rate 200 --no-build
scripts/demo.sh --down          # arrêt et purge des volumes
```

Le script attend que l'API et Spark soient prêts, rejoue un lot via le producer, suit l'ingestion
en base puis affiche `GET /api/v1/stats/overview` et une alerte critique. (Git Bash sous Windows.)

### 3 bis. Pipeline complet à la main (profil `app`)

```bash
docker compose --profile app up -d --build
docker compose logs -f spark          # suivi des micro-batches
```

Lance en plus de l'infra : le producer (lit `./data/raw/paysim.csv`), le job Spark et l'API.

| Service | URL |
|---|---|
| Spark UI | http://localhost:4040 |
| API REST | http://localhost:8080/api/v1 (port hôte : `API_PORT` dans `.env`) |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |

Pour rejouer le flux : `docker compose --profile app restart producer`.

### 3 ter. Composants lancés à la main (développement)

```bash
# Producer
cd ingestion
python -m producer.transaction_producer                      # config via .env
python -m producer.transaction_producer --rate 200 --limit 10000

# API (PostgreSQL du compose sur localhost:5433)
cd api-fraud
mvn spring-boot:run
```

> Le job Spark s'exécute dans Docker : Spark local sous Windows nécessite winutils et un chemin
> sans accent, et le connecteur Kafka est pré-installé dans l'image `processing/Dockerfile`.

## Ingestion : producer Kafka

Chaque ligne CSV devient un événement JSON publié dans `transactions`, avec pour clé le compte
émetteur (`name_orig`) afin de garantir l'ordre par client au sein d'une partition :

```json
{
  "transaction_id": "2e27f8bc-b383-4dfe-bd9b-c0b9ec60c4c4",
  "event_time": "2026-10-16T22:26:18.327+00:00",
  "step": 1,
  "type": "CASH_OUT",
  "amount": 10453.93,
  "name_orig": "C1181165722",
  "old_balance_orig": 368235.61,
  "new_balance_orig": 357781.68,
  "name_dest": "C1463201078",
  "old_balance_dest": 1481335.49,
  "new_balance_dest": 1491789.42,
  "is_fraud": false,
  "is_flagged_fraud": false
}
```

Producer idempotent (`acks=all`, `enable.idempotence`), limitation de débit, flush propre sur arrêt.
Tests : `cd ingestion && pip install -r requirements-dev.txt && python -m pytest tests -q`.

## Traitement : Spark Structured Streaming

`processing/spark_jobs/fraud_detection_job.py` lance deux requêtes streaming sur le même flux Kafka :

1. **Scoring par transaction** (`transactions-scoring`) : parsing JSON, règles stateless, puis dans
   `foreachBatch` : insertion idempotente des transactions et alertes dans PostgreSQL
   (`ON CONFLICT DO NOTHING`) et publication des alertes dans `fraud-alerts`.
2. **Fenêtres glissantes** (`velocity-windows`) : agrégation par compte émetteur sur une fenêtre de
   5 min glissant chaque minute (watermark 2 min, mode `update`), alertes de vélocité sur le nombre
   ou le montant cumulé de transactions.

| Règle | Condition | Sévérité | Score |
|---|---|---|---|
| `HIGH_AMOUNT` | montant > 200 000 | HIGH | 60 |
| `ACCOUNT_DRAIN` | TRANSFER / CASH_OUT qui vide intégralement le compte | CRITICAL | 85 |
| `TRANSFER_TO_EMPTY_ACCOUNT` | transfert vers un compte dont le solde ne bouge pas (compte mule) | MEDIUM | 45 |
| `BALANCE_INCONSISTENCY` | solde après ≠ solde avant − montant | LOW | 25 |
| `FLAGGED_BY_SOURCE` | `isFlaggedFraud` du système source | HIGH | 70 |
| `VELOCITY_COUNT` | ≥ 5 transactions du même compte en 5 min | HIGH | 65 |
| `VELOCITY_AMOUNT` | ≥ 2 transactions et ≥ 500 000 cumulés par le même compte en 5 min | HIGH | 70 |
| `ML_HIGH_RISK` | probabilité du modèle MLlib ≥ seuil appris (cf. section ML) | HIGH | 75 |

Score de risque d'une transaction = max des scores déclenchés + 10 par règle supplémentaire, borné à 100 ;
lorsque `ML_HIGH_RISK` se déclenche, le score ML (probabilité × 100) est pris en compte dans ce maximum.
Les seuils sont paramétrables via les variables `RULE_*` du `.env`.

Tests unitaires des règles (Spark local dans l'image) :

```bash
docker compose --profile app build spark
docker run --rm --entrypoint python3 dtectiondefraude-spark -m pytest /app/tests -q
```

## Machine learning : du batch au streaming

Les règles seules ont une précision très faible (environ 1 % sur l'échantillon synthétique : `ACCOUNT_DRAIN`
se déclenche sur toute opération qui vide un compte, fraude ou non). Un modèle supervisé entraîné sur le
label PaySim complète les règles.

```
 CSV PaySim ──► normalisation ──► features ──► GBTClassifier pondéré ──► évaluation ──► registre
                                     ▲                                                  models/fraud_gbt/
                                     │ même code (spark_jobs/ml/features.py)              ├── latest
 Kafka ──► job streaming ────────────┘──► PipelineModel.transform ──► ml_score, ML_HIGH_RISK  ├── v2026.../model/
                                                                                            └── v2026.../metadata.json
```

- **Features** (`ml/features.py`), calculables sur une transaction isolée donc applicables au flux :
  montant et log-montant, soldes, incohérences de solde émetteur / destinataire (`error_balance_*`),
  part du solde déplacée, compte vidé, destinataire marchand, solde destinataire inchangé, flag source,
  heure, type encodé one-hot. Une seule définition partagée par l'entraînement et le streaming.
- **Modèle** : `GBTClassifier` (gradient boosting, 60 arbres, profondeur 5) avec pondération des classes
  pour le déséquilibre (≈ 0,1 % de fraudes). Split 80 / 20, métriques AUC-ROC, AUC-PR et courbe
  précision / rappel par seuil ; le seuil retenu maximise le F1 (milieu du plateau en cas d'égalité).
  Les règles sont évaluées sur le même jeu de test pour servir de référence.
- **Registre** (`ml/registry.py`) : chaque entraînement publie `models/fraud_gbt/<version>/` (modèle +
  `metadata.json` : métriques, seuil, importance des features, horodatage) et met à jour `latest`.
  Le job streaming charge `latest` au démarrage (ou `ML_MODEL_VERSION` pour épingler une version), trace
  la version utilisée dans `transactions.model_version` et dans chaque alerte `ML_HIGH_RISK`.
  Sans modèle publié, le job fonctionne avec les règles seules.

```bash
scripts/train_model.sh                          # entraîne sur data/raw/paysim.csv puis recrée le job Spark
scripts/train_model.sh --sample 0.2 --max-iter 40   # vrai PaySim (6,3 M lignes) : échantillonner
```

Résultats sur le **vrai dataset PaySim** (échantillon de 30 %, soit 1,9 M lignes et 2 437 fraudes ;
jeu de test de 381 768 lignes), `scripts/train_model.sh --sample 0.3` :

| Détecteur | Précision | Rappel | F1 | Faux positifs (test) | Fraudes manquées |
|---|---|---|---|---|---|
| Règles métier seules | 0,3 % | 99,6 % | 0,007 | 135 035 | 2 |
| Modèle GBT (seuil 0,75) | 100 % | 99,8 % | 0,999 | 0 | 1 |

AUC-ROC 1,000, AUC-PR 0,9995. Le rapport complet (courbe précision / rappel par seuil, importance des
features) est généré par `python scripts/model_report.py` : [docs/model_report.md](docs/model_report.md).

Lecture métier : à volume égal, les règles seules auraient produit 135 000 alertes à traiter par les
analystes pour 449 fraudes réelles, le modèle en produit 450 dont 450 fondées. Le seuil est un levier
explicite : à 0,10 le rappel est total pour 195 faux positifs ; à partir de 0,60 il n'y en a plus.

Et en conditions réelles de streaming, sur les 20 000 premières transactions du dataset rejouées via Kafka
(81 fraudes), telles que mesurées dans PostgreSQL après traitement par le job Spark :

| Règle | Alertes émises | dont fraudes avérées |
|---|---|---|
| `ML_HIGH_RISK` (modèle) | 78 | 78 |
| `ACCOUNT_DRAIN` | 2 076 | 78 |
| `HIGH_AMOUNT` | 3 786 | 78 |

> Pourquoi un score aussi élevé ? PaySim est un simulateur : ses fraudes suivent un mécanisme déterministe
> (vidage du compte, soldes incohérents) que les features `orig_emptied`, `error_balance_orig` et
> `amount_over_balance` (96 % de l'importance) capturent presque parfaitement. C'est un résultat connu sur
> ce dataset ; sur des données réelles, la fraude est bien moins séparable et c'est le suivi des décisions
> analystes (statuts des alertes) qui permettrait de mesurer la précision réelle du modèle dans le temps.

Tests (`tests/test_ml.py`) : features, entraînement sur jeu réduit, registre, scoring, intégration
avec les règles et schéma de sortie avec et sans modèle.

## API métier : Spring Boot, architecture hexagonale

```
api-fraud/src/main/java/com/frauddetection/api
├── domain/            cœur métier, sans aucune dépendance framework
│   ├── model/         Alert, Transaction, AlertStatus (machine à états), filtres, pagination
│   ├── exception/     AlertNotFound, InvalidStatusTransition...
│   └── port/
│       ├── in/        cas d'utilisation exposés (AlertQueryUseCase, AlertStatusUseCase...)
│       └── out/       dépendances requises (AlertRepository, UnitOfWork...)
├── application/       implémentation des cas d'utilisation (AlertService...)
├── adapter/
│   ├── in/web/        contrôleurs REST, DTO, gestion des erreurs (RFC 9457)
│   └── out/persistence/ entités JPA, Spring Data, Specifications, adaptateurs des ports
└── config/            câblage Spring des cas d'utilisation
```

Les règles de dépendance sont vérifiées par un test ArchUnit (`HexagonalArchitectureTest`).

### Endpoints

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/v1/alerts` | recherche paginée : `status`, `severity`, `ruleName`, `account`, `minAmount`, `minRiskScore`, `from`, `to`, `page`, `size`, `sortBy`, `direction` |
| GET | `/api/v1/alerts/{id}` | alerte + transaction associée |
| GET | `/api/v1/alerts/{id}/history` | historique des changements de statut |
| PATCH | `/api/v1/alerts/{id}/status` | changer le statut : `{"status":"CONFIRMED_FRAUD","reviewedBy":"...","comment":"..."}` |
| GET | `/api/v1/transactions` | recherche paginée : `suspicious`, `fraud`, `type`, `account`, `minAmount`, `maxAmount`, `minRiskScore`, `from`, `to`... |
| GET | `/api/v1/transactions/{id}` | transaction + alertes déclenchées |
| GET | `/api/v1/stats/overview` | volumes, précision / rappel vs label PaySim, répartition des alertes |

Cycle de vie d'une alerte (transition interdite → HTTP 409) :

```
OPEN ─► UNDER_REVIEW ─► CONFIRMED_FRAUD ─► BLOCKED
  │          └─────────► FALSE_POSITIVE
  └── raccourcis directs vers CONFIRMED_FRAUD / FALSE_POSITIVE / BLOCKED
```

Exemples :

```bash
curl "http://localhost:8080/api/v1/alerts?status=OPEN&severity=CRITICAL&size=5"
curl "http://localhost:8080/api/v1/transactions?suspicious=true&minAmount=100000"
curl -X PATCH http://localhost:8080/api/v1/alerts/<id>/status \
     -H "Content-Type: application/json" \
     -d '{"status":"CONFIRMED_FRAUD","reviewedBy":"analyst.jdoe","comment":"Client contacté"}'
curl http://localhost:8080/api/v1/stats/overview
```

Tests :

```bash
cd api-fraud
mvn test       # unitaires : domaine, service (Mockito), contrôleur (@WebMvcTest), ArchUnit
mvn verify     # + intégration (*IT) : PostgreSQL 16 réel via Testcontainers, initialisé avec
               #   docker/postgres/init/01_schema.sql ; adaptateurs JPA et scénario HTTP de bout en bout
```

> Windows + Docker Desktop : Testcontainers cherche le pipe `docker_engine`, Docker Desktop expose
> `dockerDesktopLinuxEngine`. Avant `mvn verify` :
> Git Bash `export DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'`,
> PowerShell `$env:DOCKER_HOST = 'npipe:////./pipe/dockerDesktopLinuxEngine'`.

## Intégration continue

Le workflow GitHub Actions (`.github/workflows/ci.yml`) exécute à chaque push :

| Job | Contenu |
|---|---|
| `api` | `mvn verify` : tests unitaires + Testcontainers, rapports publiés en artefact |
| `spark` | build de l'image Spark (cache GHA) puis pytest des règles dans l'image |
| `producer` | pytest du producer (conversion CSV, lecture en flux, générateur) |
| `compose` | validation du `docker-compose.yml` avec tous les profils |

## Modèle de données PostgreSQL (schéma `fraud`)

- `transactions` : transactions analysées par Spark (colonnes PaySim + `risk_score`, `is_suspicious`,
  `ml_score`, `model_version`).
- `fraud_alerts` : alertes générées (règle, sévérité, score, statut), unique par (transaction, règle).
- `alert_status_history` : piste d'audit des changements de statut (alimentée par l'API).
- `v_alerts_detailed` : vue jointe alertes + transactions.

## Commandes utiles

```bash
# Consommer les topics en console
docker exec fraud-kafka kafka-console-consumer --bootstrap-server kafka:29092 --topic transactions --from-beginning --max-messages 5
docker exec fraud-kafka kafka-console-consumer --bootstrap-server kafka:29092 --topic fraud-alerts --from-beginning --max-messages 5

# Shell PostgreSQL
docker exec -it fraud-postgres psql -U fraud -d frauddb -c "select rule_name, severity, count(*) from fraud.fraud_alerts group by 1,2 order by 3 desc"

# Tout arrêter (et purger les données avec -v)
docker compose --profile app --profile tools down -v
```

## Pistes d'évolution

- Réentraînement planifié et comparaison champion / challenger entre versions du registre.
- Sécurisation de l'API (Spring Security, JWT) et rôles analyste / superviseur.
- Tests d'intégration Testcontainers Kafka + PostgreSQL pour le job Spark lui-même.
- Observabilité : métriques Spark et API dans Prometheus / Grafana.
