# Member — local testing (gRPC)

Copy-paste gRPC bodies for Postman / grpcurl against **current** `member.v1` contract (`gym-proto` v3).

> Older `GRPCURL.md` still shows JWT Bearer + pre-split gym/plan RPCs. Prefer **this file**: trusted claim metadata + v3 methods only.

## 1. Start

```bash
cd ms-gym-member
./gradlew startEnv
```

### Option A — plaintext gRPC

```bash
export MEMBER_GRPC_TLS_ENABLED=false
export GRPC_SERVER_TLS_ALLOW_PLAINTEXT=true
./gradlew bootRun
```

### Option B — local mTLS (Postman client cert)

```bash
./scripts/generate-local-certs.sh
# certs/local/ gitignored; P12 password: changeit

export MEMBER_GRPC_TLS_ENABLED=true
export MEMBER_GRPC_SERVER_CERT="$PWD/certs/local/server.crt"
export MEMBER_GRPC_SERVER_KEY="$PWD/certs/local/server.key"
export MEMBER_GRPC_CLIENT_CA="$PWD/certs/local/ca.crt"
./gradlew bootRun
```

| Port | Protocol |
|------|----------|
| `8080` | HTTP (if exposed; catalog is **not** on Member after G6 split) |
| `50051` | gRPC (plaintext A, or mTLS B) |
| `5432` | Postgres `gym_member` |
| `9092` / `8081` | Kafka + Schema Registry (from `startEnv`) |

Stop deps: `./gradlew stopEnv`.

Proto:

`../gym-proto/proto/member/v1/member.proto`  
import path root: `../gym-proto/proto`

### Postman mTLS (option B)

1. Certificates → host `localhost:50051` → `client-postman.crt` + `client-postman.key` (or `.p12` / `changeit`).
2. Trust `certs/local/ca.crt` for server.
3. Public RPCs still need `x-user-*` metadata.
4. `GetMembershipStatusByUserId` → `client-identifier.p12` (Identifier SAN).

## 2. Auth metadata (user-facing RPCs)

Member gRPC auth uses **gateway claim metadata**, not a Bearer JWT, via `AuthServerInterceptor`:

| Key | Example | Notes |
|-----|---------|--------|
| `x-user-id` | `11111111-1111-1111-1111-111111111111` | required; often the owning user for profile/purchase |
| `x-user-role` | `CUSTOMER` | see table below |
| `x-membership-status` | `NONE` | `NONE` \| `ACTIVE` \| `PAUSED` \| `EXPIRED` |
| `x-gym-id` | `22222222-2222-2222-2222-222222222222` | selected gym; needed for purchase / gym-scoped flows |

**Do not** send an empty `x-gym-id` value — omit the key if unused.

### Role map (v3 public RPCs)

| RPC | Roles |
|-----|--------|
| `GetMember` | `CUSTOMER` |
| `UpdateProfile` | `CUSTOMER` |
| `ListMembers` | `ADMIN`, `SUPER_ADMIN` |
| `PurchaseMembership` | `CUSTOMER` |
| `PauseMembership` | `CUSTOMER` |
| `ResumeMembership` | `CUSTOMER` |
| `GetMembershipStatus` | `CUSTOMER` |
| `ValidateMembership` | `CHECKIN_SERVICE` (service role claim; not end-user) |
| `ListMembersByStatus` | `NOTIFICATION_SERVICE` (service role claim; not end-user) |
| `GetMembershipStatusByUserId` | **internal workload** (Identifier mTLS) — not Postman plaintext |

Pre-split remnant on this branch: handler still exposes gym/plan methods (`GetPlans`, `CreateGymLocation`, …). Target G8 removes them; catalog testing should use **ms-gym-plans**.

Gym/plan catalog RPCs live on **ms-gym-plans**, not Member.

## 3. Postman — gRPC setup

1. New → **gRPC**.
2. URL: `grpc://localhost:50051` (plaintext / insecure for local).
3. Import `member/v1/member.proto` (import path = `gym-proto/proto`).
4. Select `member.v1.MemberService/<Method>`.
5. **Metadata**: claim keys above.
6. **Message**: JSON below (protobuf JSON **camelCase**).

```bash
grpcurl -plaintext localhost:50051 list
grpcurl -plaintext localhost:50051 describe member.v1.MemberService
```

## 4. gRPC bodies

Use real IDs from your DB / seed / prior responses.

### GetMember

```json
{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"
}
```

Metadata: `x-user-id` = owning user, `x-user-role: CUSTOMER`.

### UpdateProfile

```json
{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0",
  "fullName": "Nguyen Van A",
  "phone": "+84901234567",
  "avatarUrl": "https://example.com/avatar.jpg",
  "dateOfBirth": "1995-05-15"
}
```

