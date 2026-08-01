package com.gym.member.unit.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.adapter.out.persistence.entity.GymQRSecretEntity;
import com.gym.member.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.adapter.out.persistence.repository.GymQRSecretJpaRepository;
import com.gym.member.application.service.GymQRService;
import com.gym.member.domain.dto.GymDailySecretDto;
import com.gym.member.mapper.GymQRSecretMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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

    @Spy
    private GymQRSecretMapper gymQRSecretMapper = Mappers.getMapper(GymQRSecretMapper.class);

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
    void givenExistingGymSecret_whenGetGymDailySecret_thenReturnsDailySecretDto() {
        // Given
        when(qrSecretRepository.findById(gymId)).thenReturn(Optional.of(secretEntity));

        // When
        GymDailySecretDto dto = gymQRService.getGymDailySecret(gymId);

        // Then
        assertNotNull(dto);
        assertEquals("secret123", dto.dailySecret());
    }

    @Test
    void givenMissingGymSecret_whenGetGymDailySecret_thenThrowsNotFoundException() {
        // Given
        when(qrSecretRepository.findById(gymId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> gymQRService.getGymDailySecret(gymId));
    }

    @Test
    void givenActiveGymLocations_whenRotateAllGymDailySecrets_thenSavesRotatedSecrets() {
        // Given
        when(gymLocationRepository.findByStatus("ACTIVE")).thenReturn(List.of(gymLocationEntity));
        when(qrSecretRepository.findById(gymId)).thenReturn(Optional.of(secretEntity));

        // When
        gymQRService.rotateAllGymDailySecrets();

        // Then
        verify(qrSecretRepository, times(1)).save(any());
    }

    @Test
    void givenMemberSecretAndDate_whenComputeDailyToken_thenReturnsSha256TokenString() {
        // Given

        // When
        String token = gymQRService.computeDailyToken("mem-1", "secret123", LocalDate.now());

        // Then
        assertNotNull(token);
        assertEquals(64, token.length());
    }
}
