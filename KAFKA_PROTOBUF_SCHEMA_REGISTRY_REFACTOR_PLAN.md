# Kafka Protobuf Schema Registry Refactor Plan

**Service:** `ms-gym-member`  
**Prepared:** 2026-08-02  
**Status:** Planning only; no transport implementation has been applied  
**Primary dependencies:** `common-java`, `gym-proto`, Confluent Schema Registry 7.7.1

## 1. Objective

Replace the legacy JSON `EventEnvelope<T>` Kafka value with the shared native transport supplied by `common-java`:

- Kafka key remains the domain ordering key.
- Kafka value is the concrete generated Protobuf message.
- `KafkaProtobufSerializer` adds Confluent Schema Registry framing.
- Schema subjects use `TopicNameStrategy`, producing `<topic>-value`.
- Compatibility is `BACKWARD`.
- Event metadata moves to canonical Kafka headers.
- The transactional outbox remains the database-to-Kafka consistency mechanism.
- Consumer processing remains at-least-once and idempotent through the immutable `event-id` header.

This plan also records relevant `common-java` improvements and identifies two blockers that should be fixed in `common-java` before this service is migrated.

## 2. Scope

### In scope

- Upgrade `common-java` to the release containing native Confluent Protobuf transport.
- Remove all direct `EventEnvelope` use from `ms-gym-member` Kafka producers, consumers, tests, and local tooling.
- Consume these generated messages directly:
  - `UserRegisteredEvent`
  - `UserSuspendedEvent`
  - `PaymentCompletedEvent`
- Publish these generated messages directly through the outbox relay:
  - `MembershipActivatedEvent`
  - `MembershipPausedEvent`
  - `MembershipResumedEvent`
  - `MembershipExpiringSoonEvent`
  - `MembershipExpiredEvent`
- Adopt canonical headers and immutable event IDs.
- Add Schema Registry to local and integration-test infrastructure.
- Add real wire-format, registry, retry, and DLQ tests.
- Define a coordinated rollout that never mixes JSON envelope records and Protobuf-framed records without an explicit compatibility bridge.
- Apply low-risk, useful `common-java` improvements where they fit this service.

### Out of scope

- Replacing the transactional outbox with Kafka transactions.
- Changing business state-machine rules.
- Changing Protobuf field numbers or reusing removed field numbers.
- Migrating offset pagination to cursor pagination without a separate API contract change.
- Refactoring all shared infrastructure into this service.
- Removing legacy APIs from `common-java`; that cleanup belongs in the shared library after every service migrates.

## 3. Source-of-truth contract

The target wire contract is defined in `gym-proto`:

- `contracts/v1/kafka/wire-format.json`
- `contracts/v1/kafka/confluent-7.7.1-fixtures.json`
- `contracts/v1/kafka/README.md`
- `contracts/v1/dlq/retry-and-dlq.json`
- `contracts/v1/compatibility.json`
- `contracts/v1/manifest.json`

The required value format is:

```text
magic byte | schema ID | Protobuf message indexes | Protobuf payload
```

Canonical headers are:

| Header | Required | Meaning |
|---|---:|---|
| `event-type` | yes | Fully qualified Protobuf descriptor name, such as `events.v1.UserRegisteredEvent` |
| `source` | yes | Producing service name from `spring.application.name` |
| `timestamp` | yes | Decimal Unix epoch milliseconds |
| `event-id` | yes | Immutable producer event ID and consumer idempotency key |
| `traceparent` | when tracing exists | W3C trace context |
| `tracestate` | optional | W3C trace state |
| `x-trace-id` | migration fallback only | Used only when no valid W3C span exists |

Legacy `x-event-type`, `x-source`, `x-timestamp`, and `x-event-id` headers are read-only migration inputs. New producers must not emit them.

Production requirements:

```yaml
gym:
  kafka:
    value-subject-name-strategy: io.confluent.kafka.serializers.subject.TopicNameStrategy
    auto-register-schemas: false
```

Schema registration must happen in a controlled contract-release step before service deployment.

## 4. Current-state inventory

### 4.1 Dependency state

`build.gradle` currently has:

```groovy
implementation 'com.gym:common-java:1.0.1'
implementation 'com.gym.proto:gym-proto-java:1.0.4'
```

The uncommitted repository change only updates `gym-proto-java` from `1.0.2` to `1.0.4`. It does not yet update `common-java`.

The local `common-java` HEAD reports version `1.0.4` and contains commit `c0c2e20` (`feat: add Confluent protobuf transport`). The exact GitHub Packages coordinate must be verified before changing this service because the shared library's publish workflow increments `version.properties` during publication.

`common-java` also exports `io.confluent:kafka-protobuf-serializer:7.7.1` as an `api` dependency. This service must add `https://packages.confluent.io/maven/` if Gradle cannot resolve the transitive Confluent artifact through the current repository set.

### 4.2 Legacy application configuration

`src/main/resources/application.yml` still declares envelope codecs:

```yaml
spring:
  kafka:
    consumer:
      value-deserializer: com.gym.common.kafka.message.EventEnvelopeDeserializer
    producer:
      value-serializer: com.gym.common.kafka.message.EventEnvelopeSerializer
```

