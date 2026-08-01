package com.gym.member.application.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.adapter.out.persistence.entity.GymQRSecretEntity;
import com.gym.member.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.adapter.out.persistence.repository.GymQRSecretJpaRepository;
import com.gym.member.domain.dto.GymDailySecretDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        assertEquals(64, token1.length());
        assertEquals(token1, token2);
    }

    @Test
    void getGymDailySecret_success() {
        String gymId = UUID.randomUUID().toString();
        GymQRSecretEntity entity = new GymQRSecretEntity();
        entity.setGymId(gymId);
        entity.setDailySecret("secret123");

        when(qrSecretRepository.findById(gymId)).thenReturn(Optional.of(entity));

        GymDailySecretDto dto = gymQRService.getGymDailySecret(gymId);

        assertNotNull(dto);
        assertEquals("secret123", dto.dailySecret());
    }

    @Test
    void getGymDailySecret_notFound() {
        String gymId = UUID.randomUUID().toString();
        when(qrSecretRepository.findById(gymId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> gymQRService.getGymDailySecret(gymId));
    }

    @Test
    void rotateAllGymDailySecrets_success() {
        String gymId1 = UUID.randomUUID().toString();
        GymLocationEntity gym1 = new GymLocationEntity();
        gym1.setId(gymId1);

        when(gymLocationRepository.findByStatus("ACTIVE")).thenReturn(List.of(gym1));
        when(qrSecretRepository.findById(gymId1)).thenReturn(Optional.empty());

        gymQRService.rotateAllGymDailySecrets();

        verify(qrSecretRepository, times(1)).save(any());
    }
}
