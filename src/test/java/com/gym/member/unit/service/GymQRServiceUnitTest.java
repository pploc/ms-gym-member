package com.gym.member.unit.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.adapter.out.persistence.entity.GymQRSecretEntity;
import com.gym.member.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.adapter.out.persistence.repository.GymQRSecretJpaRepository;
import com.gym.member.application.service.GymQRService;
import com.gym.member.domain.dto.GymDailySecretDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GymQRServiceUnitTest {

    @Mock
    private GymQRSecretJpaRepository qrSecretRepository;

    @Mock
    private GymLocationJpaRepository gymLocationRepository;

    @InjectMocks
    private GymQRService gymQRService;

    private String gymId;
    private GymQRSecretEntity secretEntity;
    private GymLocationEntity gymLocationEntity;

    @BeforeEach
    void setUp() {
        gymId = UUID.randomUUID().toString();
        secretEntity = new GymQRSecretEntity();
        secretEntity.setGymId(gymId);
        secretEntity.setDailySecret("secret123");
        secretEntity.setUpdatedAt(Instant.now());

        gymLocationEntity = new GymLocationEntity();
        gymLocationEntity.setId(gymId);
        gymLocationEntity.setStatus("ACTIVE");
    }

    @Test
    void getGymDailySecret_success() {
        when(qrSecretRepository.findById(gymId)).thenReturn(Optional.of(secretEntity));

        GymDailySecretDto dto = gymQRService.getGymDailySecret(gymId);

        assertNotNull(dto);
        assertEquals("secret123", dto.dailySecret());
    }

    @Test
    void getGymDailySecret_notFound() {
        when(qrSecretRepository.findById(gymId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> gymQRService.getGymDailySecret(gymId));
    }

    @Test
    void rotateAllGymDailySecrets_success() {
        when(gymLocationRepository.findByStatus("ACTIVE")).thenReturn(List.of(gymLocationEntity));
        when(qrSecretRepository.findById(gymId)).thenReturn(Optional.of(secretEntity));

        gymQRService.rotateAllGymDailySecrets();

        verify(qrSecretRepository, times(1)).save(any());
    }

    @Test
    void computeDailyToken_validFormat() {
        String token = gymQRService.computeDailyToken("mem-1", "secret123", LocalDate.now());

        assertNotNull(token);
        assertEquals(64, token.length());
    }
}