These settings must be removed. `common-java` owns the producer and consumer factories and configures `KafkaProtobufSerializer` and `KafkaProtobufDeserializer` directly.

The retry configuration also uses old property names and units:

```yaml
gym.kafka.retry.max-attempts
gym.kafka.retry.initial-interval-ms
gym.kafka.retry.multiplier
```

The current shared properties are:

```yaml
gym.kafka.retry.enabled
gym.kafka.retry.retry-count
gym.kafka.retry.initial-interval
gym.kafka.retry.multiplier
gym.kafka.retry.max-interval
```

`retry-count` is the number of retries after the initial delivery. Contract defaults are three retries at 2s, 4s, and 8s.

### 4.3 Inbound consumers

`src/main/java/com/gym/member/payment/adapter/in/kafka/EventConsumerAdapter.java` currently accepts:

```java
EventEnvelope<UserRegisteredEvent>
EventEnvelope<PaymentCompletedEvent>
EventEnvelope<UserSuspendedEvent>
```

It reads key, event type, source, timestamp, payload, and identity from the envelope. That cannot work after the Kafka value becomes a concrete Protobuf message.

A pre-existing event-identity defect also exists: `resolveEventId` uses `envelope.traceId()` as the idempotency key instead of `envelope.eventId()`. Trace identity must never be used as business-event identity.

Current inbound defaults are:

| Logical event | Current topic default |
|---|---|
| User registered | `identity.user.registered` |
| User suspended | `identity.user.suspended` |
| Payment completed | `payment.completed` |

The checked-in `gym-proto` fixture uses `identity.user.registered.v1` and other `.v1` identity topics. Topic names are part of `TopicNameStrategy` subject identity, so adding `.v1` is not a local rename. It creates a new topic and a new `<topic>-value` subject and requires a coordinated producer/consumer rollout.

### 4.4 Outbound transactional outbox

The outbox boundary already accepts `com.google.protobuf.Message`, so the domain/application write path does not require an envelope.

Current flow:

1. A read-write business transaction builds a generated membership event.
2. `OutboxEventWriter.write(...)` assigns a UUID and stores the payload as Protobuf JSON.
3. `OutboxPublisherScheduler` claims pending records.
4. `OutboxPayloadParser` reconstructs a generated `Message`.
5. `EventPublisher` publishes it.
6. The relay marks the row `PUBLISHED` after synchronous Kafka acknowledgment.

The existing scheduler call is incorrect for the new API:

```java
eventPublisher.publish(
    event.getTopic(),
    key,
    payload,
    Map.of("eventId", event.getId().toString())
);
```

That overload generates a new canonical `event-id`; `eventId` in the map is only a custom header. It breaks stable outbox identity.

The required call is:

```java
eventPublisher.publish(
    event.getTopic(),
    key,
    payload,
    event.getId().toString(),
    Map.of()
);
```

The outbox UUID must remain unchanged across every retry and duplicate publication.

### 4.5 Local and CI infrastructure

`docker-compose.yml` starts PostgreSQL, Apache Kafka 3.8.0, and Kafka UI. It does not start Schema Registry.

`src/test/resources/application-test.yml` has no dynamic Schema Registry URL.

`.github/workflows/ci.yml` runs formatting and tests but does not:

- start Schema Registry;
- run real Kafka/Schema Registry round trips;
- verify fixture conformance;
- test compatibility;
- verify malformed-frame DLQ byte preservation.

### 4.6 Tests and local seeding

These files encode the old envelope contract:

- `src/test/java/com/gym/member/integration/kafka/EventPublisherIntegrationTest.java`
- `src/test/java/com/gym/member/integration/kafka/EventConsumerIntegrationTest.java`
- `src/test/java/com/gym/member/unit/kafka/EventConsumerAdapterUnitTest.java`
- `src/test/java/com/gym/member/util/TestEventSeeder.java`

The seeder publishes JSON strings and Spring's `__TypeId__` header. Neither is part of the new wire contract.

## 5. Blocking gaps in the current `common-java` implementation

Do not treat the `common-java` upgrade alone as sufficient. Two transport guarantees documented by the shared contract are not proven by the current code.

### Blocker A: concrete generated Protobuf consumer types

`KafkaAutoConfig.consumerFactory()` configures one global:

```java
ConsumerFactory<String, Object>
```

with `KafkaProtobufDeserializer`, but it does not configure:

- `specific.protobuf.value.type`;
- a verified `derive.type` policy;
- topic-to-message mappings;
- per-message listener container factories; or
- a converter from `DynamicMessage` to each generated event class.

A default generic Protobuf deserializer may return `DynamicMessage`. That is incompatible with listeners expecting `UserRegisteredEvent`, `PaymentCompletedEvent`, and `UserSuspendedEvent`.

#### Required shared-library resolution

Choose and document one supported strategy:

1. **Verified derived concrete types:** configure the Confluent derive-type option and prove that all `gym-proto` Java options produce the expected generated classes; or
2. **Typed listener factories:** expose a reusable factory builder that accepts a generated message class and configures `specific.protobuf.value.type`; or
3. **Explicit conversion layer:** consume `DynamicMessage` and convert by descriptor through a tested shared converter.

