# ClaimFlow – Claims Management Platform (MVP)

ClaimFlow lets **claimants** submit insurance claims, **claims officers** assess and process them, and **managers**
monitor workload and financial exposure.

It is built as a **modular monolith** with **hexagonal architecture** (ports & adapters) per module. Domain events
are published to **Kafka** reliably through a **transactional outbox**.

| Concern        | Technology                                                    |
|----------------|---------------------------------------------------------------|
| Runtime        | Java 21, Spring Boot 3.5 (virtual threads enabled)             |
| Persistence    | PostgreSQL 16, Spring Data JPA (Hibernate 6), Flyway          |
| Messaging      | Apache Kafka 3.8 (KRaft), Spring for Apache Kafka             |
| API docs       | springdoc-openapi (OpenAPI 3 + Swagger UI)                    |
| Tests          | JUnit 5, Mockito, AssertJ, ArchUnit, Testcontainers, Awaitility |
| Build / run    | Maven (wrapper included), Docker, Docker Compose              |

> **Design rationale & deep-dive Q&A:** see [INTERVIEW-QA.md](INTERVIEW-QA.md) — explains every
> significant design decision (outbox, state machine, hexagonal boundaries, concurrency) along with
> the trade-offs and known gaps.
>
> **Architecture decisions:** [docs/adr/](docs/adr/README.md) — eight ADRs.
> **AI journal:** [docs/AI-JOURNAL.md](docs/AI-JOURNAL.md) — how AI assistance was used, what it got
> wrong, and how every generated line was verified.

---

## 1. Quick start

### Run everything in Docker

```bash
docker compose up --build            # postgres + kafka + app
docker compose --profile tools up    # ... plus Kafka UI on http://localhost:8081
```

| URL                                         | What                         |
|---------------------------------------------|------------------------------|
| http://localhost:8080/swagger-ui.html       | Swagger UI                   |
| http://localhost:8080/v3/api-docs           | OpenAPI document (15 operations) |
| http://localhost:8080/actuator/health       | Health (liveness/readiness)  |
| http://localhost:8080/actuator/prometheus   | Metrics, Prometheus format   |

A fresh database is seeded with **7 demo claims — one in each lifecycle state** (`REPORTED`, `ASSIGNED`,
`UNDER_REVIEW`, `INFO_REQUIRED`, `APPROVED`, `SETTLED`, `REJECTED`), so the queue, the audit trail and the
exposure dashboard are never empty. The seeder drives the same application ports the REST adapters use, so
the demo data goes through the real state machine and produces real events and history. It is idempotent:
on a database that already holds claims it logs *"Demo seeding skipped"* and does nothing.

### Run the app locally (infrastructure in Docker)

```bash
docker compose up -d postgres kafka
./mvnw spring-boot:run                # Windows: mvnw.cmd spring-boot:run
```

### Configuration (environment variables)

| Variable                        | Default                                      |
|---------------------------------|----------------------------------------------|
| `DB_URL`                        | `jdbc:postgresql://localhost:5432/claimflow` |
| `DB_USERNAME` / `DB_PASSWORD`   | `claimflow` / `claimflow`                    |
| `KAFKA_BOOTSTRAP_SERVERS`       | `localhost:9092`                             |
| `OUTBOX_RELAY_ENABLED`          | `true`                                       |
| `NOTIFICATION_CONSUMER_ENABLED` | `true`                                       |
| `DEMO_SEED_ENABLED`             | `true`                                       |
| `KAFKA_REPLICATION_FACTOR`      | `1`                                          |

Other tunables (`claimflow.outbox.*`, `claimflow.policy.catalog`) live in
[`application.yml`](src/main/resources/application.yml). The toggles above are passed through by
`docker-compose.yml`, so `$env:OUTBOX_RELAY_ENABLED='false'; docker compose up -d app` is enough to stop
publishing without touching the code.

---

## 2. Tests

```bash
./mvnw test      # unit tests: domain, services, outbox relay, web layer, architecture rules (no Docker needed)
./mvnw verify    # + integration tests (*IT) against real PostgreSQL & Kafka via Testcontainers (needs Docker)
```

