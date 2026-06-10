CREATE TABLE IF NOT EXISTS ca_settled_events (
    message_id          VARCHAR(64)     PRIMARY KEY,
    confirmation_ref    VARCHAR(64)     NOT NULL,
    isin                VARCHAR(12)     NOT NULL,
    event_type          VARCHAR(4)      NOT NULL,
    settlement_date     VARCHAR(8),
    net_cash_amount     NUMERIC(18,6),
    currency            VARCHAR(3),
    account_id          VARCHAR(64),
    quantity            NUMERIC(18,6),
    status              VARCHAR(8),
    source_format       VARCHAR(8),
    security_name       VARCHAR(256),
    issuer_lei          VARCHAR(64),
    market_of_listing   VARCHAR(128),
    settle_ccy          VARCHAR(3),
    received_at         TIMESTAMPTZ,
    enriched_at         TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS ca_enrichment_log (
    enrichment_id       BIGSERIAL       PRIMARY KEY,
    message_id          VARCHAR(64)     NOT NULL UNIQUE,
    isin                VARCHAR(12)     NOT NULL,
    security_name       VARCHAR(256),
    issuer_lei          VARCHAR(64),
    market_of_listing   VARCHAR(128),
    settle_ccy          VARCHAR(3),
    cached              BOOLEAN         NOT NULL,
    cobol_response_ms   INTEGER,
    enriched_at         TIMESTAMPTZ     NOT NULL
);