Preferred direction: use a shared typed-factory abstraction or verified derive-type configuration. Do not create three unrelated local deserializer configurations in this service unless the shared library cannot be released in time.

#### Required proof

Add a real Kafka + Schema Registry integration test that sends and receives at least two different generated message classes through the same service application. Bean-construction and configuration-map assertions are insufficient.

### Blocker B: preserving raw framed values in the DLQ

`KafkaAutoConfig.errorHandler()` uses the normal `KafkaTemplate<String, Object>` for `DeadLetterPublishingRecoverer`. That template uses `KafkaProtobufSerializer`.

On deserialization failure, the recoverer must publish the original framed `byte[]`. A Protobuf serializer cannot serialize arbitrary raw frame bytes, and the current configuration has no:

- `ByteArraySerializer` producer template;
- recoverer template resolver by value type;
- explicit restoration of `ErrorHandlingDeserializer` raw bytes; or
- integration test proving exact byte equality in the DLQ.

#### Required shared-library resolution

- Supply a raw-byte `KafkaTemplate` backed by `ByteArraySerializer`.
- Route deserialization failures through the raw-byte template.
- Route successfully deserialized Protobuf values through the Protobuf template when application handling fails.
- Preserve original key, exact framed value, and original headers.
- Add only the contract-defined DLQ diagnostic headers.
- Commit the original offset only after confirmed DLQ publication.

#### Required proof

Publish a malformed or unknown-schema frame, wait through the configured retry path, and assert:

- `{topic}.DLQ` receives the record;
- DLQ key equals the original key;
- DLQ value bytes exactly equal the original bytes;
- original headers remain present;
- `x-original-topic`, `x-exception-message`, `x-failed-at`, and `x-retry-count` are present;
- the original consumer offset is committed only after DLQ publication.

### Blocker exit criteria

`ms-gym-member` implementation begins only after a published `common-java` version has:

- deterministic generated-type delivery for multiple event types;
- exact raw-byte DLQ recovery for deserialization failures;
- live integration tests for both behaviors; and
- documented configuration properties.

If the service must proceed before that release, record the temporary local workaround and its removal issue. Do not silently assume the global `ConsumerFactory<String, Object>` meets these guarantees.

## 6. Target service design

### 6.1 Consumer adapter boundary

Use generated Protobuf messages at the inbound adapter. Keep Kafka metadata extraction in the adapter and pass only validated values to application use cases.

Preferred listener shape after the shared concrete-type blocker is resolved:

```java
public void handleUserRegistered(
    ConsumerRecord<String, UserRegisteredEvent> record,
    Acknowledgment acknowledgment
)
```

Equivalent typed records apply to the other two listeners.

The adapter must:

1. read `record.value()` as the generated payload;
2. read `record.key()` as the ordering/business fallback key;
3. decode canonical headers as UTF-8;
4. require a nonblank `event-id` in strict mode;
5. validate `event-type` against `payload.getDescriptorForType().getFullName()`;
6. use the canonical descriptor name as the application event type;
7. call the specific processing use case;
8. acknowledge only after successful processing;
9. throw on processing failure so the shared error handler performs retry/DLQ handling.

Do not use `traceparent`, `tracestate`, or `x-trace-id` as an event ID.

### 6.2 Header extraction

Introduce one reusable, tested header reader rather than repeating byte decoding in each listener. It can live locally under the inbound Kafka adapter initially, but it is a good candidate for `common-java` because every Java service needs the same rules.

Suggested local type if the shared library does not yet supply one:

```text
KafkaEventMetadata
- eventId
- eventType
- source
- timestamp
- traceparent
- tracestate
```

Validation rules:

- Duplicate canonical headers: reject or deterministically use the last header and emit a metric; choose one policy and test it.
- Missing/blank `event-id`: reject when `app.member.require-event-id=true`.
- Missing `event-type`: derive from the payload only during an explicit compatibility window.
- Mismatched `event-type`: reject and route to retry/DLQ; do not trust an unvalidated external type header.
- Invalid timestamp: reject in strict mode; meter a temporary compatibility fallback if migration requires it.
- Header decoding: UTF-8 only.

### 6.3 Compatibility fallback

The safest migration has no fallback: new Protobuf topics require all canonical headers.

If an explicit transition needs missing-event-ID support, derive a temporary ID from stable Kafka coordinates rather than tracing data:

```text
legacy:<topic>:<partition>:<offset>
```

This is deterministic for one Kafka record and avoids collisions caused by equal source/type/key/timestamp values. Emit a counter and warning for every fallback. Remove it after the migration window and set `MEMBER_REQUIRE_EVENT_ID=true` by default.

### 6.4 Application and idempotency behavior

Keep `MemberEventProcessingService` and `IdempotencyService` transport-independent.

Pass:

- canonical `event-id` as `eventId`;
- validated Protobuf descriptor name as `eventType`;
- generated payload;
- Kafka key only where the payment handler needs a fallback user ID.

Expected duplicate behavior:

