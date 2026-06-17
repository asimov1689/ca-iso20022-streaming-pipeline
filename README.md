# Corporate Actions ISO 20022 Post-Settlement Pipeline

![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-7.5.0-black)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-336791)

In this project, I build a focused event-driven Corporate Actions post-settlement platform using
Java 21, Spring Boot, Kafka, PostgreSQL, and COBOL reference-data enrichment. It supports SWIFT
MT566 and ISO 20022 seev.036 confirmations, with validation across unit, contract, integration,
and full-stack system tests.

The system is modelled as six independently deployable Spring Boot services that ingest, normalise,
enrich, materialise, and serve corporate action settlement data through Kafka, PostgreSQL, and a
cached REST read side. Raw batch confirmations from legacy systems move through this
service-oriented streaming pipeline to become queryable settlement records.

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
| Containers | Testcontainers | 1.21.4 |
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

## Platform Requirements

| Capability | Requirement |
|---|---|
| Java runtime | Java 21 LTS |
| Build system | Gradle wrapper |
| Container runtime | Docker-compatible runtime for Kafka, PostgreSQL, and Testcontainers |
| CI runner | GitHub Actions `ubuntu-latest` |

---

## API Reference

### ca-producer — Ingest

| Method | Path | Content type | Payload | Response |
|---|---|---|---|---|
| POST | `/api/v1/ingest/mt566` | `text/plain` | `CONF_REF\|ISIN\|EVENT_TYPE\|SETTLE_DATE\|NET_CASH\|CCY\|ACCOUNT\|QTY\|STATUS` | `202 Accepted` with message metadata |
| POST | `/api/v1/ingest/seev036` | `application/xml` | ISO 20022 `seev.036.001.14` document | `202 Accepted` with message metadata |

### ca-confirmations-api — Query

| Method | Path | Parameters | Response |
|---|---|---|---|
| GET | `/api/v1/settled-confirmations` | Optional `isin`, `eventType`, `accountId` | List of `CaSettledEvent` records |
| GET | `/api/v1/settled-confirmations/{messageId}` | `messageId` path variable | Single `CaSettledEvent` or `404 Not Found` |
| GET | `/api/v1/settled-confirmations/settlement-range` | Required `from`, `to` settlement dates | List of matching `CaSettledEvent` records |
| GET | `/api/v1/settled-confirmations/health` | None | Service status and master-table count |

---

## Kafka Topics

| Topic | Partitions | Producer | Consumer |
|---|---|---|---|
| `ca.confirmations.raw` | 3 | ca-producer | ca-formatter |
| `ca.confirmations.formatted` | 3 | ca-formatter | ca-enricher |
| `ca.confirmations.enriched` | 3 | ca-enricher | ca-materializer |
| `ca.dead-letter` | 1 | ca-formatter (parse errors) | — |

---

## Validation Model

The project uses a four-layer validation model. Each layer is isolated in CI.

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

## Runtime Profiles

The repo separates productive runtime configuration from the sandbox used for local and CI validation.

| Runtime | Purpose | Configuration source |
|---|---|---|
| `productive` | Production-like deployment contract for app containers only | Platform environment variables plus `infrastructure/productive/docker-compose.yml` |
| `sandbox` | Local full-stack testing with Kafka, PostgreSQL, and the COBOL stub | `infrastructure/sandbox/sandbox.env` plus `infrastructure/sandbox/docker-compose.yml` |

The productive runtime expects managed infrastructure dependencies and externalised credentials.
Sandbox runtime is isolated and disposable, with local Kafka, PostgreSQL, and stubbed reference data.
Sandbox credentials and `application-sandbox.yml` defaults are not production configuration.

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
│   ├── productive/             # Production-like app container topology
│   ├── sandbox/                # Local disposable STP test stack
│   └── shared/                 # Shared DDL and Kafka topic bootstrap assets
├── config/checkstyle/          # Google Java Style rules
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
For a single-node query service, Caffeine provides microsecond in-process caching with zero
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
