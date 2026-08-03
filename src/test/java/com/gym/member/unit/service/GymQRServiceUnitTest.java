package com.gym.member.unit.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.location.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.location.adapter.out.persistence.entity.GymQRSecretEntity;
import com.gym.member.location.adapter.out.persistence.mapper.GymQRSecretMapper;
import com.gym.member.location.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.location.adapter.out.persistence.repository.GymQRSecretJpaRepository;
import com.gym.member.location.application.service.GymQRService;
import com.gym.member.location.domain.dto.GymDailySecretDto;
import com.gym.member.location.domain.model.GymLocationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

    @Mock
    private GymQRSecretMapper gymQRSecretMapper;

    private Clock clock;
    private GymQRService gymQRService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneId.of("UTC"));
        gymQRService = new GymQRService(qrSecretRepository, gymLocationRepository, gymQRSecretMapper, clock);
    }

    @Test
    void givenExistingGym_whenGetGymDailySecret_returnsDto() {
        String gymId = UUID.randomUUID().toString();
        GymQRSecretEntity entity = new GymQRSecretEntity();
        entity.setGymId(gymId);
        entity.setDailySecret("secret-123");
        entity.setUpdatedAt(Instant.now(clock));

        GymDailySecretDto dto = new GymDailySecretDto(UUID.fromString(gymId), "secret-123");

        when(qrSecretRepository.findById(gymId)).thenReturn(Optional.of(entity));
        when(gymQRSecretMapper.toDto(entity)).thenReturn(dto);

        GymDailySecretDto result = gymQRService.getGymDailySecret(gymId);
        assertNotNull(result);
        assertEquals("secret-123", result.dailySecret());
    }

    @Test
    void givenNonExistentGym_whenGetGymDailySecret_throwsNotFoundException() {
        String gymId = UUID.randomUUID().toString();
        when(qrSecretRepository.findById(gymId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> gymQRService.getGymDailySecret(gymId));
    }

    @Test
    void whenRotateAllGymDailySecrets_updatesExistingAndNewEntities() {
        String gymId1 = UUID.randomUUID().toString();
        String gymId2 = UUID.randomUUID().toString();

        GymLocationEntity loc1 = new GymLocationEntity();
        loc1.setId(gymId1);
        loc1.setStatus(GymLocationStatus.ACTIVE);

        GymLocationEntity loc2 = new GymLocationEntity();
        loc2.setId(gymId2);
        loc2.setStatus(GymLocationStatus.ACTIVE);

        GymQRSecretEntity entity1 = new GymQRSecretEntity();
        entity1.setGymId(gymId1);
        entity1.setDailySecret("old-secret");

        when(gymLocationRepository.findByStatus(GymLocationStatus.ACTIVE)).thenReturn(List.of(loc1, loc2));
        when(qrSecretRepository.findById(gymId1)).thenReturn(Optional.of(entity1));
        when(qrSecretRepository.findById(gymId2)).thenReturn(Optional.empty());

        gymQRService.rotateAllGymDailySecrets();

        verify(qrSecretRepository, times(2)).save(any(GymQRSecretEntity.class));
    }

    @Test
    void testComputeDailyToken() {
        String token = gymQRService.computeDailyToken("gym-1", "secret-123", LocalDate.of(2026, 8, 3));
        assertNotNull(token);
        assertFalse(token.isBlank());
    }
}