- the first delivery claims `processed_events.event_id` and applies business behavior;
- retries or outbox duplicates with the same `event-id` return the existing idempotency result;
- trace changes do not affect deduplication.

### 6.5 Outbox persistence

Keep Protobuf JSON persistence for the first transport migration. Changing both Kafka framing and database payload encoding in one release increases replay risk without being necessary.

Improve type identity using the existing nullable `payload_type` column:

- retain the simple `event_type` temporarily because scheduled deduplication queries use values such as `MembershipExpiringSoonEvent`;
- populate `payload_type` with `payload.getDescriptorForType().getFullName()` for new rows;
- resolve parsers by `payload_type` first and fall back to legacy `event_type` for existing rows;
- test replay of rows created before and after the change;
- do not use `ignoringUnknownFields()` without a documented replay compatibility reason.

A later plan may migrate the outbox payload to deterministic Protobuf bytes. That requires binary storage, descriptor/version handling, backfill/replay rules, and is not needed for this Kafka refactor.

### 6.6 Outbox event identity

Use the five-argument `EventPublisher.publish` overload and pass the persisted outbox UUID explicitly.

Acceptance rule:

```text
outbox_events.id == Kafka header event-id
```

The equality must hold across:

- first publish;
- publisher timeout followed by retry;
- relay restart;
- duplicate Kafka delivery.

### 6.7 Topic and subject strategy

Do not mix JSON envelope and Protobuf-framed values on a topic unless a tested dual-format consumer exists.

Choose one rollout strategy across all producing and consuming services:

#### Strategy A: new versioned topics — recommended

- Create new `.v1` topics and `<topic>-value` subjects.
- Register schemas and compatibility before traffic.
- Start new consumers with new consumer groups.
- Switch or dual-publish producers during a bounded transition.
- Drain and retire old JSON topics.

Benefits: clean offsets, clean subjects, straightforward rollback.  
Cost: cross-service topic changes and temporary dual routing.

#### Strategy B: coordinated in-place cutover

- Stop producers.
- Drain or explicitly abandon old JSON records.
- Register schemas for the existing topic names.
- reset or replace consumer groups;
- deploy all consumers and producers together.

Benefits: no new topic names.  
Cost: operational coordination, weak rollback, and high risk of old JSON records reaching Protobuf consumers.

The service must not independently append `.v1`; the decision affects identity, payment, notification, analytics, and any other downstream service.

## 7. Implementation phases

## Phase 0 — Confirm artifacts and close shared-library blockers

### Tasks

- Verify the published `common-java` coordinate containing commit `c0c2e20` and subsequent fixes.
- Confirm GitHub Packages exposes its POM with the Confluent dependency.
- Fix concrete generated-type deserialization in `common-java`.
- Fix raw-frame DLQ recovery in `common-java`.
- Add common integration tests with Kafka and Schema Registry 7.7.1.
- Publish a new `common-java` version.
- Update `common-java` README, which still shows dependency version `1.0.0`.

### Exit criteria

- A sample application receives multiple generated Protobuf types without `DynamicMessage`/listener conversion failure.
- Malformed framed bytes arrive unchanged in the DLQ.
- The released artifact resolves from the same credentials available to this service's CI.

## Phase 1 — Lock contracts, topics, and schemas

### Tasks

- Decide versioned-topic versus in-place rollout with every producer/consumer owner.
- Produce the final inbound and outbound topic matrix.
- Define `<topic>-value` subjects using `TopicNameStrategy`.
- Set every subject to `BACKWARD` compatibility.
- Extend `gym-proto` fixtures beyond the current identity cases to cover:
  - `PaymentCompletedEvent`;
  - `MembershipActivatedEvent`;
  - `MembershipPausedEvent`;
  - `MembershipResumedEvent`;
  - `MembershipExpiringSoonEvent`;
  - `MembershipExpiredEvent`.
- Verify generated Java message descriptor names.
- Record owner approval and release evidence in `gym-proto/contracts/v1/manifest.json`.

### Exit criteria

- Every topic has one documented concrete value type.
- Every production subject is preregistered.
- Positive additive and negative incompatible schema tests pass.
- Contract manifest is no longer `pending-owner-approval` for the required Java/Kafka scope.

## Phase 2 — Upgrade dependencies and configuration

### Files

- `build.gradle`
- `src/main/resources/application.yml`
- `src/test/resources/application-test.yml`

### Tasks

- Upgrade `com.gym:common-java` to the verified fixed release.
- Retain the user's `gym-proto-java:1.0.4` update unless the contract phase publishes a newer required version.
- Add the Confluent Maven repository if dependency resolution requires it.
- Remove envelope serializer/deserializer properties.
- Add explicit Schema Registry and subject settings.
- Replace deprecated retry properties with `Duration` values.
- Keep `auto-register-schemas=false` outside controlled local/test setup.
- Add environment placeholders for Registry URL and, if required by the target environment, authentication/TLS properties supported by the fixed shared library.

Target shape:

