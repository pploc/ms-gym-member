# Member — local testing (gRPC)

Copy-paste examples for `gym-proto` `7.0.2`.

## Trust model

| Caller | Certificate SAN | Allowed methods |
|---|---|---|
| End user through generated gateway | `ms-gym-api-gateway` / SPIFFE `.../sa/ms-gym-api-gateway` | Public Member methods with gateway-forwarded `x-user-id` and `x-user-role` |
| Check-in | `ms-gym-checkin` | `ValidateMembership` only |
| Notification | `ms-gym-notification` | `ListMembersByStatus` only |

Identifier has no Member client, certificate, or method allowlist. Internal methods are not Kong-routed.

## Start

```bash
cd ms-gym-member
./gradlew startEnv
./gradlew bootRun
```

`bootRun` generates missing local certificates under `certs/local/`. Stop dependencies with `./gradlew stopEnv`.

```bash
PROTO_DIR=../gym-proto/proto
C=certs/local
MTLS_GATEWAY=(-cacert "$C/ca.crt" -cert "$C/client-gateway.crt" -key "$C/client-gateway.key")
MTLS_CHECKIN=(-cacert "$C/ca.crt" -cert "$C/client-checkin.crt" -key "$C/client-checkin.key")
MTLS_NOTIF=(-cacert "$C/ca.crt" -cert "$C/client-notification.crt" -key "$C/client-notification.key")
H_CUSTOMER=(-H 'x-user-id: 11111111-1111-1111-1111-111111111111' -H 'x-user-role: CUSTOMER')
H_SUPER=(-H 'x-user-id: super-1' -H 'x-user-role: SUPER_ADMIN')
```

Gym context comes from request fields, never trusted headers.

## Public examples

### ListMembers — `SUPER_ADMIN` only

```bash
grpcurl "${MTLS_GATEWAY[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H_SUPER[@]}" \
  -d '{"gymId":"22222222-2222-2222-2222-222222222222","page":0,"limit":10}' \
  localhost:50051 member.v1.MemberService/ListMembers
```

### PurchaseMembership — customer self only

```bash
grpcurl "${MTLS_GATEWAY[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H_CUSTOMER[@]}" \
  -d '{
    "gymId":"22222222-2222-2222-2222-222222222222",
    "purchase":{
      "planId":"44444444-4444-4444-4444-444444444444",
      "provider":"MOMO",
      "discountCode":"",
      "idempotencyKey":"purchase-1111-plan-4444-1"
    }
  }' \
  localhost:50051 member.v1.MemberService/PurchaseMembership
```

### GetMembershipStatus — customer self or `SUPER_ADMIN`

```bash
grpcurl "${MTLS_GATEWAY[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto "${H_CUSTOMER[@]}" \
  -d '{
    "gymId":"22222222-2222-2222-2222-222222222222",
    "memberId":"31b6a40a-99d7-4f22-b6f7-f62a11298ba0"
  }' \
  localhost:50051 member.v1.MemberService/GetMembershipStatus
```

## Internal examples

### ValidateMembership — Check-in only

```bash
grpcurl "${MTLS_CHECKIN[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto \
  -d '{"userId":"11111111-1111-1111-1111-111111111111","gymId":"22222222-2222-2222-2222-222222222222"}' \
  localhost:50051 member.v1.MemberService/ValidateMembership

Check-in sends no `x-user-*` metadata. Response returns canonical persisted `memberId`; `valid` is true only for `ACTIVE`.
```

### ListMembersByStatus — Notification only

```bash
grpcurl "${MTLS_NOTIF[@]}" -import-path "$PROTO_DIR" -proto member/v1/member.proto \
  -d '{"status":"MEMBERSHIP_STATUS_ACTIVE","gymIds":["22222222-2222-2222-2222-222222222222"]}' \
  localhost:50051 member.v1.MemberService/ListMembersByStatus
```

## Negative checks

- `ADMIN` calling `ListMembers` is denied.
- Missing `gymId` on gym-specific public requests is invalid.
- Customer access to another user's member is denied.
- Check-in certificate on `ListMembersByStatus` is denied.
- Reusing an idempotency key with a different plan or provider is rejected.
