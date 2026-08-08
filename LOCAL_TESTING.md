# Member — local testing (gRPC)

Copy-paste bodies for Postman / grpcurl against **current** `member.v1` contract (`gym-proto` v3).

> Older `GRPCURL.md` still shows JWT Bearer + pre-split gym/plan RPCs. Prefer **this file**: trusted claim metadata + v3 methods only.

## 1. Start (default = mTLS)

```bash
cd ms-gym-member
./gradlew startEnv
./gradlew bootRun
```

`bootRun` runs `ensureLocalCerts` if `certs/local/` incomplete.

| Port | Protocol |
|------|----------|
| `8080` | HTTP (if exposed; catalog is **not** on Member after G6 split) |
| `50051` | gRPC **mTLS** (client cert required) |
| `5432` | Postgres `gym_member` |
| `9092` / `8081` | Kafka + Schema Registry |

Stop deps: `./gradlew stopEnv`.

Plaintext override:

```bash
export MEMBER_GRPC_TLS_ENABLED=false
export MEMBER_GRPC_ALLOW_PLAINTEXT=true
./gradlew bootRun
```

Proto: `../gym-proto/proto/member/v1/member.proto` (import path root `../gym-proto/proto`).

### Shared shell vars (grpcurl)

```bash
# from ms-gym-member
PROTO_DIR=../gym-proto/proto
C=certs/local
MTLS=(-cacert "$C/ca.crt" -cert "$C/client-postman.crt" -key "$C/client-postman.key")
H_CUST=(
  -H 'x-user-id: 11111111-1111-1111-1111-111111111111'
  -H 'x-user-role: CUSTOMER'
  -H 'x-gym-id: 22222222-2222-2222-2222-222222222222'
  -H 'x-membership-status: NONE'
)
H_ADMIN=(
  -H 'x-user-id: admin-1'
  -H 'x-user-role: ADMIN'
  -H 'x-gym-id: 22222222-2222-2222-2222-222222222222'
  -H 'x-membership-status: NONE'
)
H_CHECKIN=(
  -H 'x-user-id: checkin-svc'
  -H 'x-user-role: CHECKIN_SERVICE'
  -H 'x-membership-status: NONE'
)
H_NOTIF=(
  -H 'x-user-id: notif-svc'
  -H 'x-user-role: NOTIFICATION_SERVICE'
  -H 'x-membership-status: NONE'
)
```

### Postman gRPC setup (once)

1. Certificates → host `localhost:50051` → `client-postman.crt` + `.key` (or `.p12` / `changeit`); trust `ca.crt`.
2. New gRPC → URL `localhost:50051` (TLS on).
3. Import `member/v1/member.proto` (import path = `gym-proto/proto`).
4. Method: `member.v1.MemberService/<Method>`.
5. **Metadata** tab = claim keys (gRPC has no REST Headers tab for this).
6. **Message** tab = JSON below (camelCase).

Internal: use `client-identifier.p12` for `GetMembershipStatusByUserId`.

Catalog gym/plan RPCs: use **ms-gym-plans**, not Member (G8 removes remnants).

---

## 2. Auth metadata

| Key | Example | Notes |
|-----|---------|--------|
| `x-user-id` | `11111111-1111-1111-1111-111111111111` | required for user RPCs |
| `x-user-role` | `CUSTOMER` | see role map |
| `x-membership-status` | `NONE` | `NONE` \| `ACTIVE` \| `PAUSED` \| `EXPIRED` |
| `x-gym-id` | `22222222-2222-2222-2222-222222222222` | purchase / gym scope; **omit** if empty |

| RPC | Roles / cert |
|-----|----------------|
| GetMember, UpdateProfile | `CUSTOMER` |
| ListMembers | `ADMIN`, `SUPER_ADMIN` |
| PurchaseMembership, Pause, Resume, GetMembershipStatus | `CUSTOMER` |
| ValidateMembership | `CHECKIN_SERVICE` |
| ListMembersByStatus | `NOTIFICATION_SERVICE` |
| GetMembershipStatusByUserId | **identifier** client cert (workload) |

---

## 3. Public RPCs — body + grpcurl

Use real IDs from DB / seed / prior responses. Placeholder member:

`31b6a40a-99d7-4f22-b6f7-f62a11298ba0`

### GetMember

- **Service:** `member.v1.MemberService/GetMember`
- **Metadata:** `x-user-id` (owner), `x-user-role=CUSTOMER`, `x-membership-status=NONE`
- **Postman Message:**

```json
{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"
}
```

```bash
grpcurl "${MTLS[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H_CUST[@]}" \
  -d '{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"
}' \
  localhost:50051 member.v1.MemberService/GetMember
```

---

### UpdateProfile

- **Service:** `member.v1.MemberService/UpdateProfile`
- **Metadata:** `CUSTOMER`
- **Postman Message:**

```json
{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0",
  "fullName": "Nguyen Van A",
  "phone": "+84901234567",
  "avatarUrl": "https://example.com/avatar.jpg",
  "dateOfBirth": "1995-05-15"
}
```

```bash
grpcurl "${MTLS[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H_CUST[@]}" \
  -d '{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0",
  "fullName": "Nguyen Van A",
  "phone": "+84901234567",
  "avatarUrl": "https://example.com/avatar.jpg",
  "dateOfBirth": "1995-05-15"
}' \
  localhost:50051 member.v1.MemberService/UpdateProfile
```

---

### ListMembers

