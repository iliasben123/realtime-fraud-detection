-- ============================================================================
--  Schéma PostgreSQL - Plateforme de détection de fraude
--  Exécuté automatiquement au premier démarrage du conteneur postgres.
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS fraud;
SET search_path TO fraud, public;

-- ----------------------------------------------------------------------------
-- Types énumérés
-- ----------------------------------------------------------------------------
DO $$ BEGIN
    CREATE TYPE fraud.alert_status AS ENUM ('OPEN', 'UNDER_REVIEW', 'CONFIRMED_FRAUD', 'FALSE_POSITIVE', 'BLOCKED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE fraud.alert_severity AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ----------------------------------------------------------------------------
-- Transactions analysées (alimentée par le job Spark Streaming)
-- Colonnes alignées sur le dataset PaySim + métadonnées de traitement.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fraud.transactions (
    transaction_id      UUID            PRIMARY KEY,
    step                INTEGER         NOT NULL,                 -- heure simulée PaySim (1 step = 1h)
    event_time          TIMESTAMPTZ     NOT NULL,                 -- horodatage d'émission par le producer
    processed_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),   -- horodatage d'insertion par Spark
    type                VARCHAR(20)     NOT NULL,                 -- PAYMENT, TRANSFER, CASH_OUT, CASH_IN, DEBIT
    amount              NUMERIC(18, 2)  NOT NULL CHECK (amount >= 0),
    name_orig           VARCHAR(32)     NOT NULL,
    old_balance_orig    NUMERIC(18, 2)  NOT NULL,
    new_balance_orig    NUMERIC(18, 2)  NOT NULL,
    name_dest           VARCHAR(32)     NOT NULL,
    old_balance_dest    NUMERIC(18, 2)  NOT NULL,
    new_balance_dest    NUMERIC(18, 2)  NOT NULL,
    is_fraud            BOOLEAN         NOT NULL DEFAULT FALSE,   -- label du dataset (vérité terrain)
    is_flagged_fraud    BOOLEAN         NOT NULL DEFAULT FALSE,   -- flag natif PaySim
    risk_score          NUMERIC(5, 2)   DEFAULT 0 CHECK (risk_score BETWEEN 0 AND 100),
    is_suspicious       BOOLEAN         NOT NULL DEFAULT FALSE    -- résultat de nos règles de détection
);

CREATE INDEX IF NOT EXISTS idx_transactions_event_time ON fraud.transactions (event_time DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_name_orig  ON fraud.transactions (name_orig);
CREATE INDEX IF NOT EXISTS idx_transactions_name_dest  ON fraud.transactions (name_dest);
CREATE INDEX IF NOT EXISTS idx_transactions_suspicious ON fraud.transactions (is_suspicious) WHERE is_suspicious;
CREATE INDEX IF NOT EXISTS idx_transactions_type       ON fraud.transactions (type);

-- ----------------------------------------------------------------------------
-- Alertes de fraude (générées par Spark, gérées via l'API Java)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fraud.fraud_alerts (
    alert_id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id      UUID            NOT NULL REFERENCES fraud.transactions (transaction_id) ON DELETE CASCADE,
    rule_name           VARCHAR(64)     NOT NULL,                 -- ex: HIGH_AMOUNT, VELOCITY_5MIN, ACCOUNT_DRAIN
    description         TEXT,
    severity            fraud.alert_severity NOT NULL DEFAULT 'MEDIUM',
    risk_score          NUMERIC(5, 2)   NOT NULL DEFAULT 0,
    status              fraud.alert_status   NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    reviewed_by         VARCHAR(64),
    review_comment      TEXT,
    -- Une seule alerte par (transaction, règle) : rend les écritures Spark idempotentes
    -- (replays de micro-batch, fenêtres glissantes ré-émises en mode update)
    CONSTRAINT uq_alert_transaction_rule UNIQUE (transaction_id, rule_name)
);

CREATE INDEX IF NOT EXISTS idx_alerts_status      ON fraud.fraud_alerts (status);
CREATE INDEX IF NOT EXISTS idx_alerts_created_at  ON fraud.fraud_alerts (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_transaction ON fraud.fraud_alerts (transaction_id);
CREATE INDEX IF NOT EXISTS idx_alerts_severity    ON fraud.fraud_alerts (severity);

-- ----------------------------------------------------------------------------
-- Historique des changements de statut (audit trail pour l'API métier)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fraud.alert_status_history (
    id                  BIGSERIAL       PRIMARY KEY,
    alert_id            UUID            NOT NULL REFERENCES fraud.fraud_alerts (alert_id) ON DELETE CASCADE,
    previous_status     fraud.alert_status,
    new_status          fraud.alert_status NOT NULL,
    changed_by          VARCHAR(64),
    comment             TEXT,
    changed_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- Trigger : mise à jour automatique de updated_at
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fraud.set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_alerts_updated_at ON fraud.fraud_alerts;
CREATE TRIGGER trg_alerts_updated_at
    BEFORE UPDATE ON fraud.fraud_alerts
    FOR EACH ROW EXECUTE FUNCTION fraud.set_updated_at();

-- ----------------------------------------------------------------------------
-- Vue pratique pour l'API : alertes enrichies des infos de transaction
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW fraud.v_alerts_detailed AS
SELECT a.alert_id,
       a.rule_name,
       a.severity,
       a.status,
       a.risk_score,
       a.created_at,
       a.updated_at,
       t.transaction_id,
       t.type,
       t.amount,
       t.name_orig,
       t.name_dest,
       t.event_time,
       t.is_fraud
FROM fraud.fraud_alerts a
JOIN fraud.transactions t ON t.transaction_id = a.transaction_id;

-- Droits par défaut : l'utilisateur applicatif est propriétaire (POSTGRES_USER)
ALTER DATABASE frauddb SET search_path TO fraud, public;
