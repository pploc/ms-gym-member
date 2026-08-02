package com.gym.member.location.application.port.in;

import com.gym.member.location.domain.dto.GymDailySecretDto;

import java.time.LocalDate;

public interface GymQRUseCase {
    GymDailySecretDto getGymDailySecret(String gymId);
    void rotateAllGymDailySecrets();
    String computeDailyToken(String gymId, String dailySecret, LocalDate date);
}
