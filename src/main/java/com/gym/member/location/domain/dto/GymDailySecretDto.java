package com.gym.member.location.domain.dto;

import java.util.UUID;

public record GymDailySecretDto(
        UUID gymId,
        String dailySecret
) {}
