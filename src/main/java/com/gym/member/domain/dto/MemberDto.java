package com.gym.member.domain.dto;

import com.gym.member.domain.model.MembershipStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MemberDto(
        UUID id,
        UUID userId,
        UUID gymId,
        String fullName,
        String phone,
        String avatarUrl,
        LocalDate dateOfBirth,
        MembershipStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
