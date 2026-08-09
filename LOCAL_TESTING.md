# Member — local testing (gRPC)

Copy-paste bodies for Postman / grpcurl against **current** `member.v1` contract (`gym-proto` **4.1.0**).

> Older `GRPCURL.md` is stale (JWT Bearer, service-role claims, pre-split gym/plan RPCs). Prefer **this file**.

## Trust model

| Caller | Certificate SAN | Claims / scope |
|--------|-----------------|----------------|
| End-user RPCs (via Kong or local Kong cert) | `kong` / SPIFFE `.../sa/kong` | Kong-verified `x-user-*` headers |
| `GetMembershipStatusByUserId` | `ms-gym-identifier` | request `user_id` + `gym_id` only |
| `ValidateMembership` | `ms-gym-checkin` | request `member_id` + `gym_id` only |
| `ListMembersByStatus` | `ms-gym-notification` | request `status` + non-empty `gym_ids` |

Member does **not** accept service roles such as `CHECKIN_SERVICE`. Workload authorization is certificate SAN + exact method. End-user headers are accepted only from Kong SAN.

REST/HTTP Member APIs remain unavailable until a generated gRPC-Gateway is deployed. Kong routes native gRPC public methods only; internal methods never go through Kong.

## 1. Start (default = mTLS)

```bash
cd ms-gym-member
./gradlew startEnv
./gradlew bootRun
```

`bootRun` runs `ensureLocalCerts` if `certs/local/` is incomplete.

| Port | Protocol |
|------|----------|
| `8080` | HTTP actuator/health only |
| `50051` | gRPC **mTLS** (client cert required) |
| `5432` | Postgres `gym_member` |
| `9092` / `8081` | Kafka + Schema Registry |

Stop deps: `./gradlew stopEnv`.

Plaintext override (local unit/integration only):

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
MTLS_KONG=(-cacert "$C/ca.crt" -cert "$C/client-kong.crt" -key "$C/client-kong.key")
MTLS_ID=(-cacert "$C/ca.crt" -cert "$C/client-identifier.crt" -key "$C/client-identifier.key")
MTLS_CHECKIN=(-cacert "$C/ca.crt" -cert "$C/client-checkin.crt" -key "$C/client-checkin.key")
MTLS_NOTIF=(-cacert "$C/ca.crt" -cert "$C/client-notification.crt" -key "$C/client-notification.key")
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
H_SUPER=(
  -H 'x-user-id: super-1'
  -H 'x-user-role: SUPER_ADMIN'
  -H 'x-membership-status: NONE'
)
```

### Postman gRPC setup (once)

1. Certificates → host `localhost:50051` → `client-kong.p12` / `changeit` for end-user RPCs; trust `ca.crt`.
2. New gRPC → URL `localhost:50051` (TLS on).
3. Import `member/v1/member.proto` (import path = `gym-proto/proto`).
4. Method: `member.v1.MemberService/<Method>`.
5. **Metadata** tab = claim keys for end-user RPCs only.
6. **Message** tab = JSON below (camelCase).

Internal methods: switch client cert (`client-identifier` / `client-checkin` / `client-notification`). No `x-user-*` headers required.

Catalog gym/plan RPCs: use **ms-gym-plans**, not Member.

---

## 2. Auth metadata (end-user only)

| Key | Example | Notes |
|-----|---------|--------|
| `x-user-id` | `11111111-1111-1111-1111-111111111111` | required for user RPCs |
| `x-user-role` | `CUSTOMER` | `CUSTOMER` \| `TRAINER` \| `ADMIN` \| `SUPER_ADMIN` |
| `x-membership-status` | `NONE` | `NONE` \| `ACTIVE` \| `PAUSED` \| `EXPIRED` |
| `x-gym-id` | `22222222-2222-2222-2222-222222222222` | purchase / gym scope; **omit** if empty |

| RPC | Trust path |
|-----|------------|
| GetMember, UpdateProfile | Kong cert + `CUSTOMER` |
| ListMembers | Kong cert + `ADMIN`/`SUPER_ADMIN`; non-super requires matching gym |
| PurchaseMembership, Pause, Resume, GetMembershipStatus | Kong cert + `CUSTOMER` |
| ValidateMembership | Check-in cert only |
| ListMembersByStatus | Notification cert only; `gym_ids` min 1 |
| GetMembershipStatusByUserId | Identifier cert only |

---

## 3. Public RPCs — body + grpcurl

Use real IDs from DB / seed / prior responses. Placeholder member:

`31b6a40a-99d7-4f22-b6f7-f62a11298ba0`

### GetMember

- **Service:** `member.v1.MemberService/GetMember`
- **Cert:** Kong
- **Metadata:** `x-user-id` (owner), `x-user-role=CUSTOMER`, `x-membership-status=NONE`
- **Postman Message:**

```json
{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"
}
```

```bash
grpcurl "${MTLS_KONG[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H_CUST[@]}" \
  -d '{"memberId":"31b6a40a-99d7-4f22-b6f7-f62a11298ba0"}' \
  localhost:50051 member.v1.MemberService/GetMember
```

---

### PurchaseMembership

- **Service:** `member.v1.MemberService/PurchaseMembership`
- **Cert:** Kong
- **Metadata:** `CUSTOMER` + selected `x-gym-id` (required)
- **Required:** client `idempotencyKey` (stable across retries; unique per user intent)
- Needs Plans resolve + Payment (or fail closed locally)
- **Postman Message:**

```json
{
  "planId": "44444444-4444-4444-4444-444444444444",
  "provider": "MOMO",
  "discountCode": "",
  "idempotencyKey": "purchase-1111-plan-4444-1"
}
```

Providers: `MOMO` | `ZALOPAY` | `VNPAY`. Non-blank `discountCode` rejected until promotions exist.
Retry with same key reuses the same pending `purchase_id` as Payment `reference_id`.

```bash
grpcurl "${MTLS_KONG[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H_CUST[@]}" \
  -d '{
  "planId": "44444444-4444-4444-4444-444444444444",
  "provider": "MOMO",
  "discountCode": "",
  "idempotencyKey": "purchase-1111-plan-4444-1"
}' \
  localhost:50051 member.v1.MemberService/PurchaseMembership
```

---

### ValidateMembership (Check-in workload)

```bash
grpcurl "${MTLS_CHECKIN[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto \
  -d '{
  "memberId": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0",
  "gymId": "22222222-2222-2222-2222-222222222222"
}' \
  localhost:50051 member.v1.MemberService/ValidateMembership
```

---

### ListMembersByStatus (Notification workload)

```bash
grpcurl "${MTLS_NOTIF[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto \
  -d '{
  "status": "MEMBERSHIP_STATUS_ACTIVE",
  "gymIds": ["22222222-2222-2222-2222-222222222222"]
}' \
  localhost:50051 member.v1.MemberService/ListMembersByStatus
```

---

### GetMembershipStatusByUserId (Identifier workload)

```bash
grpcurl "${MTLS_ID[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto \
  -d '{
  "userId": "11111111-1111-1111-1111-111111111111",
  "gymId": "22222222-2222-2222-2222-222222222222"
}' \
  localhost:50051 member.v1.MemberService/GetMembershipStatusByUserId
```

---

## 4. Negative checks worth running

- Identifier cert + end-user RPC with forged `x-user-*` → `PERMISSION_DENIED` (Kong identity required).
- Check-in cert on `ListMembersByStatus` → denied by method allowlist.
- Blank `gymId` on `ListMembers` as `ADMIN` → forbidden.
- Reuse `idempotencyKey` with different plan/provider → rejected.