The integration tests are **skipped, not failed**, when no Docker daemon is available. They share one PostgreSQL
and one Kafka container (singleton container pattern), so the Spring context is started only once.

Current state: **134 unit tests + 16 integration tests, all green.**

| Test                                   | Covers                                                                             |
|----------------------------------------|------------------------------------------------------------------------------------|
| `ClaimStatusTest`, `ClaimTest`         | Full transition matrix, business rules, emitted domain events                      |
| `*ServiceTest`                         | Application services with mocked ports                                             |
| `ClaimAggregateStoreTest`              | Event → topic routing, outbox envelope, audit trail                                |
| `OutboxRelayTest`                      | Publish with key/headers, stop-on-failure ordering, max-attempts → `FAILED`        |
| `ClaimDecisionControllerTest`          | HTTP mapping: 200 / 400 / 404 / 409 / 422 (`@WebMvcTest`)                          |
| `ArchitectureTest`                     | Hexagonal rules (domain framework-free, no adapter→adapter coupling across modules)|
| `ClaimLifecycleIT`                     | End-to-end REST flows, explicit review/reserve, 409/422/400/404 against PostgreSQL |
| `OutboxKafkaIT`                        | DB → outbox → Kafka (key = claimId, headers, ordering) → idempotent consumer       |
| `ExposureIT`                           | Exposure and workload aggregates                                                   |

---

## 3. Architecture

### Modules

```
com.insurer.claimflow
├── intake          Claim Intake         – first notification of loss (POST /claims)
├── workmanagement  Work Management      – assignment / re-assignment of claims to officers
├── assessment      Assessment           – officer findings, reserve recommendation, info requests
├── lifecycle       Lifecycle Management – Claim aggregate + state machine, approve/reject/settle, queries
├── exposure        Exposure Management  – reserve/payment exposure & officer workload (read model)
├── notification    Notification         – Kafka consumer → claimant e-mails / officer notices
├── audit           Audit                – append-only claim_history + GET /claims/{id}/history
├── policy          Policy Reference     – policy lookup & coverage verification
└── shared          Shared kernel        – domain exceptions, outbox, Kafka/OpenAPI config, error handling
```

Every module uses the same hexagonal layout:

```
<module>
├── domain                     pure Java model (no Spring / JPA / Jackson) – enforced by ArchUnit
├── application
│   ├── port/in                use-case interfaces + command records (driving ports)
│   ├── port/out               repository / gateway interfaces (driven ports)
│   └── *Service               use-case implementations, transaction boundaries
└── adapter
    ├── in/web | in/kafka      REST controllers, Kafka listeners
    └── out/persistence | ...  JPA entities & repositories, JDBC read models, Kafka, logging sender
```

Modules talk to each other only through **application ports**, never through each other's adapters.
`lifecycle.api.ClaimResponse` is the one shared published representation of a claim.

### Module collaboration

```
           ┌──────────┐  verifyCoverage   ┌──────────┐
 POST ───▶ │  intake  │ ────────────────▶ │  policy  │
           └────┬─────┘                   └──────────┘
                │ ClaimAggregateStore (load / save)
 ┌──────────────▼───────────────┐   record()   ┌─────────┐
 │ lifecycle (Claim aggregate)  │ ───────────▶ │  audit  │ claim_history
 │ ◀── workmanagement / assess. │              └─────────┘
 └──────────────┬───────────────┘
                │ same DB transaction
                ▼
          outbox_event ──(OutboxRelay)──▶ Kafka ──▶ notification (idempotent consumer)
                                                ──▶ any external subscriber
 exposure ── SQL aggregates over claim ──▶ GET /exposure
```

`ClaimAggregateStore.save` persists the claim and then, for each domain event the aggregate recorded, writes
an outbox row and a `claim_history` row. **State, events and audit trail commit atomically.**

### Claim lifecycle

```
REPORTED ──assign──▶ ASSIGNED ──assessment──▶ UNDER_REVIEW ──approve──▶ APPROVED ──settle──▶ SETTLED
                                                │   ▲
                       assessment outcome       │   │  new assessment
                       INFO_REQUIRED            ▼   │
                                              INFO_REQUIRED

                                              UNDER_REVIEW ──reject──▶ REJECTED
```

