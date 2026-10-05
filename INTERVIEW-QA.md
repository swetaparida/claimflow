# ClaimFlow – Interview Questions & Answers

Prep guide for defending this codebase in a technical interview. Every answer is grounded in the
actual implementation, with file references so you can jump to the code.

**Golden rule:** when asked "why", answer with the *trade-off*, not just the mechanism. Interviewers
grade reasoning, not recall.

---

## Table of contents

1. [Project overview / "walk me through it"](#1-project-overview)
2. [Modular monolith & hexagonal architecture](#2-modular-monolith--hexagonal-architecture)
3. [Domain modelling & DDD](#3-domain-modelling--ddd)
4. [Business rules & the state machine](#4-business-rules--the-state-machine)
5. [The Outbox pattern](#5-the-outbox-pattern)
6. [Kafka & messaging](#6-kafka--messaging)
7. [Database, JPA & Flyway](#7-database-jpa--flyway)
8. [Transactions & concurrency](#8-transactions--concurrency)
9. [REST API design & error handling](#9-rest-api-design--error-handling)
10. [Testing strategy](#10-testing-strategy)
11. [Build, Docker & configuration](#11-build-docker--configuration)
12. [Insurance domain knowledge](#12-insurance-domain-knowledge)
13. [Hard / curveball questions](#13-hard--curveball-questions)
14. [Known gaps & "what next"](#14-known-gaps--what-next)
15. [Rapid-fire one-liners](#15-rapid-fire-one-liners)

---

## 1. Project overview

### Q1.1 — "Walk me through your project in 2 minutes."

ClaimFlow is a claims management platform for an insurer. It serves three personas: **claimants**
submit claims, **claims officers** assess and decide them, and **managers** monitor workload and
financial exposure.

It is a **modular monolith** — eight business modules (intake, work management, assessment,
lifecycle, exposure, notification, audit, policy reference) in one deployable, each internally
structured with **hexagonal architecture**.

A claim moves through a strict lifecycle: `REPORTED → ASSIGNED → UNDER_REVIEW → APPROVED → SETTLED`,
with `REJECTED` and `INFO_REQUIRED` as alternative branches from review. That state machine is
enforced inside the `Claim` aggregate, so the rules hold no matter which entry point is used.

Every state change emits domain events. These are written to an **outbox table in the same database
transaction** as the state change, then relayed to Kafka by a background poller — so we never lose an
event or publish one for a transaction that rolled back.

Stack: Java 21, Spring Boot 3.5, PostgreSQL 16, Kafka 3.8 (KRaft), Flyway, springdoc-openapi, Docker
Compose. Tested with JUnit 5, Mockito, ArchUnit and Testcontainers — 134 unit tests plus
container-backed integration tests.

### Q1.2 — "Why these technology choices?"

| Choice | Why |
|---|---|
| Modular monolith | MVP scope. One deploy, one transaction boundary, no distributed-transaction pain — but module seams are explicit, so any module can be extracted into a service later. |
| Hexagonal | Keeps insurance business rules independent of Spring/JPA/Kafka. The domain is plain Java and testable without a container. |
| PostgreSQL | Needed ACID for money. Also gave `JSONB` for event payloads, `FOR UPDATE SKIP LOCKED` for the outbox relay, and partial indexes. |
| Kafka | Durable, replayable event log. Downstream consumers (notification now; fraud, analytics, payments later) subscribe without the core knowing about them. |
| Outbox | The only way to make "save state + publish event" atomic without 2PC. |
| Flyway | Versioned, repeatable schema. Paired with `ddl-auto: validate` so Hibernate can never silently alter production schema. |

---

## 2. Modular monolith & hexagonal architecture

### Q2.1 — "What is a modular monolith, and why not microservices?"

A modular monolith is a single deployable unit internally partitioned into modules with explicit
boundaries and dependencies, as if they were separate services.

For an MVP, microservices would have bought distributed tracing, network failure modes, eventual
consistency everywhere and 8 deployment pipelines — in exchange for scaling independence we don't
yet need. A monolith gives us **one ACID transaction** spanning claim state, audit history and the
outbox, which is exactly the guarantee claims processing needs.

The key point: because boundaries are enforced (by package structure *and* ArchUnit tests), extracting
a module later is a refactor, not a rewrite. The modules already communicate through ports and
events — the same way they would across a network.

### Q2.2 — "Explain hexagonal architecture as you implemented it."

Each module has the same internal shape:

```
<module>/
├── domain/                     pure Java — entities, value objects, domain events
├── application/
│   ├── port/in/                use-case interfaces (driving ports)
│   ├── port/out/               SPI interfaces the module needs (driven ports)
│   └── <Service>.java          use-case implementations, orchestration, @Transactional
└── adapter/
    ├── in/web/                 REST controllers (driving adapters)
    ├── in/kafka/               Kafka listeners
    └── out/persistence|jdbc|…  repository / external system implementations (driven adapters)
```

Dependencies point **inward only**. The application layer declares what it needs as an interface
(`ClaimRepositoryPort`, `NotificationSenderPort`), and adapters implement it. Spring wires the
implementation in at runtime.

The payoff: I can swap PostgreSQL for DynamoDB, or REST for gRPC, by writing a new adapter — zero
changes to business logic. And I can unit-test every service with plain mocks, no Spring context.

### Q2.3 — "How do you *enforce* the architecture? What stops someone importing JPA into the domain?"

ArchUnit tests — `src/test/java/com/insurer/claimflow/architecture/ArchitectureTest.java`. Five rules
run on every build:

1. **`domainIsFrameworkFree`** — nothing in `..domain..` may depend on Spring, `jakarta.persistence`,
   Hibernate, Kafka, Jackson, `..adapter..` or `..application..`.
2. **`applicationDoesNotDependOnAdapters`** — the core talks outward only through ports.
3. **`applicationDoesNotUseJpaKafkaOrWeb`** — persistence/messaging/HTTP tech stays in adapters.
4. **`adaptersOfDifferentModulesAreIndependent`** — a slice rule; module adapters can't call each other.
5. **`portsAreInterfacesOrRecords`** — ports are contracts, not classes.

This is the difference between architecture that's *documented* and architecture that's *enforced*.
A convention in a README decays; a failing build does not.

### Q2.4 — "How do modules talk to each other?"

Two ways, deliberately:

- **Synchronously, through application ports** when the caller needs an immediate answer and the same
  transaction. Example: `ClaimIntakeService` calls `PolicyQueryUseCase.verifyCoverage(...)` before
  registering a claim — if the policy is lapsed, nothing is written.
- **Asynchronously, via Kafka events** when the work is a side effect that must not fail the
  transaction. Example: notifications. If the email system is down, the claim is still approved.

The rule of thumb I applied: *if failure of the collaborator should abort the business operation, call
it synchronously; otherwise, publish an event.*

### Q2.5 — "`ClaimIntakeService` imports `lifecycle.application.ClaimAggregateStore`. Isn't that a module boundary violation?" ⚠️

**This is the sharpest critique of the design — be ready for it.**

Honest answer: it's a deliberate trade-off, and it is the one place I'd change first.

`Claim` is a single aggregate root owned by the lifecycle module. Intake, work management and
assessment all need to mutate it. I exposed `ClaimAggregateStore` as the lifecycle module's
**published API** — the one sanctioned way in. The alternative, duplicating a claim model per module,
would have fragmented the invariants, which is far worse for a money-handling system.

Why it's still imperfect: it's a concrete class, not an interface, so the dependency is on an
implementation rather than a contract. ArchUnit permits it (the rules forbid application→adapter and
adapter↔adapter coupling, not application→application), but that's a gap in my rules, not a
justification.

**How I'd fix it:** define `ClaimAggregatePort` in a shared/published-language package, have lifecycle
implement it, and add an ArchUnit rule that modules may only depend on `..api..` or `..port..`
packages of other modules. That makes the seam explicit and extraction-ready.

> This answer scores highly: it shows you understand the rule, know you bent it, know *why*, and know
> the fix. Never pretend it isn't there.

---

## 3. Domain modelling & DDD

### Q3.1 — "What is your aggregate root and why?"

`Claim` (`lifecycle/domain/Claim.java`) is the aggregate root. It owns the `Incident`, the status, the
assigned officer and all the money fields (claimed, reserve, approved, settled).

It's the consistency boundary: "approved amount must not exceed claimed amount" and "settlement must
not exceed approval" are invariants that span those fields, so they must be enforced by one object
loaded and saved as a unit.

`Assignment` and `Assessment` are **separate aggregates** that merely reference `claimId`. They have
independent lifecycles — a claim can be reassigned many times and accumulate many assessments — and
they don't participate in the claim's invariants. Keeping them out of the `Claim` aggregate keeps it
small and avoids loading an unbounded collection to change one field.

### Q3.2 — "Show me that your domain model is genuinely rich, not anaemic."

`Claim` has no public setters. Every mutation goes through a behaviour method that enforces rules and
records an event — `assignTo`, `beginReview`, `requestInformation`, `adjustReserve`, `approve`,
`reject`, `settle`.

Example, `approve()`:

```java
public void approve(BigDecimal amount, String notes, String actor, Instant now) {
    ensureCanTransitionTo(ClaimStatus.APPROVED);          // review-before-approval
    BigDecimal approved = money(amount);
    if (approved.signum() <= 0) throw new BusinessRuleViolationException("INVALID_APPROVED_AMOUNT", …);
    if (approved.compareTo(claimedAmount) > 0)
        throw new BusinessRuleViolationException("APPROVED_AMOUNT_EXCEEDS_CLAIM", …);
    approvedAmount = approved;
    transitionTo(ClaimStatus.APPROVED, notes, actor, now);
    register(new DecisionMade(…));
    changeReserve(approved, "APPROVAL", actor, now);       // reserve follows the decision
}
```

Four things happen atomically in the model: validate transition, validate money, change state, emit
events. There is no way to construct an invalid `Claim` — the constructor is private and the only
entry points are the `submit()` factory and `reconstitute()` for loading.

Contrast with an anaemic model: a `ClaimEntity` with getters/setters and a `ClaimService` with 300
lines of `if` statements. That logic leaks and gets duplicated; this doesn't.

### Q3.3 — "How do you keep JPA out of the domain?"

Two separate models plus an explicit mapping step:

- `lifecycle/domain/Claim.java` — pure Java, no annotations.
- `lifecycle/adapter/out/persistence/ClaimJpaEntity.java` — the `@Entity`.
- `ClaimPersistenceAdapter` maps between them and implements `ClaimRepositoryPort`.

`Claim.reconstitute(...)` is the factory used when loading — it rebuilds state **without emitting
events**, which matters because replaying a load must not re-publish `ClaimCreated`.

Cost: mapping code to maintain. Benefit: the domain can use private constructors, final fields and
invariant-enforcing factories — all things JPA fights you on (it wants a no-arg constructor and
mutable fields). It also means ~130 domain unit tests run in milliseconds with no database.

### Q3.4 — "How are domain events produced and dispatched?"

The aggregate records events internally in a `pendingEvents` list via `register(...)`. Nothing is
published from inside the domain — it has no idea Kafka exists.

`ClaimAggregateStore.save()` drains them:

```java
@Transactional(propagation = Propagation.MANDATORY)
public Claim save(Claim claim) {
    repository.save(claim);
    for (ClaimDomainEvent event : claim.pullDomainEvents()) {
        outbox.append(AGGREGATE_TYPE, claim.getId(), topicFor(event), event);
        auditTrail.record(toHistory(event));
    }
    return claim;
}
```

One loop, one transaction: state, outbox and audit history commit or roll back together.
`pullDomainEvents()` clears the list so a double-save can't duplicate events.

`topicFor(...)` uses a **switch over a sealed interface**, so if someone adds a new event type the
compiler forces them to assign it a topic — no silently-dropped events.

---

## 4. Business rules & the state machine

### Q4.1 — "Walk me through the lifecycle and how it's enforced."

The transition table lives in `ClaimStatus.allowedTransitions()`:

| From | Allowed to |
|---|---|
| `REPORTED` | `ASSIGNED` |
| `ASSIGNED` | `UNDER_REVIEW` |
| `UNDER_REVIEW` | `APPROVED`, `REJECTED`, `INFO_REQUIRED` |
| `INFO_REQUIRED` | `UNDER_REVIEW` |
| `APPROVED` | `SETTLED` |
| `REJECTED`, `SETTLED` | — (terminal) |

The four stated business rules fall out of this table automatically:

- *Assigned before review* — `UNDER_REVIEW` is only reachable from `ASSIGNED`/`INFO_REQUIRED`, both of
  which require an officer.
- *Review before approval/rejection* — `APPROVED`/`REJECTED` are only reachable from `UNDER_REVIEW`.
- *Approval before settlement* — `SETTLED` is only reachable from `APPROVED`.
- *Invalid transition → 409* — `ensureCanTransitionTo` throws `InvalidStateTransitionException`, mapped
  to `HttpStatus.CONFLICT`.

Defining it as data in one enum rather than scattered `if` statements means the rules are auditable at
a glance, and `ClaimStatusTest` exhaustively verifies **all 56 (status × status) combinations**.

### Q4.2 — "Why 409 Conflict and not 400 Bad Request?"

Because the request is **syntactically valid** — it's the *current state of the resource* that makes it
impossible. "Settle this claim" is a perfectly well-formed request; it fails only because the claim
hasn't been approved yet.

RFC 9110 defines 409 as "the request could not be completed due to a conflict with the current state of
the target resource" — exactly this. 400 would imply the client should fix the payload, which is
misleading: the client should fix the *workflow order*, or retry after the claim advances.

We also return a problem detail with `currentStatus` and `targetStatus` properties so the client knows
*why*, not just that it failed.

### Q4.3 — "Where do you enforce rules — domain, service, database, or API?"

All four, as defence in depth, each with a distinct job:

| Layer | Enforces | Example |
|---|---|---|
| **Bean Validation** (DTO) | Shape of input | `@NotNull`, `@Positive` on `SubmitClaimRequest` |
| **Domain** (`Claim`) | Business invariants | approved ≤ claimed; transition legality |
| **Application service** | Cross-aggregate policy | only the assigned officer may assess |
| **Database** | Last line of defence | `CHECK (settled_amount <= approved_amount)` |

The domain is the *authoritative* layer. The database constraints exist because data outlives code —
a future bug, a bad migration or a manual `UPDATE` shouldn't be able to corrupt financial records.

### Q4.4 — "Why is `beginReview()` idempotent but `assignTo()` not?"

Different semantics:

```java
public void beginReview(String actor, Instant now) {
    if (status == ClaimStatus.UNDER_REVIEW) return;   // no-op
    transitionTo(ClaimStatus.UNDER_REVIEW, …);
}
```

An officer filing a *second* assessment on a claim already under review is normal — multiple
assessments per claim are expected — so re-entering review must be a no-op, not an error.

Whereas assigning a claim to the officer who already owns it is almost certainly a mistake or a
double-submit, so it throws `ALREADY_ASSIGNED` (422). Re-assigning to a *different* officer is allowed
and keeps the current status — handover happens mid-review in real claims teams.

### Q4.5 — "Explain the reserve logic. Why does the reserve change on approval?"

A **reserve** is the insurer's estimate of what a claim will ultimately cost — it's a liability on the
balance sheet and drives regulatory capital. It must always reflect best current knowledge:

| Moment | Reserve becomes | Why |
|---|---|---|
| Claim submitted | claimed amount | Worst case, nothing known yet |
| Assessment recorded | assessor's recommendation | Expert estimate replaces the guess |
| Approved | approved amount | We now know what we owe |
| Rejected | 0 | We owe nothing |
| Settled | 0 | Paid — liability moves from reserve to paid |

`changeReserve()` is a no-op when the value is unchanged (`compareTo == 0`), so we never emit a noise
`ReserveChanged` event. Finance consumes `claims.reserve-changed.v1` to keep the general ledger in
sync, so false events would be actively harmful.

### Q4.6 — "A claim is `APPROVED`. Can the reserve still be adjusted?"

Yes. `ClaimStatus.OPEN` includes `APPROVED` because an approved-but-unpaid claim still carries
exposure — the money hasn't left yet. `adjustReserve` rejects changes only on `REJECTED`/`SETTLED`
(throws `CLAIM_CLOSED`, 422). That mirrors how insurers actually account for outstanding claims.

---

## 5. The Outbox pattern

### Q5.1 — "What problem does the outbox solve?"

The **dual-write problem**. Naively:

```java
claimRepository.save(claim);        // commits to Postgres
kafkaTemplate.send(event);          // separate system
```

Two systems, no shared transaction. Three failure modes:
- Crash between them → DB updated, event lost → downstream permanently inconsistent.
- Kafka send fails → same.
- Send succeeds, then DB transaction rolls back → **event published for something that never happened**.
  Worst case: a claimant is emailed "your claim was approved" for an approval that doesn't exist.

The outbox removes the second system from the write path entirely. The event is inserted into
`outbox_event` **in the same transaction** as the claim update, so it's atomic by construction. A
separate relay process reads committed rows and publishes them.

### Q5.2 — "Walk me through your implementation."

**Write side** — `OutboxWriter.append(...)`, annotated `@Transactional(propagation = MANDATORY)`.
`MANDATORY` is the critical detail: it *throws* if there's no active transaction. That makes it
impossible to accidentally call it outside the business transaction and silently lose the atomicity
guarantee. It wraps the domain event in an `EventEnvelope` (eventId, eventType, schemaVersion,
aggregateType, aggregateId, occurredAt, data) and persists it as `JSONB`.

**Relay side** — `OutboxRelay`, `@Scheduled(fixedDelay = 500ms)`:

```java
List<OutboxEventJpaEntity> batch = repository.lockNextPendingBatch(properties.batchSize());
for (OutboxEventJpaEntity event : batch) {
    try {
        kafkaTemplate.send(toRecord(event)).get(timeout, MILLISECONDS);
        event.markPublished(clock.instant());
    } catch (Exception e) {
        event.markAttemptFailed(e.toString(), properties.maxAttempts());
        break;                                   // stop the batch
    }
}
```

The query:

```sql
SELECT * FROM outbox_event WHERE status = 'PENDING'
ORDER BY seq LIMIT :batchSize
FOR UPDATE SKIP LOCKED
```

**Cleanup** — a nightly cron purges `PUBLISHED` rows older than the retention window (7 days), so the
table doesn't grow without bound.

### Q5.3 — "Why `FOR UPDATE SKIP LOCKED`?"

It lets **multiple application instances relay concurrently without publishing duplicates**. Each
instance locks a disjoint set of rows; `SKIP LOCKED` means instance B steps over rows instance A has
locked rather than blocking on them.

Without it you'd need either a single designated relay (a SPOF and a scaling bottleneck) or a
distributed lock (another moving part). This gets horizontal scalability from one SQL clause.

`ORDER BY seq` with a `BIGINT GENERATED ALWAYS AS IDENTITY` guarantees insertion order. The partial
index `ix_outbox_pending ON outbox_event (seq) WHERE status = 'PENDING'` keeps the scan cheap — it only
indexes unpublished rows, so it stays small even as the table accumulates millions of published ones.

### Q5.4 — "Why do you `break` out of the loop on failure instead of continuing?" ⭐

**To preserve per-aggregate ordering.** This is the detail most outbox implementations get wrong.

Suppose claim X produces `ClaimApproved` (seq 10) then `ClaimSettled` (seq 11). If seq 10 fails and we
continue to seq 11, a consumer sees a settlement for a claim it believes was never approved. For a
financial system that's corruption.

Stopping the batch guarantees we never publish an event ahead of an earlier failed one. The failed
event retries on the next poll (500 ms later), and attempts are capped by `maxAttempts`.

**The trade-off, stated honestly:** this is head-of-line blocking. One poison event stalls *all*
events globally, not just that aggregate's. Acceptable for an MVP where correctness ≫ throughput.

**How I'd improve it:** partition the relay by `aggregate_id` hash so a stuck claim only blocks its own
stream, and move events exceeding `maxAttempts` to a `FAILED` state (already modelled in `OutboxStatus`)
with an alert, so they stop blocking the queue.

`OutboxRelayTest.stopsBatchOnFailureToPreserveOrdering` is an explicit regression test for this.

### Q5.5 — "Is this exactly-once delivery?"

No — it's **at-least-once, with idempotent consumers**, which is the achievable goal.

The gap: `kafkaTemplate.send()` can succeed, and then the transaction marking the row `PUBLISHED` can
fail to commit. The row stays `PENDING` and gets republished. True exactly-once across Postgres and
Kafka would need XA/2PC, which is slow, operationally painful, and poorly supported.

So we make duplicates harmless instead:
- Every event carries a stable `eventId` (UUID) in both the envelope and the Kafka header.
- Consumers record `(event_id, consumer)` in `processed_event` (PK on both columns) and skip anything
  already seen — `NotificationService.handle()` returns early on a duplicate.
- The producer runs with `enable.idempotence: true` and `acks: all`, eliminating duplicates from
  broker-level retries.

That's the standard pattern: at-least-once delivery + idempotent consumption = effectively-once
processing.

### Q5.6 — "Why poll the table? Isn't CDC/Debezium better?"

Debezium tailing the Postgres WAL is lower latency and puts no query load on the DB — it's the right
answer at scale and the natural next step.

I chose polling for the MVP because it's **one less piece of infrastructure** (no Kafka Connect
cluster to run, secure, monitor and upgrade), it's trivially debuggable (`SELECT * FROM outbox_event`
shows you the state of the world), and 500 ms latency is irrelevant for claims processing where the
downstream action is an email.

The abstraction is preserved: swapping in CDC means deleting `OutboxRelay` and configuring a
connector. No business code changes.

### Q5.7 — "Why is Kafka keyed by `aggregateId`?"

```java
new ProducerRecord<>(event.getTopic(), event.getAggregateId().toString(), event.getPayload());
```

Kafka only guarantees ordering **within a partition**, and partition is chosen by key hash. Keying by
claim id puts every event for one claim on the same partition, in order. Different claims spread
across partitions for parallelism.

This is what makes consumer-side ordering meaningful: a consumer processing claim X sees created →
assigned → approved → settled in order, while still scaling out across claims.

---

## 6. Kafka & messaging

### Q6.1 — "Explain your topic design."

Five topics, one per event type, in `Topics.java`:

```
claims.claim-created.v1
claims.claim-assigned.v1
claims.status-changed.v1
claims.reserve-changed.v1
claims.decision-made.v1
```

Convention: `<domain>.<event>.<version>`.

**Why topic-per-event-type rather than one `claims.events` topic?** Consumers subscribe only to what
they need — the finance system takes `reserve-changed` without filtering out noise; notifications take
four of the five. It also allows per-topic retention and partition tuning.

**Why `.v1` in the name?** Explicit schema versioning. A breaking payload change publishes to `.v2`
while `.v1` keeps flowing, so producers and consumers migrate independently instead of in a lockstep
deploy. The envelope also carries a numeric `schemaVersion` for non-breaking additive changes.

Topics are created declaratively by `KafkaConfig.claimTopics(...)` with 3 partitions, and
`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"` in Compose — so a typo in a topic name fails loudly instead
of silently creating a ghost topic nothing consumes.

### Q6.2 — "Explain your producer configuration."

```yaml
acks: all                                    # all in-sync replicas must ack
enable.idempotence: true                     # broker dedupes producer retries
max.in.flight.requests.per.connection: 5     # safe up to 5 when idempotence is on
delivery.timeout.ms: 30000
linger.ms: 5                                 # small batching window
```

`acks: all` + idempotence is the durability-first configuration: no acknowledged write is lost unless
every replica dies, and retries can't produce duplicates or reorder.

The subtle one is `max.in.flight = 5`. Without idempotence, anything above 1 can **reorder** messages
on retry. With idempotence enabled, the broker tracks sequence numbers per producer and Kafka
guarantees ordering up to 5 in-flight — so we get pipelining throughput *and* ordering.

`linger.ms: 5` trades 5 ms of latency for batching efficiency.

### Q6.3 — "How do you handle a poison message?"

`KafkaConfig.kafkaErrorHandler(...)`:

```java
DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
backOff.setMaxElapsedTime(10_000L);
DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
handler.addNotRetryableExceptions(IllegalArgumentException.class);
```

Retry with exponential backoff (500 ms, doubling, capped at 10 s total), then publish to
`<topic>-dlt`. The DLT topics are declared up front alongside the main topics.

The important classification: **`IllegalArgumentException` is non-retryable**. A malformed JSON payload
will *never* succeed — retrying it 5 times just wastes 10 seconds before the inevitable. So
`ClaimEventsKafkaConsumer.parse()` deliberately throws `IllegalArgumentException` for unparseable
records, routing them straight to the DLT.

Transient failures (DB blip, downstream timeout) throw other exception types and do get retried.

Without this, one bad message blocks its partition forever — the offset never advances and every claim
on that partition stops processing.

### Q6.4 — "Why manual offset commits?"

```yaml
enable-auto-commit: false
listener:
  ack-mode: record
```

Auto-commit commits on a timer, regardless of whether processing succeeded. If the app crashes between
the commit and the work completing, the message is lost — the offset says "done" for work that never
happened.

With `ack-mode: record`, Spring commits only **after** the listener returns normally. Crash mid-processing
and the message is redelivered. Combined with the `processed_event` dedupe table, redelivery is safe.

Also `isolation.level: read_committed` on the consumer, so we never read records from aborted producer
transactions.

### Q6.5 — "Your consumer marks the event processed *before* sending the notification. Isn't that a bug?"

Good catch — it's safe, but only because of the transaction:

```java
@Transactional
public void handle(ClaimEventNotice notice) {
    if (!processedEvents.markProcessed(notice.eventId(), CONSUMER_NAME)) return;
    compose(notice).ifPresent(sender::send);
}
```

`markProcessed` inserts into `processed_event` and returns false on PK conflict. If `send` then throws,
the **whole transaction rolls back** — including the `processed_event` insert — so the event is not
marked done and redelivery will retry it.

Doing it in this order (insert first, then act) rather than the reverse is what makes the dedupe check
race-safe: two concurrent deliveries of the same event contend on the primary key, and exactly one
wins. A check-then-act ordering would let both pass the check.

**The real limitation:** this guarantees atomicity between the dedupe record and the *database*, not
the email provider. Once the current `LoggingNotificationSender` is replaced with a real SMTP/SendGrid
call, a failure after a successful send would roll back the marker and re-send the email. The fix is to
persist an outbound notification row in the transaction and have a separate sender drain it — i.e. the
outbox pattern again, on the egress side.

### Q6.6 — "Why `auto-offset-reset: earliest`?"

For a new consumer group, `latest` would silently skip every event that already exists. For claims —
where an event means "tell the claimant their claim was approved" — skipping is worse than
re-processing, and re-processing is safe thanks to dedupe. `earliest` also makes adding a new consumer
(e.g. analytics) a replay-from-the-beginning operation for free.

---

## 7. Database, JPA & Flyway

### Q7.1 — "Walk me through your schema."

Six tables across three migrations:

| Table | Purpose |
|---|---|
| `claim` | Aggregate root state, money fields, status, `version` for optimistic locking |
| `incident` | Loss details, 1:1 with claim (`uq_incident_claim`) |
| `assignment` | Officer assignment history, one active row per claim |
| `assessment` | Append-only assessor findings |
| `claim_history` | Append-only audit trail of every domain event |
| `outbox_event` | Transactional outbox |
| `processed_event` | Consumer idempotency ledger (V3) |

Design decisions worth calling out:

- **`NUMERIC(19,2)` for all money.** Never `float`/`double` — binary floating point can't represent
  0.10 exactly, and rounding errors in claim payouts are a regulatory problem. The domain mirrors this
  with `BigDecimal` and explicit `setScale(2, HALF_UP)`.
- **`TIMESTAMPTZ` everywhere**, with Hibernate pinned to UTC (`hibernate.jdbc.time_zone: UTC`).
  Insurance is cross-border; ambiguous local times cause disputes.
- **CHECK constraints as invariant backstops** — `ck_claim_settled_le_approved`,
  `ck_claim_assigned_before_review`, status enumeration.
- **Partial indexes** — `ux_assignment_active_claim ON assignment (claim_id) WHERE active` enforces
  "at most one active assignment per claim" *in the database*, atomically. No read-then-write race can
  produce two active assignments.

### Q7.2 — "Why `ddl-auto: validate` and Flyway rather than `update`?"

`ddl-auto: update` is dangerous in production: it infers DDL from entity annotations, never drops or
renames anything, can't express constraints/indexes/backfills, and gives you no review artefact or
rollback story. Two instances starting simultaneously can race on DDL.

Flyway makes schema changes **explicit, versioned, ordered and code-reviewed**. `validate` then acts as
a safety net — the app refuses to start if entities and schema have drifted, catching the mismatch at
boot rather than at 3 a.m. on a query.

### Q7.3 — "How would you handle a breaking schema change with zero downtime?"

Expand/contract (parallel change), across releases:

1. **Expand** — add the new nullable column; deploy code that writes both old and new, reads old.
2. **Backfill** — migrate existing rows in batches.
3. **Switch** — deploy code that reads the new column.
4. **Contract** — once no running version references it, a later migration drops the old column.

Each step is independently deployable and rollback-safe. The rule: never deploy a migration that an
in-flight previous version of the app can't tolerate, because during a rolling deploy both versions run
simultaneously.

### Q7.4 — "Why did you build the exposure report with JdbcTemplate instead of JPA?"

It's a **read model / reporting query**, not aggregate navigation. `JdbcExposureReadModelAdapter` pushes
aggregation into SQL:

```sql
SELECT currency,
       COUNT(*) FILTER (WHERE status IN (…))                         AS open_claims,
       COALESCE(SUM(reserve_amount) FILTER (WHERE status IN (…)), 0) AS total_reserve,
       …
FROM claim GROUP BY currency
```

Loading every claim into the JVM to sum in Java would be O(n) memory and would collapse under a real
book of business. Postgres aggregates in-engine, using indexes, and returns a handful of rows — it
scales with data volume, not heap size.

This is a light **CQRS** split: commands go through the rich aggregate and JPA; queries go through
purpose-built read adapters. Different jobs, different tools. The `ExposureReadModelPort` interface
means the report could later be served from a materialised view or a separate read replica with no
change to `ExposureService`.

### Q7.5 — "How are claim numbers generated?"

A PostgreSQL sequence (`claim_number_seq`) via `SequenceClaimNumberGenerator`. Sequences are atomic and
concurrency-safe without locking — two simultaneous submissions can't collide. `uq_claim_number`
enforces uniqueness as a backstop.

Why not a UUID for the business identifier? Claim numbers are read aloud on the phone, written on
forms and quoted in letters. `CLM-000123` is human-usable; a UUID is not. We use both: UUID as the
technical primary key (no information leakage, generatable client-side, no hotspotting on inserts) and
a sequential claim number as the human reference.

---

## 8. Transactions & concurrency

### Q8.1 — "Where are your transaction boundaries and why?"

At the **application service** methods — `ClaimIntakeService.submit`, `ClaimLifecycleService.approve`,
`AssignmentService.assign`, `AssessmentService.record`. That's the use-case boundary: one business
operation, one transaction.

Not in controllers (they'd leak persistence concerns into HTTP and keep transactions open during
serialisation) and not in the domain (it must stay framework-free).

`ClaimAggregateStore.save` and `OutboxWriter.append` use `Propagation.MANDATORY` — they refuse to run
without an enclosing transaction. This is a compile-time-ish guarantee that the outbox write is always
atomic with the state change, enforced by the framework rather than by developer discipline.

Queries use `@Transactional(readOnly = true)`, which lets Hibernate skip dirty checking and allows
routing to a read replica later.

### Q8.2 — "Two officers approve the same claim simultaneously. What happens?"

Optimistic locking. `claim.version` is a `@Version` column; the `UPDATE` carries
`WHERE id = ? AND version = ?`. The second commit matches zero rows, Hibernate throws
`OptimisticLockingFailureException`, and `GlobalExceptionHandler` maps it to **409 Conflict** with code
`CONCURRENT_MODIFICATION` and a "reload and retry" message.

Why optimistic rather than pessimistic (`SELECT FOR UPDATE`)? Concurrent edits to the *same* claim are
rare — claims are owned by one officer at a time. Pessimistic locking would pay a locking cost on every
read to defend against a rare event, and risks lock contention and deadlocks. Optimistic locking is
free in the common case and only costs a retry in the rare one.

Note the database also backs this up: `ux_assignment_active_claim` means even a race on assignment
can't create two active assignments.

### Q8.3 — "Isn't it a problem that `OutboxRelay` holds row locks while calling Kafka?" ⚠️

Yes — this is a legitimate weakness and worth volunteering.

The `kafkaTemplate.send(...).get(timeout)` happens inside the transaction that holds `FOR UPDATE` locks
on the batch. A slow broker holds database locks for up to `send-timeout` (10 s) × batch size. It
occupies a Hikari connection from a pool of 20 the whole time.

It's bounded and safe (`SKIP LOCKED` means other instances aren't blocked, and the timeout caps the
hold), but under broker degradation it could exhaust the connection pool.

**Better design:** claim the batch in a short transaction (mark rows `IN_FLIGHT`, commit, release the
connection), publish outside any transaction, then mark published in a second short transaction. That
reduces lock hold time to milliseconds. The cost is a slightly larger duplicate window on crash — which
consumer idempotency already handles.

### Q8.4 — "Why `open-in-view: false`?"

`spring.jpa.open-in-view` defaults to `true`, which keeps the Hibernate session open through view
rendering. That hides N+1 queries (lazy loads silently fire during JSON serialisation) and holds a
database connection for the entire request including network write time.

Turning it off forces all loading to happen inside the service transaction. Lazy-loading mistakes fail
fast with `LazyInitializationException` during development instead of quietly degrading production
throughput.

### Q8.5 — "You enabled virtual threads. Why, and what's the risk?"

`spring.threads.virtual.enabled: true` (Java 21). This is an I/O-bound service — most request time is
spent waiting on Postgres and Kafka. Virtual threads let blocking code scale to high concurrency
without the complexity of reactive programming, and keep stack traces and debuggers usable.

The known risk is **pinning**: a virtual thread blocking inside a `synchronized` block pins its carrier
thread and can starve the pool. JDK 21 has this issue with some older drivers. Our stack (HikariCP,
pgjdbc, Spring Kafka) is largely `ReentrantLock`-based, and JDK 24+ removed the pinning problem
entirely. I'd monitor carrier-thread starvation with `jdk.VirtualThreadPinned` JFR events before
trusting it under production load.

Note also that the connection pool (20) remains the real concurrency ceiling for DB work — virtual
threads don't change that, they just stop thread count being a *second* bottleneck.

---

## 9. REST API design & error handling

### Q9.1 — "Why POST for `/approve` rather than PATCH on the claim?"

These are **state transitions with business meaning**, not field edits. `POST /claims/{id}/approve` is
explicit about intent, maps to a specific use case, and takes its own payload (approved amount, notes).

`PATCH /claims/{id}` with `{"status": "APPROVED"}` would imply the client controls the state machine,
invite arbitrary status writes, and make authorisation harder (approving requires different permissions
from editing a phone number). Separate endpoints give clean per-action authorisation and a self-documenting
API.

This is the "command resource" pattern — pragmatic REST rather than dogmatic REST.

### Q9.2 — "Explain your error handling."

`GlobalExceptionHandler` (`@RestControllerAdvice`) returns **RFC 7807 Problem Details** for everything:

| Exception | Status | Code |
|---|---|---|
| `ResourceNotFoundException` | 404 | `CLAIM_NOT_FOUND` etc. |
| `InvalidStateTransitionException` | **409** | + `currentStatus`/`targetStatus` properties |
| `BusinessRuleViolationException` | 422 | e.g. `APPROVED_AMOUNT_EXCEEDS_CLAIM` |
| `OptimisticLockingFailureException` | 409 | `CONCURRENT_MODIFICATION` |
| `DataIntegrityViolationException` | 409 | `DATA_INTEGRITY_VIOLATION` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` + per-field errors |
| `Exception` | 500 | `INTERNAL_ERROR` — generic message |

Three deliberate choices:

1. **Every response carries a stable machine-readable `code`.** Clients branch on `code`, never on the
   human-readable message, so we can reword messages without breaking integrations.
2. **409 vs 422 are distinguished.** 409 = wrong state, retry later might work. 422 = semantically
   invalid request, retrying won't help. That distinction tells the client whether to retry.
3. **The 500 handler logs the exception but returns a generic message.** Stack traces and SQL in an API
   response are an information-disclosure vulnerability.

### Q9.3 — "Why 422 for business rule violations instead of 400?"

400 means "I can't parse/bind this request". 422 Unprocessable Content means "I understood it perfectly,
but it violates a semantic rule".

`{"approvedAmount": 50000}` on a claim for 10,000 is well-formed JSON, passes all Bean Validation, and
binds fine. What fails is the insurance rule that you can't approve more than was claimed. 422 captures
that precisely, and the `code` (`APPROVED_AMOUNT_EXCEEDS_CLAIM`) tells the client exactly which rule.

### Q9.4 — "How is OpenAPI documentation generated?"

springdoc-openapi introspects controllers, DTOs and Bean Validation annotations at runtime —
`/v3/api-docs` and Swagger UI at `/swagger-ui.html`. `OpenApiConfig` supplies the metadata.

Generated-from-code rather than hand-written means the docs **cannot drift** from the implementation.
A hand-maintained spec is wrong within two sprints.

The alternative worth mentioning is contract-first (write the OpenAPI spec, generate interfaces). That's
better when multiple teams negotiate a contract before implementation; code-first is faster for a
single-team MVP.

### Q9.5 — "How does pagination work, and why not return a `Page` directly?"

`GET /api/v1/claims` accepts filter criteria plus paging and returns a custom `PageResponse` wrapping a
domain `PageResult`.

Spring Data's `Page` is deliberately *not* exposed: its JSON shape is unstable across Spring versions,
it leaks `Pageable`/`Sort` internals into the public contract, and it couples the API to the persistence
library. A hand-rolled response DTO is a contract we control.

---

## 10. Testing strategy

### Q10.1 — "Describe your testing approach."

A test pyramid, split by what each layer is *for*:

| Level | Count | Tooling | What it proves |
|---|---|---|---|
| Domain unit | ~90 | JUnit 5 only | Business rules & state machine |
| Service unit | ~25 | JUnit 5 + Mockito | Orchestration, port interaction |
| Web slice | 6 | `@WebMvcTest` + MockMvc | Status codes, JSON, validation |
| Architecture | 5 | ArchUnit | Structural rules hold |
| Integration | 3 classes | Testcontainers | Real Postgres + Kafka, end to end |

**134 unit tests, all passing.** Unit tests run in ~10 s with no Docker; integration tests run under
`mvn verify` via failsafe.

The split is enforced in the build: surefire excludes `**/*IT.java`, failsafe runs them. So the fast
feedback loop stays fast and nobody is tempted to skip tests locally.

`ClaimStatusTest` deserves a mention — it exhaustively asserts **all 56 status × status combinations**,
so no transition can be added or removed without a deliberate test change.

### Q10.2 — "Why Testcontainers instead of H2?"

H2 lies. It doesn't support `FOR UPDATE SKIP LOCKED`, `JSONB`, partial indexes
(`CREATE INDEX … WHERE active`), `COUNT(*) FILTER (WHERE …)`, or `GENERATED ALWAYS AS IDENTITY` — every
one of which this schema depends on. Tests would pass against H2 and the application would fail against
production Postgres.

Testcontainers runs the **actual `postgres:16-alpine` and `apache/kafka:3.8.0` images** that production
uses, so the migrations, the SQL and the Kafka semantics under test are the real ones.

### Q10.3 — "How do you keep Testcontainers fast?"

The **singleton container pattern** in `AbstractIntegrationTest`: `static` container fields started once
via `Startables.deepStart(POSTGRES, KAFKA).join()` and shared by every IT class. Combined with Spring's
context cache (identical `@DynamicPropertySource` values ⇒ same context), the containers boot once per
build rather than once per class.

Also `@Testcontainers(disabledWithoutDocker = true)` — on a machine without Docker the integration tests
**skip rather than fail**, so a developer can still run `mvn test` productively.

### Q10.4 — "How do you test time-dependent logic?"

`java.time.Clock` is injected everywhere (`CoreConfig` provides `Clock.systemUTC()`); no service ever
calls `Instant.now()` directly. Tests inject `Clock.fixed(...)`, making assertions on timestamps
deterministic and letting us simulate retention windows and date boundaries without sleeping.

### Q10.5 — "What does `OutboxKafkaIT` actually verify?"

The full reliability chain against real infrastructure: submit a claim over HTTP → assert the row lands
in `outbox_event` → wait (Awaitility) for the relay to publish → consume from the real Kafka topic →
assert the envelope and headers. That's the one test that proves the outbox guarantee end to end;
mocks can't.

Awaitility is used rather than `Thread.sleep` so the test polls until the condition holds (or times
out) — fast when things work, and not flaky when CI is slow.

---

## 11. Build, Docker & configuration

### Q11.1 — "Explain your Dockerfile."

Multi-stage:

- **Build stage** (`maven:3.9-eclipse-temurin-21`) resolves dependencies with a BuildKit cache mount
  (`--mount=type=cache,target=/root/.m2`), so dependency downloads are reused across builds. `pom.xml`
  is copied before `src` so a source-only change doesn't invalidate the dependency layer.
- It then runs `java -Djarmode=tools -jar target/claimflow.jar extract --layers`, splitting the fat jar
  into dependencies / loader / snapshot-deps / application. Each becomes its own Docker layer, so a code
  change only rebuilds and re-pushes the small application layer, not the ~60 MB of dependencies.
- **Runtime stage** (`eclipse-temurin:21-jre-alpine`) — JRE not JDK, Alpine base, and a **non-root user**
  (`claimflow`). Smaller attack surface; a container escape doesn't land on root.
- `-XX:MaxRAMPercentage=75` so the JVM sizes its heap from the *container* limit rather than the host's
  memory, and `-XX:+ExitOnOutOfMemoryError` so an OOM kills the container and lets the orchestrator
  restart it cleanly rather than limping along.
- A `HEALTHCHECK` hitting `/actuator/health/readiness`.

> ⚠️ **Note for this repo:** the pom originally overrode `<directory>` to `target-java25-validation`
> while the Dockerfile copied from `target/` — the Docker build was broken. That's fixed; artifacts now
> build to `target/` as the Dockerfile expects.

### Q11.2 — "Explain the Docker Compose setup."

Postgres 16, Kafka 3.8 in **KRaft mode** (no ZooKeeper — simpler, fewer moving parts), the app, and an
optional Kafka UI behind `profiles: ["tools"]` so it doesn't start by default.

The important part is **health-gated startup**: the app uses `depends_on: { condition: service_healthy }`
for both Postgres and Kafka. Plain `depends_on` only waits for the container to *start*, not to be
*ready* — the app would race Flyway against an uninitialised database. The healthchecks
(`pg_isready`, `kafka-broker-api-versions.sh`) make the dependency real.

Kafka uses separate `INTERNAL` (`kafka:29092`) and `EXTERNAL` (`localhost:9092`) listeners, so
containers and host-run processes can both reach the broker with correct advertised addresses — the
single most common Kafka-in-Docker failure.

### Q11.3 — "How is configuration managed across environments?"

Everything environment-specific is an env var with a sane local default:
`${DB_URL:jdbc:postgresql://localhost:5432/claimflow}`, `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`,
`${KAFKA_REPLICATION_FACTOR:1}`.

That's 12-factor: one image, configuration injected at runtime. The same artifact promotes from dev to
prod. Feature toggles (`OUTBOX_RELAY_ENABLED`, `NOTIFICATION_CONSUMER_ENABLED`) allow running the relay
or consumer in dedicated instances later.

For production, secrets would move out of env vars into a secret manager (Vault / AWS Secrets Manager)
and `KAFKA_REPLICATION_FACTOR` would be ≥3.

### Q11.4 — "What's exposed for production monitoring?"

Actuator with `health,info,metrics,prometheus`, liveness/readiness probes enabled, and
`show-details: when-authorized` so health internals aren't public. Graceful shutdown is on, so in-flight
requests drain before the process exits during a rolling deploy.

Metrics I'd alert on: outbox `PENDING` depth and oldest-pending age (the single best indicator the event
pipeline is broken), consumer lag, DLT message count, and claim transition rates.
`OutboxEventJpaRepository.countByStatus` already exposes the data for a custom gauge.

---

## 12. Insurance domain knowledge

### Q12.1 — "What is a reserve and why does it matter?"

The estimated ultimate cost of a claim, held as a liability from the moment of notification. It drives
solvency capital requirements (Solvency II / IFRS 17), reinsurance recovery calculations and financial
reporting. Under-reserving overstates profit and can breach regulatory capital; over-reserving ties up
capital unnecessarily. That's why every reserve change in this system emits an event and is written to
an immutable audit trail.

### Q12.2 — "Why is the audit trail append-only?"

Regulatory requirement. Insurers must demonstrate *who* decided *what*, *when*, and on what basis —
particularly for rejections, which claimants can dispute or escalate to an ombudsman.

`claim_history` is insert-only, keyed by `event_id` with a unique constraint (idempotent writes), carries
a monotonic `seq`, and stores the full event payload as `JSONB`. Because history is written from the same
domain events in the same transaction as the state change, **it cannot drift from reality** — there is no
code path that changes a claim without recording it.

### Q12.3 — "Why validate the policy at intake?"

No valid policy ⇒ no contract ⇒ no claim. `ClaimIntakeService` calls `PolicyQueryUseCase.verifyCoverage`
*before* creating the claim, checking the policy exists, is `ACTIVE`, that the incident date falls within
the coverage period, that the claimed amount is within the coverage limit, and that currencies match.

It also rejects future-dated incidents (`INCIDENT_DATE_IN_FUTURE`) — you can't claim for a loss that
hasn't happened. These are the cheapest fraud and data-quality controls available, applied at the front
door before anything is persisted.

In this MVP the policy catalog is config-driven (`claimflow.policy.catalog` — four seeded policies)
standing in for a real policy administration system. The `PolicyCatalogPort` interface means replacing it
with a REST client to the PAS is a single adapter.

### Q12.4 — "Why can only the assigned officer record an assessment?"

`AssessmentService` enforces it (`ASSESSOR_NOT_ASSIGNED`, 422). It's an accountability and
segregation-of-duties control: assessments must be traceable to the officer who owns the claim.

Note this is in the *service*, not the aggregate — it's a cross-cutting authorisation policy rather than
a claim invariant. In production it would be reinforced by actual authentication, with the officer
identity taken from the security context rather than the request body.

---

## 13. Hard / curveball questions

### Q13.1 — "Your relay crashes after Kafka accepts the message but before the DB commit. What happens?"

The row stays `PENDING` and is republished on the next poll. The consumer sees a duplicate `eventId`,
`processed_event` rejects it on the primary key, and `NotificationService` returns early.

That's the whole point of at-least-once + idempotent consumers: we accept duplicate *delivery* and
guarantee single *effect*. Trying to eliminate duplicate delivery would require 2PC between Postgres
and Kafka, which costs far more than it's worth.

### Q13.2 — "Scale this to 10 million claims. What breaks first?"

In order:

1. **`claim` table size.** Mitigation: partition by `created_at` (claims are overwhelmingly accessed by
   recency), and archive settled/rejected claims older than the retention period to cold storage.
2. **The exposure query.** A full `GROUP BY` over 10 M rows per request won't hold. Mitigation: a
   materialised view refreshed on a schedule, or maintain exposure incrementally by consuming
   `claims.reserve-changed.v1` into a dedicated read model. The `ExposureReadModelPort` abstraction means
   this swap doesn't touch `ExposureService`.
3. **Outbox table churn.** Already addressed by the nightly purge and the partial index on `PENDING`,
   but at high write rates I'd partition it or move to CDC.
4. **Relay throughput.** Single-threaded per instance. `SKIP LOCKED` already allows scaling out by
   adding instances; beyond that, partition by `aggregate_id`.
5. **The monolith itself.** At that point, extract notification and exposure first — they're read-only
   or event-driven, so they have the cleanest seams.

### Q13.3 — "The business wants approvals above €100,000 to need a second approver. How would you implement it?"

The design already localises this. I'd add to the `Claim` aggregate:

- An approval threshold and a `PENDING_SECOND_APPROVAL` status (or, better, a `pendingApproval` value
  object holding first approver + amount, so the status graph doesn't explode).
- `approve()` branches: below threshold → `APPROVED` as today; above → records the first approval and
  requires a `secondApprove()` by a *different* actor before transitioning.
- A new `ApprovalRequired` domain event so the work-management module can queue it for a senior officer.

What's notable is what *doesn't* change: the controller, the repository, the outbox, the relay. The rule
lives in the aggregate, the transition table in `ClaimStatus`, and the test in `ClaimStatusTest`. That's
the dividend of keeping business logic out of the infrastructure.

I'd also enforce "second approver ≠ first approver" in the domain (four-eyes principle) and back it with
a database constraint.

### Q13.4 — "How would you add authentication and authorisation?"

Currently there is none — the actor is passed in the request, which is fine for an MVP but obviously not
production-ready. I'd add:

- Spring Security with OAuth2 resource server / JWT, issued by the corporate IdP.
- Roles: `CLAIMANT`, `CLAIMS_OFFICER`, `MANAGER`.
- `@PreAuthorize("hasRole('CLAIMS_OFFICER')")` on decision endpoints; `MANAGER` for `/exposure`.
- **Replace every `actor`/`decidedBy` field sourced from the request body with the authenticated
  principal.** Today a client can claim to be anyone, which undermines the audit trail — the thing the
  audit trail exists to prevent.
- Claimants restricted to their own claims via a policy check on `claimant` identity.

### Q13.5 — "Why not event sourcing, given you already have domain events?"

I considered it — the events exist and the audit trail is already event-derived. I chose state storage
plus an event log because:

- Queries stay simple. `GET /api/v1/claims?status=…` is a SQL `WHERE`; with event sourcing it needs a
  projection to be built and maintained.
- The team's operational familiarity. Debugging "what is this claim's state" is a `SELECT`, not a replay.
- Schema evolution of a 10-year-old event store is genuinely hard, and claims have long tails — some
  stay open for years.

Event sourcing would be compelling if we needed full temporal queries ("what did we believe the reserve
was on this date?"), which reinsurance and reserving actuaries sometimes do want. The current design is a
reasonable middle ground: authoritative state, plus an immutable append-only history that answers most
of those questions.

### Q13.6 — "What's the single weakest part of this codebase?"

Pick one and be specific — vagueness reads as evasion. Good candidates:

1. **Cross-module coupling to `ClaimAggregateStore`** (see Q2.5) — a concrete class, not a port.
2. **The relay holds DB locks during Kafka I/O** (see Q8.3) — connection pool risk under broker
   degradation.
3. **Head-of-line blocking in the relay** (see Q5.4) — one poison event stalls all events.
4. **No authentication** — the `actor` is self-asserted, which weakens the audit trail.
5. **Nothing consumes the DLT topics** — messages land there and no one is alerted.

The strongest version of this answer names the issue, explains the blast radius, and gives the fix.

---

## 14. Known gaps & "what next"

Volunteering these shows engineering maturity. If asked "what would you do with another two weeks":

| Gap | Fix | Priority |
|---|---|---|
| No authN/authZ | Spring Security + JWT; derive `actor` from principal | **P0** |
| DLT has no consumer or alerting | DLT monitor + ops dashboard | **P0** |
| Relay holds locks during Kafka send | Claim/publish/confirm in separate short transactions | P1 |
| No idempotency key on `POST /claims` | `Idempotency-Key` header + dedupe table — prevents duplicate claims from double-clicks/retries | P1 |
| Head-of-line blocking in relay | Partition relay by `aggregate_id`; park `FAILED` events | P1 |
| Policy catalog is config, not a real system | REST adapter behind `PolicyCatalogPort` + circuit breaker | P1 |
| Notifications only logged | Real SMTP/SendGrid adapter, with an egress outbox | P2 |
| Exposure recomputed per request | Materialised view or event-driven read model | P2 |
| No distributed tracing | Micrometer Tracing + OpenTelemetry; propagate trace id through Kafka headers | P2 |
| No rate limiting | Gateway-level throttling on public intake | P2 |
| `settle()` emits no `DecisionMade` | Intentional (settlement is execution, not decision) — but confirm with the business | P3 |

---

## 15. Rapid-fire one-liners

**Why hexagonal?** Business logic independent of frameworks; swap infrastructure without touching rules.

**Why modular monolith?** MVP scope, ACID transactions, extraction-ready boundaries.

**Why outbox?** Atomic state change + event publication without 2PC.

**Why `SKIP LOCKED`?** Multiple relay instances, zero duplicates, no distributed lock.

**Why break the batch on failure?** Preserve per-aggregate event ordering.

**Why at-least-once?** Exactly-once needs 2PC; idempotent consumers are cheaper and simpler.

**Why key by claim id?** Kafka orders within a partition; same claim ⇒ same partition.

**Why `.v1` topics?** Independent producer/consumer schema migration.

**Why 409?** Valid request, wrong resource state (RFC 9110).

**Why 422?** Understood request, violates a business rule.

**Why `BigDecimal`/`NUMERIC`?** Floating point can't represent money exactly.

**Why `TIMESTAMPTZ` + UTC?** Cross-border insurance; no ambiguous local times.

**Why Flyway + `validate`?** Versioned, reviewable schema; app refuses to start on drift.

**Why Testcontainers?** H2 lacks `SKIP LOCKED`, `JSONB`, partial indexes, `FILTER`.

**Why ArchUnit?** Architecture enforced by the build, not by README discipline.

**Why optimistic locking?** Same-claim contention is rare; don't pay locking cost for it.

**Why inject `Clock`?** Deterministic tests without sleeping.

**Why `open-in-view: false`?** Surfaces N+1 immediately; frees connections sooner.

**Why `MANDATORY` propagation?** Makes it impossible to write to the outbox outside the business transaction.

**Why separate domain and JPA models?** Final fields, private constructors, invariant-enforcing factories.

---

## Appendix — Numbers worth memorising

- **8** business modules
- **7** lifecycle states, **5** topics, **6+1** tables
- **134** unit tests, all passing
- **5** ArchUnit rules
- **56** status-transition combinations exhaustively tested
- **3** Flyway migrations
- **3** Kafka partitions per topic, **500 ms** relay poll, **7 d** outbox retention
- Java **21**, Spring Boot **3.5.16**, PostgreSQL **16**, Kafka **3.8**
