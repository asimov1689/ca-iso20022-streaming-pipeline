# Corporate Actions ISO 20022 Post-Settlement Pipeline

![CI](https://github.com/asimov1689/ca-iso20022-streaming-pipeline/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-7.5.0-black)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-336791)

Production-grade event streaming pipeline that processes post-settlement corporate action
confirmations in ISO 20022 (MT566 pipe-delimited and seev.036 XML) formats. Six independently
deployable Spring Boot microservices consume from a COBOL batch adapter, normalise, enrich with
reference data, and materialise a queryable master table — served via a Caffeine-cached REST API.

> **Author:** Christian Oliver Jaramillo ([@asimov1689](https://github.com/asimov1689))

---

## Architecture

```
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │  COBOL Batch Layer                                                          │
 │                                                                             │
 │  MT566 pipe-delimited file           seev.036 ISO 20022 XML                │
 │         └──────────────────────────────────┘                               │
 │                          │                                                  │
 └──────────────────────────┼──────────────────────────────────────────────── ┘
                            ▼
               ┌─────────────────────┐
               │   ca-producer       │  REST adapter — COBOL batch job POSTs
               │   port 8081         │  raw confirmations via HTTP
               └──────────┬──────────┘
                          │
                 [ca.confirmations.raw]  ◄─── Kafka topic (3 partitions)
                          │
               ┌──────────▼──────────┐
               │   ca-formatter      │  Parses MT566 + seev.036 → canonical
               │   port 8082         │  CaConfirmationEvent; DLQ on parse error
               └──────────┬──────────┘
                          │
              [ca.confirmations.formatted]
                          │
               ┌──────────▼──────────┐      ┌─────────────────────┐
               │   ca-enricher       │─────►│   ca-cobol-stub     │
               │   port 8083         │      │   port 8086         │
               │   Caffeine 1hr TTL  │      │   ISIN → ref data   │
               └──────────┬──────────┘      └─────────────────────┘
                          │                  (Strangler Fig pattern:
              [ca.confirmations.enriched]     wraps legacy mainframe)
                          │
               ┌──────────▼──────────┐
               │  ca-materializer    │  Idempotent upsert to master table
               │  port 8084          │  (handles duplicate Kafka deliveries)
               └──────────┬──────────┘
                          │
               ┌──────────▼──────────────────────────┐
               │          PostgreSQL                  │
               │  ca_settled_events   (master table)  │
               │  ca_enrichment_log   (audit trail)   │
               └──────────┬──────────────────────────┘
                          │
               ┌──────────▼──────────┐
               │ ca-confirmations-api│  CQRS read side — Caffeine 30s TTL
               │ port 8085           │  Virtual threads (Java 21)
               └─────────────────────┘
```

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 LTS |
| Framework | Spring Boot | 3.3.4 |
| Build | Gradle | 9.x (multi-module) |
| Messaging | Apache Kafka | Confluent 7.5.0 |
| Database | PostgreSQL | 15 |
| Caching | Caffeine | via Spring Cache |
| Concurrency | Virtual Threads | Java 21 (`spring.threads.virtual.enabled`) |
| Testing | JUnit 5, Mockito, AssertJ | via Spring Boot Test |
| Containers | Testcontainers | 1.20.1 |
| Contract Testing | WireMock | 3.3.1 |
| Static Analysis | Checkstyle | 10.17.0 (Google style) |
| CI | GitHub Actions | ubuntu-latest |

---

## Services

| Service | Port | Responsibility |
|---|---|---|
| `ca-producer` | 8081 | REST ingest adapter — receives MT566 / seev.036 from COBOL batch jobs |
| `ca-formatter` | 8082 | Parses raw payloads into canonical `CaConfirmationEvent`; routes failures to DLQ |
| `ca-enricher` | 8083 | Enriches with ISIN reference data (security name, LEI, market); writes audit log |
| `ca-cobol-stub` | 8086 | Simulates the legacy COBOL mainframe reference-data service (Strangler Fig) |
| `ca-materializer` | 8084 | Idempotent upsert of `EnrichedConfirmationEvent` into `ca_settled_events` |
| `ca-confirmations-api` | 8085 | CQRS read side — REST API over the master table with Caffeine caching |

---

## Prerequisites

- **Java 21** (`sdk install java 21-tem` or [Temurin](https://adoptium.net))
- **Docker Desktop** 4.x+ (for local stack and Testcontainers)
- **Gradle** — wrapper included, no installation needed

---

## Quick Start

```bash
# 1. Clone
git clone git@github.com:asimov1689/ca-iso20022-streaming-pipeline.git
cd ca-iso20022-streaming-pipeline

# 2. Build all JARs (skip tests for speed)
./gradlew clean build -x test

# 3. Start the full stack
docker compose up --build

# 4. Submit a test MT566 confirmation
curl -X POST http://localhost:8081/api/v1/ingest/mt566 \
  -H "Content-Type: text/plain" \
  -d "CONF-20261231-001|CH0012221716|DVCA|20261231|2500.00|CHF|ACC-001|1000|SETT"

# 5. Query the master table (allow ~2s for pipeline to process)
curl http://localhost:8085/api/v1/settled-confirmations
```

---

## API Reference

### ca-producer — Ingest

```
POST /api/v1/ingest/mt566
  Content-Type: text/plain
  Body: CONF_REF|ISIN|EVENT_TYPE|SETTLE_DATE|NET_CASH|CCY|ACCOUNT|QTY|STATUS
  → 202 Accepted  { "messageId": "...", "type": "MT566", "status": "ACCEPTED" }

POST /api/v1/ingest/seev036
  Content-Type: application/xml
  Body: <seev.036.001.14> ... </seev.036.001.14>
  → 202 Accepted  { "messageId": "...", "type": "seev.036", "status": "ACCEPTED" }
```

### ca-confirmations-api — Query

```
GET  /api/v1/settled-confirmations
     ?isin=CH0012221716
     &eventType=DVCA
     &accountId=ACC-001
  → 200 OK  [ { ...CaSettledEvent }, ... ]

GET  /api/v1/settled-confirmations/{messageId}
  → 200 OK  { ...CaSettledEvent }
  → 404 Not Found

GET  /api/v1/settled-confirmations/settlement-range?from=20261201&to=20261231
  → 200 OK  [ { ...CaSettledEvent }, ... ]

GET  /api/v1/settled-confirmations/health
  → 200 OK  { "status": "UP", "service": "ca-confirmations-api", "master-table-count": "42" }
```

---

## Kafka Topics

| Topic | Partitions | Producer | Consumer |
|---|---|---|---|
| `ca.confirmations.raw` | 3 | ca-producer | ca-formatter |
| `ca.confirmations.formatted` | 3 | ca-formatter | ca-enricher |
| `ca.confirmations.enriched` | 3 | ca-enricher | ca-materializer |
| `ca.dead-letter` | 1 | ca-formatter (parse errors) | — |

---

## Test Pyramid

The project enforces a 4-layer test pyramid. Each layer runs in isolation in CI.

```
        ▲
       /E2E\         systemTest      — 2 full-stack Testcontainers tests
      /─────\                          (Kafka-direct + File-ingest entry points)
     /  Intg  \      integrationTest — @EmbeddedKafka + PostgreSQLContainer
    /───────────\
   / Contract    \   contractTest    — WireMock (ca-enricher → ca-cobol-stub)
  /───────────────\
 /   Unit Tests    \  test           — Mockito, no I/O
/───────────────────\
```

```bash
# Run each layer individually
./gradlew test                              # unit        (~5s,   no Docker)
./gradlew contractTest                     # contract    (~10s,  WireMock only)
./gradlew integrationTest                  # integration (~2min, Testcontainers)
./gradlew :ca-confirmations-api:systemTest # E2E         (~1min, full Docker stack)

# Run all at once (CI order)
./gradlew checkstyleMain test contractTest integrationTest
```

> **E2E prerequisite:** Docker images must be built first —
> `./gradlew build -x test && docker compose build`

### Test counts by service

| Service | Unit | Integration | Contract | E2E |
|---|---|---|---|---|
| ca-producer | 2 | 2 | — | — |
| ca-formatter | 14 | 2 | — | — |
| ca-cobol-stub | 5 | 4 | — | — |
| ca-enricher | 8 | 2 | 5 | — |
| ca-materializer | 4 | 4 | — | — |
| ca-confirmations-api | 8 | 8 | — | 2 |

---

## Project Structure

```
ca-iso20022-streaming-pipeline/
├── shared-model/               # Java records: RawConfirmationEvent,
│                               # CaConfirmationEvent, EnrichedConfirmationEvent
├── ca-producer/                # REST ingest adapter
├── ca-formatter/               # MT566 + seev.036 parser
├── ca-cobol-stub/              # Legacy mainframe simulator
├── ca-enricher/                # Reference data enrichment + audit log
├── ca-materializer/            # PostgreSQL master table writer
├── ca-confirmations-api/       # CQRS read API
│   └── src/systemTest/         # Full-stack E2E tests
├── infrastructure/
│   └── postgres/init.sql       # DDL for ca_settled_events + ca_enrichment_log
├── config/checkstyle/          # Google Java Style rules
├── docker-compose.yml          # Local dev stack
├── .github/workflows/ci.yml    # GitHub Actions — 4-job CI pipeline
└── build.gradle                # Root Gradle — shared deps, source sets, Checkstyle
```

---

## CI Pipeline

Four jobs run on every push. System tests only run on `main` and PRs to `main`.

```
push/PR
  │
  ├─► checkstyle        (~30s)  Google Java Style — blocks all downstream jobs
  │
  └─► unit-tests        (~1min) No Docker
        │
        ├─► integration-tests  (~2min) Testcontainers (Kafka + PostgreSQL)
        │
        └─► contract-tests     (~30s)  WireMock
              │
              └─► system-tests (main/PR only, ~1min) Full Docker stack E2E
```

---

## Design Decisions

**Why CQRS (separate materializer + confirmations-api)?**
The write side (materializer) and read side (confirmations-api) have independent scaling needs
and failure domains. The API never touches Kafka — it reads a clean, pre-materialised table.

**Why Caffeine over Redis?**
For a single-node portfolio service, Caffeine provides microsecond in-process caching with zero
operational overhead. Redis would be appropriate when multiple API replicas need a shared cache.

**Why idempotent upsert in ca-materializer?**
Kafka guarantees at-least-once delivery. The materializer uses `repository.save()` with a fixed
primary key (`messageId`) so duplicate Kafka messages are safe — the second write is a no-op update.

**Why virtual threads in ca-confirmations-api?**
The API is I/O bound (PostgreSQL queries). Virtual threads (Java 21, `spring.threads.virtual.enabled`)
allow thousands of concurrent requests on a small thread pool without blocking platform threads,
improving throughput under load with no code changes.

**Why the Strangler Fig (ca-cobol-stub)?**
The legacy mainframe is never modified. `ca-cobol-stub` wraps its HTTP interface so `ca-enricher`
is decoupled from the mainframe protocol. In production, only the `cobol.stub.url` property
changes — no service code changes are required.