| Rule                                         | Enforced by                                   | HTTP |
|----------------------------------------------|-----------------------------------------------|------|
| Claim must be assigned before review         | `Claim.beginReview` (ASSIGNED/INFO_REQUIRED only) | 409  |
| Review required before approval/rejection    | `Claim.approve/reject` (UNDER_REVIEW only)    | 409  |
| Approval required before settlement          | `Claim.settle` (APPROVED only)                | 409  |
| Decided claims cannot be re-assigned         | `Claim.assignTo`                              | 409  |
| Approved ≤ claimed, settled ≤ approved       | `Claim`                                       | 422  |
| Only the assigned officer may assess         | `AssessmentService`                           | 422  |
| Policy exists, active, covers date/currency/limit | `PolicyService`                          | 422  |
| Concurrent update of the same claim          | JPA `@Version`                                | 409  |

**Reserves:** a claim opens with a case reserve equal to the claimed amount. An assessment may adjust it, approval
sets it to the approved amount, and rejection/settlement release it to 0. Every change emits `ReserveChanged`.

### Transactional outbox

1. The business transaction inserts into `outbox_event` (status `PENDING`), wrapping the domain event in an
   envelope: `eventId, eventType, schemaVersion, aggregateType, aggregateId, occurredAt, data`.
2. `OutboxRelay` polls every 500 ms. It locks the next batch with `FOR UPDATE SKIP LOCKED`, so several instances
   can run safely, and sends each record synchronously. The Kafka **key is the claim id**, which keeps per-claim
   ordering, and the headers carry `eventId`, `eventType` and `aggregateId`.
3. On success the row becomes `PUBLISHED`. On failure the batch stops, to preserve ordering, and the attempt is
   counted. After `max-attempts` the row becomes `FAILED` for manual inspection.
4. Published rows are purged daily after `retention` (7 days by default).

Delivery is **at-least-once**. The producer is idempotent with `acks=all`, and consumers de-duplicate on
`eventId` using `processed_event`. A consumer failure is retried with exponential back-off; malformed messages
go straight to `<topic>-dlt`.

### Kafka topics

| Topic                         | Event                | Emitted when                              |
|-------------------------------|----------------------|-------------------------------------------|
| `claims.claim-created.v1`     | `ClaimCreated`       | Claim submitted                           |
| `claims.claim-assigned.v1`    | `ClaimAssigned`      | Claim assigned / re-assigned              |
| `claims.status-changed.v1`    | `ClaimStatusChanged` | Any lifecycle transition                  |
| `claims.reserve-changed.v1`   | `ReserveChanged`     | Reserve set / adjusted / released         |
| `claims.decision-made.v1`     | `DecisionMade`       | Claim approved or rejected                |

Each topic has a `-dlt` companion. Topics are created at startup (3 partitions by default).

### Database (Flyway: `src/main/resources/db/migration`)

| Table             | Purpose                                                                  |
|-------------------|--------------------------------------------------------------------------|
| `claim`           | Aggregate root, status, amounts, reserve, assignee, optimistic `version`  |
| `incident`        | Loss details (1:1 with claim)                                            |
| `assignment`      | Assignment history; partial unique index ⇒ one active assignment / claim |
| `assessment`      | Officer findings and recommendations                                     |
| `claim_history`   | Append-only audit trail (JSONB details)                                  |
| `outbox_event`    | Transactional outbox (JSONB payload)                                     |
| `processed_event` | Consumer idempotency keys                                                |

Hibernate runs with `ddl-auto=validate`; Flyway owns the schema.

---

## 4. API

