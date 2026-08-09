#!/usr/bin/env sh
# Local mTLS material for Member gRPC (Postman / grpcurl). Do not use in prod.
set -eu

out=${1:-"$(cd "$(dirname "$0")/.." && pwd)/certs/local"}
days=${CERT_DAYS:-365}
pass=${P12_PASSWORD:-changeit}

rm -rf "$out"
mkdir -p "$out"
trap 'rm -f "$out"/*.csr "$out"/*.ext "$out"/*.srl' EXIT

openssl req -x509 -newkey rsa:2048 -nodes -days "$days" \
  -keyout "$out/ca.key" \
  -out "$out/ca.crt" \
  -subj '/CN=gym-member-local-ca' >/dev/null 2>&1

issue() {
  name=$1
  san=$2
  usage=$3
  openssl req -newkey rsa:2048 -nodes \
    -keyout "$out/$name.key" \
    -out "$out/$name.csr" \
    -subj "/CN=$name" >/dev/null 2>&1
  printf 'subjectAltName=%s\nextendedKeyUsage=%s\nbasicConstraints=CA:FALSE\n' \
    "$san" "$usage" >"$out/$name.ext"
  openssl x509 -req -days "$days" \
    -in "$out/$name.csr" \
    -CA "$out/ca.crt" \
    -CAkey "$out/ca.key" \
    -CAcreateserial \
    -out "$out/$name.crt" \
    -extfile "$out/$name.ext" >/dev/null 2>&1
}

issue server 'DNS:localhost,DNS:ms-gym-member,IP:127.0.0.1' serverAuth
issue client-kong 'DNS:kong,URI:spiffe://gym.cluster.local/ns/gym-system/sa/kong' clientAuth
issue client-identifier 'DNS:ms-gym-identifier,URI:spiffe://gym.cluster.local/ns/gym-system/sa/ms-gym-identifier' clientAuth
issue client-checkin 'DNS:ms-gym-checkin,URI:spiffe://gym.cluster.local/ns/gym-system/sa/ms-gym-checkin' clientAuth
issue client-notification 'DNS:ms-gym-notification,URI:spiffe://gym.cluster.local/ns/gym-system/sa/ms-gym-notification' clientAuth

p12() {
  name=$1
  openssl pkcs12 -export \
    -out "$out/$name.p12" \
    -inkey "$out/$name.key" \
    -in "$out/$name.crt" \
    -certfile "$out/ca.crt" \
    -password "pass:$pass" \
    -name "$name" >/dev/null 2>&1
}

p12 client-kong
p12 client-identifier
p12 client-checkin
p12 client-notification

chmod 600 "$out"/*.key "$out"/*.p12

cat >"$out/README.txt" <<EOF
Member local mTLS (scripts/generate-local-certs.sh)
P12 password: $pass

Server env (or rely on application.yml defaults + bootRun ensureLocalCerts):
  export MEMBER_GRPC_TLS_ENABLED=true
  export MEMBER_GRPC_ALLOW_PLAINTEXT=false
  export MEMBER_GRPC_SERVER_CERT=$out/server.crt
  export MEMBER_GRPC_SERVER_KEY=$out/server.key
  export MEMBER_GRPC_CLIENT_CA=$out/ca.crt

End-user RPCs: client-kong.p12 + Kong-verified x-user-* metadata
GetMembershipStatusByUserId: client-identifier.p12
ValidateMembership: client-checkin.p12
ListMembersByStatus: client-notification.p12

grpcurl end-user example:
  grpcurl -cacert $out/ca.crt \\
    -cert $out/client-kong.crt -key $out/client-kong.key \\
    -H 'x-user-id: u1' -H 'x-user-role: CUSTOMER' -H 'x-membership-status: NONE' \\
    -d '{"memberId":"..."}' localhost:50051 member.v1.MemberService/GetMember
EOF

echo "Wrote certs under $out"
ls -la "$out"