```yaml
gym:
  kafka:
    schema-registry-url: ${SCHEMA_REGISTRY_URL:http://localhost:8081}
    value-subject-name-strategy: io.confluent.kafka.serializers.subject.TopicNameStrategy
    auto-register-schemas: ${SCHEMA_AUTO_REGISTER:false}
    publish-timeout: ${KAFKA_PUBLISH_TIMEOUT:PT25S}
    dlq:
      suffix: .DLQ
    retry:
      enabled: true
      retry-count: ${KAFKA_RETRY_COUNT:3}
      initial-interval: ${KAFKA_RETRY_INITIAL_INTERVAL:PT2S}
      multiplier: ${KAFKA_RETRY_MULTIPLIER:2.0}
      max-interval: ${KAFKA_RETRY_MAX_INTERVAL:PT8S}
```

### Exit criteria

- `./gradlew dependencies` resolves `common-java`, `gym-proto`, and Confluent serializer artifacts without local unpublished dependencies.
- Spring starts one intended Kafka producer factory and one intended listener configuration per shared-library design.
- No application property references an envelope codec.

## Phase 3 — Refactor inbound Kafka adapters

### Files

- `src/main/java/com/gym/member/payment/adapter/in/kafka/EventConsumerAdapter.java`
- new metadata/header helper under `payment/adapter/in/kafka` if not supplied by `common-java`
- `src/test/java/com/gym/member/unit/kafka/EventConsumerAdapterUnitTest.java`

### Tasks

- Remove the `EventEnvelope` import and all envelope parameters.
- Accept typed `ConsumerRecord` values or the shared typed listener abstraction.
- Extract Kafka key and canonical metadata from records/headers.
- Replace `resolveEventId(EventEnvelope<?>)` with canonical-header validation.
- Remove all trace-ID-as-event-ID behavior.
- Validate header `event-type` against the payload descriptor.
- Keep acknowledgment after successful processing only.
- Throw validation and processing failures so the shared error handler owns retries and DLQ publication.
- Preserve payment key fallback behavior explicitly.
- Keep the adapter dependent on specific application use cases/services, not a new pass-through facade.

### Exit criteria

- All three generated event classes reach their correct processing method.
- Missing or mismatched canonical headers follow the documented strict/transition policy.
- No inbound production code imports `EventEnvelope`.

## Phase 4 — Correct outbox publication identity and type metadata

### Files

- `src/main/java/com/gym/member/shared/outbox/scheduler/OutboxPublisherScheduler.java`
- `src/main/java/com/gym/member/shared/outbox/service/OutboxEventWriter.java`
- `src/main/java/com/gym/member/shared/outbox/service/OutboxPayloadParser.java`
- targeted outbox unit and integration tests

### Tasks

- Pass `event.getId().toString()` through the explicit event-ID overload.
- Stop emitting the noncanonical custom `eventId` header.
- Populate `payloadType` with the fully qualified descriptor name.
- Parse by `payloadType`, with a tested fallback for legacy pending rows.
- Preserve read-write transaction boundaries for every outbox write.
- Preserve scheduled event deduplication for `MembershipExpiringSoonEvent`.
- Preserve the application-assigned outbox UUID and do not add `@GeneratedValue`.
- Keep synchronous publication before marking `PUBLISHED`.

### Exit criteria

- The Kafka `event-id` equals the outbox UUID exactly.
- Existing pending rows remain publishable.
- Retry does not create a new event ID.
- Scheduled events remain deduplicated within the existing time window.

## Phase 5 — Add Schema Registry infrastructure

### Files

- `docker-compose.yml`
- external Helm/deployment configuration in the owning infrastructure repository
- `README.md`

### Tasks

- Add Confluent Schema Registry 7.7.1 to local Compose.
- Prefer a pinned Confluent Kafka 7.7.1 broker for contract parity, or prove the current Apache Kafka broker combination works with the Registry image.
- Add Registry health checks and application dependency/readiness behavior.
- Configure Kafka UI with the Schema Registry URL if supported.
- Expose host URL `http://localhost:8081` for local application runs.
- Use network URL `http://schema-registry:8081` for containers.
- Add production Registry URL, credentials, TLS trust, and secret references in the external deployment repository.
- Do not enable automatic registration in production.

### Exit criteria

- `./gradlew startEnv` starts PostgreSQL, Kafka, Schema Registry, and Kafka UI successfully.
- The service can fetch preregistered schemas.
- Local setup instructions include schema registration and compatibility checks.

## Phase 6 — Rewrite seeding and tests

### Unit tests

- Rewrite `EventConsumerAdapterUnitTest` for typed records and headers.
- Verify exact event ID, event type, key, payload, and acknowledgment behavior.
- Verify no acknowledgment on exceptions.
- Verify missing/blank/duplicate/malformed headers.
- Verify header/payload type mismatch.
- Update `OutboxPublisherSchedulerUnitTest` to assert the five-argument publisher call and exact UUID.
- Keep transport-neutral outbox relay and business tests.

### Integration tests

Use real Kafka and Schema Registry containers. Mocking `KafkaTemplate` does not prove the wire contract.

Required cases:

1. Publish and consume each inbound generated type.
2. Publish each outbound membership generated type.
3. Assert the wire value uses Confluent framing.
4. Assert the subject is `<topic>-value`.
5. Assert canonical headers and UTF-8 values.
6. Assert outbox UUID equals `event-id`.
7. Assert duplicate delivery is idempotent.
8. Assert application exception retries at 2s, 4s, and 8s, then reaches `.DLQ`.
9. Assert malformed/unknown-schema bytes are preserved exactly in `.DLQ`.
10. Assert original headers survive DLQ recovery.
11. Assert `auto.register.schemas=false` fails when a schema is absent.
12. Assert preregistered schemas publish successfully with auto-registration disabled.
13. Assert an additive compatible schema passes and an incompatible field-type change fails.

### Seeder

Rewrite `TestEventSeeder` to:

- use `KafkaProtobufSerializer`;
- send generated message values directly;
- set canonical headers;
- accept `kafka.bootstrap` and `schema.registry.url` parameters;
- use an explicit generated event ID;
- remove Jackson, `EventEnvelope`, string values, and `__TypeId__`.

Prefer calling the shared `EventPublisher` in a Spring test utility if that exercises the same production configuration without starting the full business application.

### Exit criteria

- No test or seeder imports `EventEnvelope`, `EventEnvelopeSerializer`, or `EventEnvelopeDeserializer`.
- Real integration tests prove framing, concrete typing, canonical headers, idempotency, retry, and DLQ behavior.
- `./gradlew test` and `./gradlew check` pass the existing 95% coverage gate.

## Phase 7 — CI and release gates

### Files

- `.github/workflows/ci.yml`
- optional reusable workflow in the shared infrastructure repository

### Tasks

- Ensure CI can resolve `common-java` and `gym-proto` from GitHub Packages.
- Run Kafka/Schema Registry integration tests through Testcontainers.
- Run `gym-proto` fixture verification against the pinned contract artifact.
- Run non-mutating Schema Registry compatibility checks.
- Fail CI when:
  - a required subject is missing;
  - the wrong subject strategy is used;
  - automatic production registration is enabled;
  - fixture bytes differ;
  - canonical headers are missing;
  - raw DLQ bytes differ.
- Publish service artifacts only after contract checks pass.

### Exit criteria

- CI proves the same wire contract used locally and in production.
- Contract checks fail before deployment, not after a runtime serialization error.

## Phase 8 — Coordinated rollout

### Preparation

- Inventory every producer and consumer for all eight member-related event types.
- Record topic, subject, consumer group, current offset, owner, and deployment order.
- Register schemas and set `BACKWARD` compatibility.
- Create DLQ topics with retention and access controls.
- Create dashboards and alerts before traffic migration.

### Recommended versioned-topic order

1. Deploy new consumers for versioned Protobuf topics with no traffic.
2. Verify Registry access and consumer readiness.
3. Enable producer dual-publish or switch producers according to the agreed window.
4. Compare old/new consumer business outcomes and idempotency metrics.
5. Move downstream consumers of member outbound events.
6. Stop old JSON publication.
7. Drain old topics and confirm no required records remain.
8. Disable transition fallbacks and require canonical `event-id`.
9. Retire old consumers, groups, subjects, and topic permissions after retention requirements are met.

### In-place alternative order

1. Stop producers.
2. Drain or archive old JSON records.
3. Deploy new consumers with new group IDs or explicitly reset offsets.
4. Register and verify schemas.
5. Deploy new producers.
6. Resume traffic.

Do not start a Protobuf consumer at an offset containing JSON envelope records. Those records will fail deserialization and may flood the DLQ.

### Rollback

For versioned topics:

- stop new publication;
- keep old consumers/topics available during the rollback window;
- resume old producer path only if dual publishing or a reversible producer release was retained;
- do not replay Protobuf frames into JSON consumers.

For an in-place cutover, rollback requires a reverse bridge or restored old topic. This is why versioned topics are preferred.

## 8. Observability and operational controls

Add or verify metrics for:

- published events by topic and Protobuf descriptor;
- publish latency and timeout count;
- Schema Registry request/error count;
- serialization and deserialization failures;
- listener success/failure by topic;
- retry count;
- DLQ publication success/failure;
- missing canonical header fallback count;
- event-type mismatch count;
- duplicate event-ID count;
- outbox pending, in-flight, failed, and oldest-record age;
- time from outbox creation to Kafka acknowledgment.

Logs must include:

- topic, partition, offset, key, `event-id`, and descriptor name;
- no full payload for personal/member data;
- no internal exception text in client-visible DLQ headers.

Alerts:

- any Schema Registry connectivity failure above a short threshold;
- any DLQ publication failure;
- sustained deserialization errors;
- outbox oldest pending age above the delivery SLO;
- missing `event-id` after strict mode begins;
- unexpected legacy JSON traffic after cutover.

## 9. Security and configuration requirements

- Store Schema Registry credentials and TLS material in the deployment secret manager, not Git.
- Use TLS and authenticated Registry access outside trusted local environments when supported by the platform.
- Restrict schema registration permissions to CI/release identities; runtime service identities need read access only when `auto.register.schemas=false`.
- Restrict topic and DLQ ACLs by producer/consumer role.
- Treat canonical headers as untrusted input at the consumer boundary and validate them.
- Do not log complete Protobuf payloads containing email, full name, or other member data.
- Preserve client-safe DLQ diagnostics; do not put exception messages or stack traces in Kafka headers.