| Method | Path                                   | Description                                      |
|--------|----------------------------------------|--------------------------------------------------|
| POST   | `/api/v1/claims`                       | Submit a claim → `201` + `Location`              |
| GET    | `/api/v1/claims/{id}`                  | Get a claim                                      |
| GET    | `/api/v1/claims`                       | Search (`status`, `assignedOfficerId`, `policyNumber`, `page`, `size`) |
| POST   | `/api/v1/claims/{id}/assign`           | Assign / re-assign to an officer                 |
| GET    | `/api/v1/claims/{id}/assignments`      | Assignment history                               |
| POST   | `/api/v1/claims/{id}/review`           | Start the review (idempotent) → `UNDER_REVIEW`   |
| POST   | `/api/v1/claims/{id}/reserve`          | Set the case reserve of an open claim            |
| POST   | `/api/v1/claims/{id}/assessments`      | Record an assessment → `201`                     |
| GET    | `/api/v1/claims/{id}/assessments`      | List assessments                                 |
| POST   | `/api/v1/claims/{id}/approve`          | Approve                                          |
| POST   | `/api/v1/claims/{id}/reject`           | Reject                                           |
| POST   | `/api/v1/claims/{id}/settle`           | Settle                                           |
| GET    | `/api/v1/claims/{id}/history`          | Audit trail                                      |
| GET    | `/api/v1/exposure`                     | Exposure & workload dashboard                    |
| GET    | `/api/v1/policies/{policyNumber}`      | Policy reference lookup                          |

Errors use RFC 9457 `application/problem+json` with a machine-readable `code`:

```json
{
  "type": "https://api.claimflow.example/problems/invalid-state-transition",
  "title": "Invalid state transition",
  "status": 409,
  "detail": "Claim 6f1c… cannot transition from REPORTED to APPROVED",
  "code": "INVALID_STATE_TRANSITION",
  "currentStatus": "REPORTED",
  "targetStatus": "APPROVED",
  "timestamp": "2026-10-01T10:00:00Z"
}
```

### Guided demo (recommended)

One command walks the five MVP outcomes end to end, printing each call, its status code and the fields
that matter — including an illegal transition that returns `409`:

```powershell
docker compose up --build -d          # wait for /actuator/health = UP
powershell -ExecutionPolicy Bypass -File scripts\demo.ps1
```

It pauses between sections so you can talk over it; add `-NonInteractive` to let it run straight through,
or `-BaseUrl http://host:port` to point it elsewhere.

| Section | Shows |
|---------|-------|
| 1 | Claimant submits a claim → `201`, claim number, reserve opened at the claimed amount |
| 2 | `GET /claims/{id}` and the timeline from `/history` |
| 3 | Pageable queue (`page`, `size`, `totalElements`), a rejected illegal transition (`409`), assignment, then the officer's own queue |
| 4 | Start review → record assessment → re-reserve → approve; a second claim is rejected to show the other branch |
| 5 | Settlement, a refused second settlement (`409`), the full 12-entry timeline, and the manager's exposure dashboard |

### Outbox & Kafka demo

```powershell
powershell -ExecutionPolicy Bypass -File scripts\demo-outbox.ps1
```

Proves the messaging guarantees by switching the relay off and on again (the app container is recreated
twice, ~30 s in total):

| Section | Shows |
|---------|-------|
| 1 | Relay **off**: a claim is submitted; the `claim` row and its `outbox_event` rows are committed together and sit at `PENDING`. No dual write — and the full event envelope is printed |
| 2 | Relay **on**: the same rows flip to `PUBLISHED`, backlog returns to 0, no `FAILED` rows |
| 3 | The message on `claims.claim-created.v1`: `key equals claim id: True`, headers `eventId`/`eventType`/`aggregateId` |
| 4 | Every lifecycle event routed to its versioned topic, and the `processed_event` table backing consumer idempotency |

The toggles are honoured by Compose, so you can also do it by hand:

```powershell
$env:OUTBOX_RELAY_ENABLED='false'; docker compose up -d app    # stop publishing
docker compose exec -T postgres psql -U claimflow -d claimflow -c "select status, count(*) from outbox_event group by status"
$env:OUTBOX_RELAY_ENABLED='true';  docker compose up -d app    # drain the backlog
```

### Walk-through (curl)


