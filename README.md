# ms-gym-member

> **Member Profile & Subscription Lifecycle Microservice**
> **Tech Stack:** Java 26 | Spring Boot 4.1.0 | PostgreSQL | Kafka | gRPC (Port 50051) | REST (Port 8080)

---

## 📋 Overview

`ms-gym-member` is a core microservice in the Gym Management system. It owns member profiles, gym location configurations, subscription plans, and the member state machine.

### Key Responsibilities
- **Member Lifecycle Management**: (`NONE → ACTIVE → PAUSED → EXPIRED`)
- **Subscription State Machine**:
  - Flexible plans: `MONTHLY`, `YEARLY`, `LIFETIME`
  - Pause & Resume rules: max 30-day pause limit, max 2 pauses per cycle, `LIFETIME` pause restriction (`CannotPauseLifetimeException`)
- **Gym Location Management**: CRUD for gym locations and daily door QR secrets (`gym_qr_secrets`)
- **Daily Gym Door QR Secret Generation**: SHA-256 rotating daily secret (`SHA256(gym_id + today + daily_secret)`)
- **Event-Driven Integration**:
  - Consumes `identity.user.registered`, `payment.completed`
  - Publishes `membership.activated`, `membership.paused`, `membership.resumed`, `membership.expiring-soon`, `membership.expired`

---

## 🛠 Tech Stack & Dependencies

- **Language & Runtime**: Java 26 (Eclipse Temurin 26)
- **Framework**: Spring Boot 4.1.0 (Spring Data JPA, Spring Kafka, Spring Web)
- **Database**: PostgreSQL 16+ (Schema migrations managed via Flyway)
- **Shared Libraries (GitHub Packages)**:
  - `com.gym:common-java:1.0.1`
  - `com.gym.proto:gym-proto-java:1.0.0`
- **Containerization & Infra**: Docker, Docker Compose, Kubernetes Helm Charts (`gym-infra`)

---

## 🏗 Project Architecture

Domain-Driven Design (DDD) with Hexagonal / Clean Architecture:

```
src/main/java/com/gym/member/
├── member/                    # Bounded Context: Member & Subscription Domain
│   ├── domain/                # Aggregates, Enums, DTOs, Events, Exceptions
│   ├── application/           # Member & Subscription Use Cases, Services, Schedulers
│   └── adapter/               # gRPC Inbound Delegates/Handlers, Persistence Entities & Repositories
├── location/                  # Bounded Context: Gym Location & Door QR Secret Domain
│   ├── domain/                # Gym Location Models & DTOs
│   ├── application/           # GymLocation & Daily Door QR Services & Rotation Schedulers
│   └── adapter/               # Gym Location gRPC Handlers, Persistence Entities & Repositories
├── payment/                   # Bounded Context: Payment Integration & Event Listener
│   └── adapter/               # Payment gRPC Client & Kafka Event Consumer Adapters
├── shared/                    # Shared Infrastructure Context
│   ├── outbox/                # Transactional Outbox Pattern (Entities, Repositories, Relay Service, Schedulers)
│   ├── idempotency/           # Consumer Idempotency Tracking
│   └── mapper/                # Common Mapping Utilities
└── config/                    # Spring Boot, Security, and gRPC Configuration
```

---

## ⚡ Quick Start & Testing

### Prerequisites
- JDK 26 installed (`java -version`)
- Docker & Docker Compose running

### 1. Run Unit Tests
```bash
./gradlew test
```

### 2. Start Local Testing Environment (PostgreSQL + Kafka KRaft - Zookeeper-less)
```bash
./gradlew startEnv
```

### 3. Run Application Locally
```bash
./gradlew bootRun
```

### 4. Stop Local Testing Environment
```bash
./gradlew stopEnv
```

---

## ⚙️ CI/CD Pipeline

Continuous Integration and Container Builds are automated via GitHub Actions using reusable workflows from [`gym-infra`](https://github.com/pploc/gym-infra):

- **Testing**: Runs JUnit 5 test suite on JDK 26
- **Docker Build**: Builds and pushes multi-stage Docker images to GitHub Container Registry (`ghcr.io/pploc/ms-gym-member:latest`)