## 10. `common-java` capabilities worth adopting

### Adopt during this migration

#### Native publisher and explicit event-ID overload

Directly required. It publishes the generated `Message`, injects canonical headers, applies W3C trace propagation, waits for broker acknowledgment, and supports an explicit immutable event ID.

#### Shared retry, manual acknowledgment, and DLQ configuration

Adopt after the raw-byte blocker is fixed. This centralizes the 2s/4s/8s retry contract, `.DLQ` suffix, offset commit policy, and diagnostic headers.

#### New `KafkaEventProperties`

Adopt the current `Duration`-based retry properties, `schemaRegistryUrl`, subject strategy, auto-registration policy, DLQ suffix, and publish timeout. Remove deprecated `backoff` and `maxAttempts` usage.

#### W3C tracing headers

Use `traceparent`/`tracestate` for correlation. Keep event ID and trace identity separate.

### Adopt as a small follow-up

#### `NormalPage.map(Function)`

The service already uses `NormalPage<MemberDto>`. The newer `map` method can simplify DTO-to-Protobuf mapping without changing the API contract. For example, `MemberGrpcDelegate.listMembers` can map the page items through `memberMapper::toResponse` while retaining page metadata.

This is low risk but should be a separate cleanup commit from Kafka transport changes.

### Do not adopt now

#### `PageMapper.toProtoResponse`

The current `ListMembersResponse` schema contains `repeated members` and `int32 total`; it does not expose the shared `PaginationResponse` shape. Adopting `PageMapper` requires a `gym-proto` API change and client migration. `NormalPage.map` is useful now; `PageMapper` is not directly compatible.

#### Cursor pagination

`CursorUtils` and `CursorPage` are useful for high-growth lists, but this service's public request uses `page` and `limit`. Cursor adoption is an API change and belongs in a separate proposal.

#### Additional gRPC interceptor refactor

The service already uses shared `RequireRole`, authentication, exception, logging, metrics, and tracing interceptors while owning its manual gRPC server lifecycle. Keep the current server configuration unless a separate shared lifecycle abstraction is introduced and tested.

#### Applying `BaseEntity` to outbox/idempotency entities

Core entities already use the shared persistence base where appropriate. The outbox has an application-assigned UUID plus lease/retry semantics and must remain separate. Do not add `@GeneratedValue` to it.

### Shared-library follow-up candidates

- Add a canonical `KafkaEventMetadata`/header reader to prevent service-specific parsing rules.
- Add typed Protobuf listener-factory support or verified descriptor-derived type support.
- Add raw-byte and Protobuf template routing to the DLQ recoverer.
- Add Schema Registry authentication/TLS property pass-through if production requires it.
- Remove legacy envelope APIs only after all services migrate.
- Correct the stale dependency version in `common-java/README.md`.

## 11. File-by-file change matrix

| File | Planned change |
|---|---|
| `build.gradle` | Upgrade fixed `common-java`; retain/align `gym-proto`; add Confluent repository only if required |
| `src/main/resources/application.yml` | Remove envelope codecs; add Registry, subject, registration, timeout, retry, and DLQ properties; finalize topic names |
| `src/test/resources/application-test.yml` | Supply dynamic Registry settings and explicit test registration policy |
| `src/main/java/com/gym/member/payment/adapter/in/kafka/EventConsumerAdapter.java` | Consume concrete Protobuf values and canonical record metadata |
| new inbound Kafka metadata helper | Centralize UTF-8 header extraction and validation if not supplied by `common-java` |
| `src/main/java/com/gym/member/shared/outbox/scheduler/OutboxPublisherScheduler.java` | Pass exact outbox UUID through explicit event-ID overload |
| `src/main/java/com/gym/member/shared/outbox/service/OutboxEventWriter.java` | Populate fully qualified `payloadType` while preserving simple `eventType` compatibility |
| `src/main/java/com/gym/member/shared/outbox/service/OutboxPayloadParser.java` | Resolve by descriptor type first; retain old-row fallback |
| `docker-compose.yml` | Add pinned Schema Registry and health checks; align broker image if selected |
| `src/test/java/com/gym/member/unit/kafka/EventConsumerAdapterUnitTest.java` | Rewrite for records, typed values, headers, acknowledgment, and failures |
| `src/test/java/com/gym/member/integration/kafka/EventPublisherIntegrationTest.java` | Replace stale envelope assertions with real framed-value and canonical-header assertions |
| `src/test/java/com/gym/member/integration/kafka/EventConsumerIntegrationTest.java` | Test real Kafka/Registry consumption and idempotency |
| `src/test/java/com/gym/member/integration/outbox/OutboxPublisherIntegrationTest.java` | Assert outbox UUID, framing, subject, and published state |
| `src/test/java/com/gym/member/unit/scheduler/OutboxPublisherSchedulerUnitTest.java` | Verify exact five-argument publisher call |
| `src/test/java/com/gym/member/util/TestEventSeeder.java` | Publish direct Protobuf values with Schema Registry and canonical headers |
| `.github/workflows/ci.yml` | Run registry/wire/compatibility/DLQ gates |
| `README.md` | Document Schema Registry, Protobuf values, headers, topics, local setup, and correct dependency versions |
| external infrastructure repository | Add Registry endpoint, secrets, TLS, ACLs, topics, subjects, and rollout settings |

