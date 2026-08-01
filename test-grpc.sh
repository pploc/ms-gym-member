#!/usr/bin/env bash

# Local test JWT token (no newline breaks)
TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJyb2xlIjoiQURNSU4iLCJneW1faWQiOiIyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MjAwMDAwMDAwMH0.signature"

echo "Sending gRPC request ListMembers to 127.0.0.1:50051..."
grpcurl -plaintext \
  -H "authorization: Bearer ${TOKEN}" \
  -d '{"gym_id": "22222222-2222-2222-2222-222222222222", "page": 0, "limit": 10}' \
  127.0.0.1:50051 \
  member.v1.MemberService/ListMembers
