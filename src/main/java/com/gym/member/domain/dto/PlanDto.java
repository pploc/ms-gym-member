package com.gym.member.domain.dto;

import com.gym.member.domain.model.PlanType;
import java.util.UUID;

public record PlanDto(
        UUID id,
        UUID gymId,
        String name,
        PlanType planType,
        Integer durationDays,
        long priceVnd,
        String description,
        boolean active
) {}
