package com.gym.member.domain.event;

import com.gym.member.domain.model.PlanType;
import java.time.LocalDate;
import java.util.UUID;

public record MembershipExpiringSoonEvent(
        UUID memberId,
        LocalDate endDate,
        PlanType planType,
        UUID gymId
) {}