```bash
# 1. Claimant submits a claim (seeded policy POL-1001, EUR, limit 50 000)
ID=$(curl -s -X POST localhost:8080/api/v1/claims -H 'Content-Type: application/json' -d '{
  "policyNumber": "POL-1001",
  "claimant": {"name": "Jane Doe", "email": "jane.doe@example.com", "phone": "+49 30 1234567"},
  "incident": {"type": "AUTO_COLLISION", "date": "2026-09-28", "location": "Berlin",
               "description": "Rear-ended at a traffic light"},
  "claimedAmount": 4500.00,
  "currency": "EUR"
}' | jq -r .id)

# 2. Supervisor assigns it
curl -s -X POST localhost:8080/api/v1/claims/$ID/assign -H 'Content-Type: application/json' \
  -d '{"officerId": "officer-1", "assignedBy": "supervisor-1"}'

# 3. Officer starts the review, records an assessment (→ UNDER_REVIEW) and re-reserves
curl -s -X POST localhost:8080/api/v1/claims/$ID/review -H 'Content-Type: application/json' \
  -d '{"reviewerId": "officer-1"}'
curl -s -X POST localhost:8080/api/v1/claims/$ID/assessments -H 'Content-Type: application/json' \
  -d '{"assessorId": "officer-1", "outcome": "RECOMMEND_APPROVAL", "findings": "Damage verified", "recommendedReserve": 4200}'
curl -s -X POST localhost:8080/api/v1/claims/$ID/reserve -H 'Content-Type: application/json' \
  -d '{"reserveAmount": 3900.00, "reason": "Revised repair estimate", "actor": "officer-1"}'

# 4. Manager approves, finance settles
curl -s -X POST localhost:8080/api/v1/claims/$ID/approve -H 'Content-Type: application/json' \
  -d '{"approvedAmount": 4000, "decidedBy": "manager-1", "notes": "Covered under section 2"}'
curl -s -X POST localhost:8080/api/v1/claims/$ID/settle -H 'Content-Type: application/json' \
  -d '{"settlementAmount": 4000, "paymentReference": "PAY-0001", "settledBy": "finance-1"}'

# 5. Audit trail and dashboard
curl -s localhost:8080/api/v1/claims/$ID/history | jq
curl -s localhost:8080/api/v1/exposure | jq

# Business rule: settling a non-approved claim → 409
```

Seeded policies (`claimflow.policy.catalog`): `POL-1001` (EUR 50 000, motor), `POL-1002` (EUR 250 000, home),
`POL-2001` (lapsed), `POL-3001` (USD 100 000, health).

### Postman

A ready-to-run collection of **53 requests** covering the complete flow (happy path, every business-rule
violation, reject/info-required branch, explicit review & reserve, queries, audit trail and exposure) lives
in [`postman/`](postman/README.md):

```bash
newman run postman/ClaimFlow.postman_collection.json -e postman/ClaimFlow-Local.postman_environment.json
```

The collection is maintained in Postman's directory format (one `.request.yaml` per request) so that it
reviews well in git; the two JSON files Newman consumes are generated from it by
[`scripts/build-postman-collection.ps1`](scripts/build-postman-collection.ps1).

After a run, [`scripts/verify-db.sql`](scripts/verify-db.sql) checks that the data really landed in
PostgreSQL (row counts, audit trail, outbox payloads, and three consistency queries that must return
zero rows).

---

## 5. Assumptions & next steps

* **Authentication/authorisation is out of scope for the MVP.** Actors are passed explicitly (`assignedBy`,
  `decidedBy`, `X-User-Id`). Next step: OAuth2 resource server (JWT) with role-based access
  (CLAIMANT / OFFICER / MANAGER / FINANCE) and actors derived from the token.
* **Policy Reference** is backed by a configured catalogue. In production, replace `ConfiguredPolicyCatalogAdapter`
  with a client for the policy administration system; the port stays unchanged.
* **Notifications** are logged by `LoggingNotificationSender`. Swap in an SMTP/e-mail-service adapter.
* The outbox relay keeps its DB transaction open while sending a batch (bounded by `batch-size` × `send-timeout`).
  That is simple and safe at MVP scale. At higher volume, use Debezium CDC on `outbox_event` instead.
* Single currency per claim (must match the policy). No FX conversion in exposure figures; they are grouped by
  currency.
* Exposure is computed on read with SQL aggregates. If the volume grows, project it into a dedicated read model
  from the `reserve-changed` / `status-changed` topics.
