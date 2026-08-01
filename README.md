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

Hexagonal / Clean Architecture:

```
src/main/java/com/gym/member/
├── domain/
│   ├── model/         # Domain enums & models (MembershipStatus, PlanType)
│   ├── dto/           # Java 26 Records (MemberDto, SubscriptionDto, GymLocationDto)
│   ├── event/         # Domain event records
│   └── exception/     # Custom domain exceptions
├── application/
│   ├── service/       # Domain application services (MemberService, SubscriptionService, etc.)
│   └── scheduler/     # Scheduled jobs (0 0 * * * QR rotation, 6 AM & 9 AM expiry jobs)
├── adapter/
│   ├── in/
│   │   ├── grpc/      # MemberGrpcHandler (gRPC service on port 50051)
│   │   └── kafka/     # EventConsumerAdapter (identity & payment event listeners)
│   └── out/
│       └── persistence/# JPA Entities & Repositories
└── config/            # Spring & gRPC configuration
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

### 2. Start Local Testing Environment (PostgreSQL + Kafka)
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
