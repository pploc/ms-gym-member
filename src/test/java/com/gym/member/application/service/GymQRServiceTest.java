package com.gym.member.application.service;

import com.gym.member.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.adapter.out.persistence.repository.GymQRSecretJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class GymQRServiceTest {

    @Mock
    private GymQRSecretJpaRepository qrSecretRepository;

    @Mock
    private GymLocationJpaRepository gymLocationRepository;

    @InjectMocks
    private GymQRService gymQRService;

    @Test
    void computeDailyToken_deterministicResult() {
        String gymId = UUID.randomUUID().toString();
        String dailySecret = "secret1234567890";
        LocalDate date = LocalDate.of(2026, 8, 1);

        String token1 = gymQRService.computeDailyToken(gymId, dailySecret, date);
        String token2 = gymQRService.computeDailyToken(gymId, dailySecret, date);

        assertNotNull(token1);
        assertEquals(64, token1.length()); // SHA-256 hex string length
        assertEquals(token1, token2);
    }
}
