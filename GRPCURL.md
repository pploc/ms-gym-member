# gRPC Testing Commands (`grpcurl`) for `ms-gym-member`

This document provides standalone, direct `grpcurl` commands for all 15 RPC endpoints of `member.v1.MemberService`. Each command contains literal values and can be copied and executed directly in your terminal without setting shell environment variables.

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

### 4. `GetPlans`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"gym_id": "22222222-2222-2222-2222-222222222222"}' \
  172.20.76.101:50051 member.v1.MemberService/GetPlans
```

### 5. `PurchaseMembership`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"plan_id": "44444444-4444-4444-4444-444444444444", "provider": "MOMO", "discount_code": "WELCOME10"}' \
  172.20.76.101:50051 member.v1.MemberService/PurchaseMembership
```

### 6. `PauseMembership`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"member_id": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"}' \
  172.20.76.101:50051 member.v1.MemberService/PauseMembership
```

### 7. `ResumeMembership`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"member_id": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"}' \
  172.20.76.101:50051 member.v1.MemberService/ResumeMembership
```

### 8. `GetMembershipStatus`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"member_id": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"}' \
  172.20.76.101:50051 member.v1.MemberService/GetMembershipStatus
```

---

## 4. Gym Location Management

### 9. `CreateGymLocation`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"chain_id": "33333333-3333-3333-3333-333333333333", "name": "FitZone Q1", "address": "123 Le Loi", "city": "HCM"}' \
  172.20.76.101:50051 member.v1.MemberService/CreateGymLocation
```

### 10. `GetGymLocation`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"id": "22222222-2222-2222-2222-222222222222"}' \
  172.20.76.101:50051 member.v1.MemberService/GetGymLocation
```

### 11. `ListGymLocations`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"chain_id": "33333333-3333-3333-3333-333333333333"}' \
  172.20.76.101:50051 member.v1.MemberService/ListGymLocations
```

### 12. `UpdateGymLocation`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"id": "22222222-2222-2222-2222-222222222222", "name": "FitZone Q1 Updated", "address": "123 Le Loi", "city": "HCM", "status": "ACTIVE"}' \
  172.20.76.101:50051 member.v1.MemberService/UpdateGymLocation
```

---

## 5. Check-in Gateway Integration (Internal APIs)

### 13. `GetGymDailySecret`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"gym_id": "22222222-2222-2222-2222-222222222222"}' \
  172.20.76.101:50051 member.v1.MemberService/GetGymDailySecret
```

### 14. `ValidateMembership`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"member_id": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0", "gym_id": "22222222-2222-2222-2222-222222222222"}' \
  172.20.76.101:50051 member.v1.MemberService/ValidateMembership
```

### 15. `ListMembersByStatus`
```bash
grpcurl -plaintext \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"status": "ACTIVE", "gym_ids": ["22222222-2222-2222-2222-222222222222"]}' \
  172.20.76.101:50051 member.v1.MemberService/ListMembersByStatus
```

---

## 6. Using Local `.proto` File (Offline / No Reflection)

If Server Reflection is disabled, supply the `-import-path` and `-proto` arguments directly:

```bash
grpcurl -plaintext \
  -import-path ../gym-proto/proto \
  -proto ../gym-proto/proto/member/v1/member.proto \
  -H "authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature" \
  -d '{"member_id": "31b6a40a-99d7-4f22-b6f7-f62a11298ba0"}' \
  172.20.76.101:50051 member.v1.MemberService/GetMember
```
