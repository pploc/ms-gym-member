# Deprecated

This file is obsolete. It used plaintext gRPC, forged Bearer JWTs, and service-role claims (`CHECKIN_SERVICE` / `NOTIFICATION_SERVICE`) that Member no longer accepts.

Use **[LOCAL_TESTING.md](./LOCAL_TESTING.md)** instead:

- mTLS client certs by caller (Kong / Identifier / Check-in / Notification)
- Kong-injected `x-user-*` headers for end-user RPCs only
- required `idempotencyKey` on purchase
- no JWT verification inside Member
