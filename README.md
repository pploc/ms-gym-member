# ms-gym-member

> **Member Profile & Subscription Lifecycle Microservice**
> **Tech Stack:** Java 26 | Spring Boot 4 | PostgreSQL | Kafka | gRPC mTLS (Port 50051)

---

## Overview

`ms-gym-member` owns member profiles, multi-gym subscriptions, purchase orchestration, and membership lifecycle events.

It does **not** own gym locations or plan catalog data. Those live in **ms-gym-plans**. Member stores only opaque `gym_id` / `plan_id` references plus purchased snapshots.

### Key responsibilities
- Member shell + profile updates
- Subscription state machine (`NONE` / `ACTIVE` / `PAUSED` / `EXPIRED`) with multi-gym aggregate status
- Durable purchase initiation (`idempotency_key` → stable `purchase_id` as Payment `reference_id`)
- Outbox lifecycle events and atomic Kafka consumer claims
- Workload RPCs for Identifier, Check-in, and Notification over verified mTLS SAN

---

## Trust boundaries

| Path | Peer SAN | Notes |
|------|----------|-------|
| End-user RPCs | Kong | Kong verifies JWT, injects `x-user-*`; Member accepts those headers only from Kong SAN |
| `GetMembershipStatusByUserId` | `ms-gym-identifier` | no user claims |
| `ValidateMembership` | `ms-gym-checkin` | no user claims |
| `ListMembersByStatus` | `ms-gym-notification` | requires ≥1 `gym_ids` |

NetworkPolicy (Helm overlay) admits gRPC `50051` from Kong + those three workloads only. HTTP `8080` is actuator/health.

Internal methods are never Kong-routed. Public REST is deferred until a generated gRPC-Gateway exists.

---

## Architecture

```
src/main/java/com/gym/member/
├── member/                    # Member & subscription domain
│   ├── domain/
│   ├── application/           # purchase, lifecycle, expiry, event processing
│   └── adapter/               # gRPC, JPA, Specifications
├── plans/                     # Plans ResolvePurchasablePlan client
├── payment/                   # Payment InitiatePayment client + Kafka consumers
├── shared/                    # outbox, processed-event idempotency
└── config/                    # mTLS, Kong interceptor, method SAN allowlist
```

---

## Quick start

### Prerequisites
- JDK 26
- Docker / Docker Compose

### Unit tests
```bash
./gradlew test
```

### Local dependencies
```bash
./gradlew startEnv   # PostgreSQL + Kafka + Schema Registry
./gradlew bootRun    # generates certs/local if missing
./gradlew stopEnv
```

Local testing commands: [LOCAL_TESTING.md](./LOCAL_TESTING.md).

---

## Purchase durability

1. Client sends required `idempotency_key`.
2. Member creates or loads one `pending_purchases` row for `(user_id, idempotency_key)`.
3. TX commits **before** Payment RPC.
4. Payment receives stable `reference_id=purchase_id`.
5. Retry reuses same purchase/reference; payload mismatch conflicts.

Payment must return the same intent for repeated `InitiatePayment` with the same membership `reference_id`.

---

## gRPC TLS config

| Env | Default | Purpose |
|-----|---------|---------|
| `MEMBER_GRPC_TLS_ENABLED` | `true` | mTLS |
| `MEMBER_GRPC_ALLOW_PLAINTEXT` | `false` | test-only plaintext |
| `MEMBER_GRPC_SERVER_CERT` | `certs/local/server.crt` | server chain; **prod must override** |
| `MEMBER_GRPC_SERVER_KEY` | `certs/local/server.key` | server key; **prod must override** |
| `MEMBER_GRPC_CLIENT_CA` | `certs/local/ca.crt` | client trust CA; **prod must override** |
| `PLANS_GRPC_TARGET` | _(empty)_ | Plans gRPC host:port; required for purchase |
| `PAYMENT_GRPC_TARGET` | _(empty)_ | Payment gRPC host:port |

Local defaults point at `certs/local/` (gitignored; `bootRun` → `ensureLocalCerts`). Never bake those files into the image.

### Shared libraries
- `com.gym:common-java:2.1.0`
- `com.gym.proto:gym-proto-java:4.1.0`

---

## CI/CD

Reusable workflows from [`gym-infra`](https://github.com/pploc/gym-infra):
- JUnit 5 on JDK 26
- Multi-stage image to `ghcr.io/pploc/ms-gym-member`
