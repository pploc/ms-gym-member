package com.gym.member.member.domain.dto;

import com.gym.member.member.domain.model.PlanType;

public record PurchasedPlanTerms(
        String planId,
        String gymId,
        PlanType planType,
        Integer durationDays,
        long priceVnd
) {}
