# gRPC Testing Commands (`grpcurl`) for `ms-gym-member`

This document provides standalone, direct `grpcurl` commands for remaining `member.v1.MemberService` RPCs after Phase 8 (catalog/location RPCs live on ms-gym-plans). Each command contains literal values and can be copied and executed directly in your terminal without setting shell environment variables.

---

## 1. General Commands

### Discover Services (Reflection)
```bash
grpcurl -plaintext 172.20.76.101:50051 list
```

### Describe MemberService Definition
```bash
grpcurl -plaintext 172.20.76.101:50051 describe member.v1.MemberService
```

---

## 2. Member Management

### 1. `GetMember`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"member_id": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"}' \
  172.20.76.101:50051 member.v1.MemberService/GetMember
```

### 2. `ListMembers`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"gym_id": "22222222-2222-2222-2222-222222222222", "page": 0, "limit": 10}' \
  172.20.76.101:50051 member.v1.MemberService/ListMembers
```

### 3. `UpdateProfile`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"member_id": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0", "full_name": "John Doe", "phone": "+84901234567", "avatar_url": "https://example.com/avatar.jpg", "date_of_birth": "1995-05-15"}' \
  172.20.76.101:50051 member.v1.MemberService/UpdateProfile
```

---

## 3. Subscription Management

### 4. `PurchaseMembership`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQ1VTVE9NRVIiLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"plan_id": "44444444-4444-4444-4444-444444444444", "provider": "MOMO"}' \
  172.20.76.101:50051 member.v1.MemberService/PurchaseMembership
```

Catalog/plan listing and gym location RPCs moved to **ms-gym-plans**. Leave `discount_code` blank; nonblank codes are rejected until authoritative discount pricing exists.

### 5. `PauseMembership`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQ1VTVE9NRVIiLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"member_id": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"}' \
  172.20.76.101:50051 member.v1.MemberService/PauseMembership
```

### 6. `ResumeMembership`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQ1VTVE9NRVIiLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"member_id": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"}' \
  172.20.76.101:50051 member.v1.MemberService/ResumeMembership
```

### 7. `GetMembershipStatus`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQ1VTVE9NRVIiLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"member_id": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"}' \
  172.20.76.101:50051 member.v1.MemberService/GetMembershipStatus
```

---

## 4. Check-in / Notification Integration (Internal APIs)

### 8. `ValidateMembership`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQ0hFQ0tJTl9TRVJWSUNFIiwgImd5bV9pZCI6IjIyMjIyMjIyLTIyMjItMjIyMi0yMjIyLTIyMjIyMjIyMjIyMiIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoyMDAwMDAwMDAwfQ.signature" \
  -d '{"member_id": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0", "gym_id": "22222222-2222-2222-2222-222222222222"}' \
  172.20.76.101:50051 member.v1.MemberService/ValidateMembership
```

### 9. `ListMembersByStatus`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiTk9USUZJQ0FUSU9OX1NFUlZJQ0UiLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"status": "ACTIVE", "gym_ids": ["22222222-2222-2222-2222-222222222222"]}' \
  172.20.76.101:50051 member.v1.MemberService/ListMembersByStatus
```

---

## 5. Using Local `.proto` File (Offline / No Reflection)

If Server Reflection is disabled, supply the `-import-path` and `-proto` arguments directly:

```bash
grpcurl -plaintext \
  -import-path ../gym-proto/proto \
  -proto ../gym-proto/proto/member/v1/member.proto \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"member_id": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"}' \
  172.20.76.101:50051 member.v1.MemberService/GetMember
```
