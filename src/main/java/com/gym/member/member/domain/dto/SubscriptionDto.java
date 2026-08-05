package com.gym.member.member.domain.dto;

import com.gym.member.member.domain.model.MembershipStatus;
import java.time.LocalDate;
import java.util.UUID;

public record SubscriptionDto(
        UUID id,
        UUID memberId,
        UUID gymId,
        UUID planId,
        MembershipStatus status,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate pausedAt,
        Integer remainingDays,
        int pauseCount
) {}
