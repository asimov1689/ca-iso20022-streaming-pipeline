# Corporate Actions ISO 20022 Streaming Pipeline

![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-7.5.0-black)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-336791)

I built a six-service event-driven post-settlement pipeline for corporate actions.
It ingests SWIFT MT566 and ISO 20022 `seev.036` confirmations, normalizes them into
a canonical event stream, enriches them with legacy reference data, materializes
settled state in PostgreSQL, and serves a read-optimized API.

## Architectural Highlights

- Event backbone built on Kafka with explicit topic boundaries between raw, formatted, and enriched events.
- Canonical event model in `shared-model`, so MT566 and `seev.036` converge early and the rest of the pipeline stays format-agnostic.
- Idempotent materialization keyed by `messageId`, which makes duplicate Kafka deliveries safe.
- Clean read side separation: write path stops at PostgreSQL, query path starts at `ca-confirmations-api`.
- Legacy integration isolated behind `ca-cobol-stub`, following a Strangler Fig boundary instead of leaking mainframe concerns into the rest of the system.
- Parse failures go to a dead-letter topic instead of contaminating downstream state.
- Full-stack system coverage proves the pipeline from Kafka consumption and both ingest endpoints through to the OpenAPI-backed REST layer.

## Topology

```text
MT566 text / seev.036 XML
          |
          v
   ca-producer (8081)
          |
          v
   ca.confirmations.raw
          |
          v
   ca-formatter (8082)
          |
          +--> ca.dead-letter
          |
          v
   ca.confirmations.formatted
          |
          v
   ca-enricher (8083) ---> ca-cobol-stub (8086)
          |
          v
   ca.confirmations.enriched
          |
          v
   ca-materializer (8084)
          |
          v
      PostgreSQL
          |
          v
   ca-confirmations-api (8085)
```

## Services

| Service | Port | Responsibility |
|---|---|---|
| `ca-producer` | 8081 | Ingest adapter for MT566 and `seev.036` payloads |
| `ca-formatter` | 8082 | Parses raw payloads into canonical confirmation events |
| `ca-enricher` | 8083 | Adds reference data and writes enrichment audit records |
| `ca-cobol-stub` | 8086 | Legacy reference-data boundary used by the enricher |
| `ca-materializer` | 8084 | Upserts enriched events into the settled-events store |
| `ca-confirmations-api` | 8085 | Read-side API over the materialized state |

## Core Contracts

### Ingest

| Method | Path | Content-Type |
|---|---|---|
| `POST` | `/api/v1/ingest/mt566` | `text/plain` |
| `POST` | `/api/v1/ingest/seev036` | `application/xml`, `text/xml` |

### Query

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/settled-confirmations` | Optional filters: `isin`, `eventType`, `accountId` |
| `GET` | `/api/v1/settled-confirmations/{messageId}` | Point lookup by message id |
| `GET` | `/api/v1/settled-confirmations/settlement-range` | Required `from` and `to` |
| `GET` | `/api/v1/settled-confirmations/health` | Service and row-count health check |

## Kafka Topics

| Topic | Partitions | Producer | Consumer |
|---|---|---|---|
| `ca.confirmations.raw` | 3 | `ca-producer` | `ca-formatter` |
| `ca.confirmations.formatted` | 3 | `ca-formatter` | `ca-enricher` |
| `ca.confirmations.enriched` | 3 | `ca-enricher` | `ca-materializer` |
| `ca.dead-letter` | 1 | `ca-formatter` | None |

## Validation

The repo is validated as a four-layer test pyramid:

- `test`: unit coverage
- `contractTest`: WireMock contract checks
- `integrationTest`: Kafka and PostgreSQL integration coverage
- `systemTest`: full Docker/Testcontainers end-to-end coverage

The full-stack suite proves three production-relevant flows:

- Kafka raw confirmation -> formatter -> enricher -> materializer -> API
- MT566 ingest endpoint -> full pipeline -> settled confirmation API
- `seev.036` ingest endpoint -> full pipeline -> settled confirmation API

Run the full gate locally with:

```bash
./gradlew allLayersEndToEnd --rerun-tasks --console=plain
```

## Runtime Modes

| Mode | Purpose | Files |
|---|---|---|
| `sandbox` | Local full-stack validation with Kafka, PostgreSQL, and the COBOL stub | `infrastructure/sandbox/*` |
| `productive` | Production-like app container contract with externalized infrastructure | `infrastructure/productive/*` |

## Stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.4 |
| Build | Gradle multi-module |
| Messaging | Apache Kafka |
| Database | PostgreSQL 15 |
| Caching | Caffeine |
| Testing | JUnit 5, Mockito, AssertJ, WireMock, Testcontainers |
| CI | GitHub Actions |

## Repository Layout

```text
shared-model/            Shared event records
ca-producer/             Ingest adapter
ca-formatter/            MT566 and seev.036 parsing
ca-enricher/             Reference data enrichment
ca-cobol-stub/           Legacy boundary simulator
ca-materializer/         Settled-state writer
ca-confirmations-api/    Read-side API
infrastructure/          Sandbox and productive compose assets
config/checkstyle/       Static analysis rules
```
