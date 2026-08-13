#!/usr/bin/env bash
# Smoke ListMembers via generated gateway mTLS identity.
# Prefer LOCAL_TESTING.md for full matrix.
set -euo pipefail
cd "$(dirname "$0")"

PROTO_DIR="${PROTO_DIR:-../gym-proto/proto}"
C="${CERT_DIR:-certs/local}"
: "${MEMBER_HOST:=localhost:50051}"
: "${USER_ID:=11111111-1111-1111-1111-111111111111}"
: "${GYM_ID:=22222222-2222-2222-2222-222222222222}"

if [[ ! -f "$C/client-gateway.crt" ]]; then
  echo "missing $C/client-gateway.crt — run ./gradlew ensureLocalCerts or bootRun first" >&2
  exit 1
fi

echo "ListMembers as SUPER_ADMIN via gateway cert → $MEMBER_HOST"
grpcurl \
  -cacert "$C/ca.crt" -cert "$C/client-gateway.crt" -key "$C/client-gateway.key" \
  -import-path "$PROTO_DIR" -proto member/v1/member.proto \
  -H "x-user-id: super-1" \
  -H "x-user-role: SUPER_ADMIN" \
  -d "{\"gymId\":\"$GYM_ID\",\"page\":0,\"limit\":10}" \
  "$MEMBER_HOST" member.v1.MemberService/ListMembers
