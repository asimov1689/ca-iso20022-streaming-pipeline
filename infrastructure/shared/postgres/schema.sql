-- ================================================================
-- TABLE 1: ca_enrichment_log
-- Written by:  ca-enricher
-- Purpose:     Audit log — every COBOL reference-data lookup.
--              Records whether Caffeine cache served the result
--              or the mainframe was called, and latency in ms.
-- ================================================================
CREATE TABLE IF NOT EXISTS ca_enrichment_log (
                                                 enrichment_id      BIGSERIAL     PRIMARY KEY,
                                                 message_id         VARCHAR(64)   NOT NULL,
    isin               VARCHAR(12)   NOT NULL,
    security_name      VARCHAR(256),
    issuer_lei         VARCHAR(64),
    market_of_listing  VARCHAR(128),
    settle_ccy         VARCHAR(3),
    cached             BOOLEAN       NOT NULL DEFAULT false,
    cobol_response_ms  INTEGER,       -- NULL when cached=true
    enriched_at        TIMESTAMPTZ   DEFAULT now()
    );
CREATE INDEX IF NOT EXISTS idx_enr_isin        ON ca_enrichment_log (isin);
CREATE INDEX IF NOT EXISTS idx_enr_message_id  ON ca_enrichment_log (message_id);
CREATE INDEX IF NOT EXISTS idx_enr_cached      ON ca_enrichment_log (cached);
CREATE INDEX IF NOT EXISTS idx_enr_at          ON ca_enrichment_log (enriched_at DESC);

-- ================================================================
-- TABLE 2: ca_settled_events  (MASTER TABLE)
-- Written by:  ca-materializer (idempotent upsert)
-- Read by:     ca-confirmations-api (with Caffeine cache)
-- Purpose:     Authoritative post-settlement CA record.
--              Source of truth for custody / tax / reconciliation.
-- ================================================================
CREATE TABLE IF NOT EXISTS ca_settled_events (
                                                 message_id         VARCHAR(64)   PRIMARY KEY,
    confirmation_ref   VARCHAR(64)   NOT NULL,
    isin               VARCHAR(12)   NOT NULL,
    event_type         VARCHAR(4)    NOT NULL,
    settlement_date    VARCHAR(8),
    net_cash_amount    NUMERIC(18,6),
    currency           VARCHAR(3),
    account_id         VARCHAR(64),
    quantity           NUMERIC(18,6),
    status             VARCHAR(8),
    source_format      VARCHAR(8),
    security_name      VARCHAR(256),
    issuer_lei         VARCHAR(64),
    market_of_listing  VARCHAR(128),
    settle_ccy         VARCHAR(3),
    received_at        TIMESTAMPTZ,
    enriched_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   DEFAULT now(),
    updated_at         TIMESTAMPTZ   DEFAULT now()
    );
CREATE INDEX IF NOT EXISTS idx_se_isin         ON ca_settled_events (isin);
CREATE INDEX IF NOT EXISTS idx_se_conf_ref     ON ca_settled_events (confirmation_ref);
CREATE INDEX IF NOT EXISTS idx_se_event_type   ON ca_settled_events (event_type);
CREATE INDEX IF NOT EXISTS idx_se_settle_date  ON ca_settled_events (settlement_date);
CREATE INDEX IF NOT EXISTS idx_se_account_id   ON ca_settled_events (account_id);
CREATE INDEX IF NOT EXISTS idx_se_created_at   ON ca_settled_events (created_at DESC);

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$ BEGIN
    NEW.updated_at = now(); RETURN NEW;
END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_settled_updated
    BEFORE UPDATE ON ca_settled_events
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
