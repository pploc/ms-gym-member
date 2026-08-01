package com.gym.member.domain.dto;

import java.util.UUID;

public record GymDailySecretDto(
        UUID gymId,
        String dailySecret
) {}