### ListMembers — admin

```json
{
  "page": 0,
  "limit": 10,
  "gymId": "22222222-2222-2222-2222-222222222222"
}
```

Metadata: `x-user-role: ADMIN` or `SUPER_ADMIN`, set `x-gym-id` as needed by your scope rules.

### PurchaseMembership — customer

Requires selected gym on claims; plan must exist in **Plans** and be resolvable (G8 wiring). Locally, Payment gRPC target may be empty/plaintext mock — call may fail closed without Payment/Plans.

```json
{
  "planId": "44444444-4444-4444-4444-444444444444",
  "provider": "MOMO",
  "discountCode": ""
}
```

Providers: `MOMO` | `ZALOPAY` | `VNPAY`.  
V3: non-blank `discountCode` is rejected until promotions exist — use `""` or omit.

Metadata example:

```text
x-user-id: 11111111-1111-1111-1111-111111111111
x-user-role: CUSTOMER
x-gym-id: 22222222-2222-2222-2222-222222222222
x-membership-status: NONE
```

### PauseMembership

```json
{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"
}
```

### ResumeMembership

```json
{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"
}
```

### GetMembershipStatus

```json
{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"
}
```

### ValidateMembership

```json
{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0",
  "gymId": "22222222-2222-2222-2222-222222222222"
}
```

### ListMembersByStatus

```json
{
  "status": "ACTIVE",
  "gymIds": [
    "22222222-2222-2222-2222-222222222222"
  ]
}
```

### GetMembershipStatusByUserId — internal only

```json
{
  "userId": "11111111-1111-1111-1111-111111111111",
  "gymId": "22222222-2222-2222-2222-222222222222"
}
```

Needs Identifier client cert over mTLS. Plaintext Postman → `PERMISSION_DENIED` / workload failure (expected).

## 5. grpcurl examples

```bash
PROTO_DIR=../gym-proto/proto
H=(
  -H 'x-user-id: 11111111-1111-1111-1111-111111111111'
  -H 'x-user-role: CUSTOMER'
  -H 'x-gym-id: 22222222-2222-2222-2222-222222222222'
  -H 'x-membership-status: NONE'
)

grpcurl -plaintext -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H[@]}" \
  -d '{"memberId":"31b6a40a-99d7-4f22-b6f7-f62a11298ba0"}' \
  localhost:50051 member.v1.MemberService/GetMember

grpcurl -plaintext -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H[@]}" \
  -d '{"planId":"44444444-4444-4444-4444-444444444444","provider":"MOMO","discountCode":""}' \
  localhost:50051 member.v1.MemberService/PurchaseMembership
```

If a field is dropped, try snake_case (`member_id`); protobuf JSON accepts both.

## 6. Sample response shapes

### MemberResponse

```json
{
  "id": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0",
  "userId": "11111111-1111-1111-1111-111111111111",
  "fullName": "Nguyen Van A",
  "phone": "+84901234567",
  "avatarUrl": "https://example.com/avatar.jpg",
  "dateOfBirth": "1995-05-15",
  "status": "ACTIVE"
}
```

### MembershipResponse

```json
{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0",
  "status": "ACTIVE",
  "startDate": "2026-08-01",
  "endDate": "2026-08-31",
  "remainingDays": 20
}
```

### PurchaseResponse

```json
{
  "paymentUrl": "https://payment.example/redirect/...",
  "paymentId": "pay-..."
}
```

## 7. What this service needs vs Plans

| Test goal | Service |
|-----------|---------|
| Create gym / plan catalog | **ms-gym-plans** (`LOCAL_TESTING.md`) |
| Profile, membership pause/resume, purchase orchestration | **ms-gym-member** |
| Real JWT login | Identifier + Kong (not required for claim-header gRPC) |
| Purchase end-to-end | Member + Plans resolve + Payment (or fake) + Kafka `payment.completed` |

Member alone: profile/status RPCs if data exists (seed / prior Kafka `UserRegistered`). Purchase without Plans/Payment will fail closed — that is expected.

## 8. Common failures

| Symptom | Cause |
|---------|--------|
| `UNAUTHENTICATED` | missing/invalid claim metadata |
| `PERMISSION_DENIED` | wrong role or internal RPC without mTLS |
| TLS handshake error | set `MEMBER_GRPC_TLS_ENABLED=false` and `GRPC_SERVER_TLS_ALLOW_PLAINTEXT=true` |
| Purchase / resolve errors | Plans or Payment not running; non-blank discount |
| Unknown method `GetPlans` / gym RPCs | removed in v3 — use **ms-gym-plans** |