- **Service:** `member.v1.MemberService/ListMembers`
- **Metadata:** `ADMIN` or `SUPER_ADMIN` (+ `x-gym-id` if scoped)
- **Postman Message:**

```json
{
  "page": 0,
  "limit": 10,
  "gymId": "22222222-2222-2222-2222-222222222222"
}
```

```bash
grpcurl "${MTLS[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H_ADMIN[@]}" \
  -d '{
  "page": 0,
  "limit": 10,
  "gymId": "22222222-2222-2222-2222-222222222222"
}' \
  localhost:50051 member.v1.MemberService/ListMembers
```

---

### PurchaseMembership

- **Service:** `member.v1.MemberService/PurchaseMembership`
- **Metadata:** `CUSTOMER` + selected `x-gym-id` (required)
- Needs Plans resolve + Payment (or fail closed locally)
- **Postman Message:**

```json
{
  "planId": "44444444-4444-4444-4444-444444444444",
  "provider": "MOMO",
  "discountCode": ""
}
```

Providers: `MOMO` | `ZALOPAY` | `VNPAY`. Non-blank `discountCode` rejected until promotions exist.

```bash
grpcurl "${MTLS[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H_CUST[@]}" \
  -d '{
  "planId": "44444444-4444-4444-4444-444444444444",
  "provider": "MOMO",
  "discountCode": ""
}' \
  localhost:50051 member.v1.MemberService/PurchaseMembership
```

---

### PauseMembership

- **Service:** `member.v1.MemberService/PauseMembership`
- **Metadata:** `CUSTOMER`
- **Postman Message:**

```json
{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"
}
```

```bash
grpcurl "${MTLS[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H_CUST[@]}" \
  -d '{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"
}' \
  localhost:50051 member.v1.MemberService/PauseMembership
```

---

### ResumeMembership

- **Service:** `member.v1.MemberService/ResumeMembership`
- **Metadata:** `CUSTOMER`
- **Postman Message:**

```json
{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"
}
```

```bash
grpcurl "${MTLS[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H_CUST[@]}" \
  -d '{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"
}' \
  localhost:50051 member.v1.MemberService/ResumeMembership
```

---

### GetMembershipStatus

- **Service:** `member.v1.MemberService/GetMembershipStatus`
- **Metadata:** `CUSTOMER`
- **Postman Message:**

```json
{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"
}
```

```bash
grpcurl "${MTLS[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H_CUST[@]}" \
  -d '{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"
}' \
  localhost:50051 member.v1.MemberService/GetMembershipStatus
```

---

### ValidateMembership

- **Service:** `member.v1.MemberService/ValidateMembership`
- **Metadata:** `x-user-role=CHECKIN_SERVICE`
- **Postman Message:**

```json
{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0",
  "gymId": "22222222-2222-2222-2222-222222222222"
}
```

```bash
grpcurl "${MTLS[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H_CHECKIN[@]}" \
  -d '{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0",
  "gymId": "22222222-2222-2222-2222-222222222222"
}' \
  localhost:50051 member.v1.MemberService/ValidateMembership
```

---

### ListMembersByStatus

- **Service:** `member.v1.MemberService/ListMembersByStatus`
- **Metadata:** `x-user-role=NOTIFICATION_SERVICE`
- **Postman Message:**

```json
{
  "status": "ACTIVE",
  "gymIds": [
    "22222222-2222-2222-2222-222222222222"
  ]
}
```

```bash
grpcurl "${MTLS[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H_NOTIF[@]}" \
  -d '{
  "status": "ACTIVE",
  "gymIds": [
    "22222222-2222-2222-2222-222222222222"
  ]
}' \
  localhost:50051 member.v1.MemberService/ListMembersByStatus
```

---

## 4. Internal RPC — body + grpcurl

### GetMembershipStatusByUserId

- **Service:** `member.v1.MemberService/GetMembershipStatusByUserId`
- **Client cert:** `client-identifier.crt` / `.key` (SAN `ms-gym-identifier`)
- **Metadata:** none as workload proof
- **Postman Message:**

```json
{
  "userId": "11111111-1111-1111-1111-111111111111",
  "gymId": "22222222-2222-2222-2222-222222222222"
}
```

```bash
grpcurl -cacert "$C/ca.crt" -cert "$C/client-identifier.crt" -key "$C/client-identifier.key" \
  -import-path "$PROTO_DIR" -proto member/v1/member.proto \
  -d '{
  "userId": "11111111-1111-1111-1111-111111111111",
  "gymId": "22222222-2222-2222-2222-222222222222"
}' \
  localhost:50051 member.v1.MemberService/GetMembershipStatusByUserId
```

Postman with only postman cert → `PERMISSION_DENIED` (expected).

---

## 5. Sample response shapes

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

---

## 6. What this service needs vs Plans

| Test goal | Service |
|-----------|---------|
| Create gym / plan catalog | **ms-gym-plans** |
| Profile, pause/resume, purchase | **ms-gym-member** |
| Real JWT | Identifier + Kong (not required for claim metadata) |
| Purchase E2E | Member + Plans + Payment + Kafka `payment.completed` |

---

## 7. Common failures

| Symptom | Cause |
|---------|--------|
| `UNAUTHENTICATED` | missing/invalid claim metadata |
| `PERMISSION_DENIED` | wrong role or internal RPC without identifier cert |
| TLS / dial fail | missing client cert or wrong CA |
| Purchase / resolve errors | Plans or Payment not running; non-blank discount |
| Unknown method `GetPlans` / gym RPCs | use **ms-gym-plans** |
| field ignored | try snake_case (`member_id`); parser accepts both |