## 12. Suggested commit sequence

Keep shared-library fixes and service changes reviewable:

1. `fix: support concrete protobuf consumers and raw-byte kafka dlq`
   - `common-java` only.
2. `test: add kafka protobuf schema registry contract coverage`
   - `common-java` and/or `gym-proto` contract evidence.
3. `feat: register member kafka protobuf contracts`
   - `gym-proto` fixtures/subjects/manifests.
4. `refactor: configure member service for kafka protobuf transport`
   - dependency and configuration changes.
5. `refactor: consume protobuf events with canonical kafka metadata`
   - inbound adapter and unit tests.
6. `fix: preserve outbox event id in kafka headers`
   - outbox identity and compatibility tests.
7. `test: verify member kafka schema registry and dlq behavior`
   - real integration suite and seeder.
8. `chore:` is not allowed by the repository-specific commit convention; include documentation/CI with the relevant `refactor:`, `test:`, or `fix:` commit instead.

## 13. Acceptance criteria

The migration is complete only when all statements are true:

- [ ] No production/test code in this repository imports a legacy `EventEnvelope` transport type.
- [ ] Every Kafka value is a concrete generated Protobuf message with Confluent framing.
- [ ] Every subject is `<topic>-value` with `BACKWARD` compatibility.
- [ ] Runtime auto-registration is disabled in production.
- [ ] Every produced event has `event-type`, `source`, `timestamp`, and `event-id` headers.
- [ ] Outbox UUID equals Kafka `event-id` across retries.
- [ ] Consumers never use trace IDs as idempotency IDs.
- [ ] Consumers acknowledge only after successful/idempotent business processing.
- [ ] Three retries occur at 2s, 4s, and 8s before DLQ recovery.
- [ ] DLQ publication preserves key, exact framed bytes, and original headers.
- [ ] Old JSON records cannot accidentally reach the new Protobuf consumer path.
- [ ] Existing pending outbox records remain replayable.
- [ ] Real Kafka + Schema Registry tests cover all inbound event types and all outbound member event types.
- [ ] CI runs wire fixture and compatibility checks.
- [ ] Local Compose includes a healthy Schema Registry.
- [ ] Production deployment has Registry credentials/TLS/ACLs and preregistered subjects.
- [ ] README dependency versions and local setup are current.
- [ ] `./gradlew spotlessCheck`, `./gradlew test`, and `./gradlew check` pass.

## 14. Open decisions

Resolve these before implementation:

1. What published `common-java` version contains both blocker fixes?
2. Will the platform use new `.v1` topics or an in-place coordinated cutover?
3. Which service/repository owns schema registration and compatibility enforcement?
4. Does production Schema Registry require basic authentication, mTLS, or another supported mechanism?
5. Will generated type delivery use verified derive-type configuration or typed listener factories?
6. How long must legacy topics and consumer groups remain available for rollback?
7. Should missing canonical metadata be rejected immediately or supported through a short metered transition?
8. What retention and replay policy applies to `.DLQ` topics?
9. Should outbox `payload_type` be populated now while preserving JSON, or deferred to a dedicated outbox replay hardening change?

## 15. Primary risks

| Risk | Impact | Mitigation |
|---|---|---|
| Generic deserializer returns `DynamicMessage` | Typed listeners fail at runtime | Fix and prove concrete type delivery in `common-java` first |
| DLQ recoverer uses Protobuf serializer for raw bytes | Poison records cannot be preserved or published | Add raw-byte template routing and byte-equality integration test |
| Old JSON records remain at consumer offsets | Deserialization failures and DLQ flood | New versioned topics/groups or controlled drain/reset |
| Schema absent while auto-registration is disabled | Producer outage | Preregister and verify every subject before deployment |
| Topic rename changes subject identity | Producer/consumer incompatibility | Coordinate topic matrix across all services |
| Outbox relay generates a new event ID | Duplicate business effects | Pass persisted outbox UUID through explicit overload |
| Trace ID remains idempotency key | Incorrect deduplication | Use canonical `event-id` only |
| Outbox JSON/type lookup cannot replay old rows | Stuck pending events | Descriptor-first parsing with tested legacy fallback |
| Shared dependency is unpublished or has stale metadata | CI/build failure | Verify package coordinate and POM before service upgrade |
| Tests continue mocking Kafka | False confidence | Require live Kafka/Registry contract tests |

## 16. Recommended next action

Fix and publish the two `common-java` blockers first. Then implement Phases 1–4 on a coordinated topic strategy, followed by real infrastructure and contract tests. Do not start by only changing listener signatures: the current shared consumer and DLQ factories do not yet prove the required runtime behavior.
