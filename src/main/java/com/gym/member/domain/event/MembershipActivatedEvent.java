package com.gym.member.domain.event;

import com.gym.member.domain.model.PlanType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MembershipActivatedEvent(
        UUID memberId,
        UUID userId,
        PlanType planType,
        LocalDate startDate,
        LocalDate endDate,
        UUID gymId,
        boolean isRenewal,
        Instant timestamp
) {}
