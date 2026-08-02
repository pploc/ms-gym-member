package com.gym.member.unit.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.location.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.location.domain.model.GymLocationStatus;
import com.gym.member.member.adapter.out.persistence.entity.MembershipPlanEntity;
import com.gym.member.location.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.location.adapter.out.persistence.repository.GymQRSecretJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.MembershipPlanJpaRepository;
import com.gym.member.location.application.service.GymLocationService;
import com.gym.member.location.domain.dto.GymLocationDto;
import com.gym.member.member.domain.dto.PlanDto;
import com.gym.member.location.adapter.out.persistence.mapper.GymLocationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GymLocationServiceUnitTest {

    @Mock
    private GymLocationJpaRepository gymLocationRepository;

    @Mock
    private GymQRSecretJpaRepository qrSecretRepository;

    @Mock
    private MembershipPlanJpaRepository planRepository;

    @Spy
    private GymLocationMapper gymLocationMapper = Mappers.getMapper(GymLocationMapper.class);

    @Spy
    private java.time.Clock clock = java.time.Clock.systemUTC();

    @InjectMocks
    private GymLocationService gymLocationService;

    private String gymId;
    private String chainId;
    private GymLocationEntity location;

    @BeforeEach
    void setUp() {
        gymId = UUID.randomUUID().toString();
        chainId = UUID.randomUUID().toString();

        location = new GymLocationEntity();
        location.setId(gymId);
        location.setChainId(chainId);
        location.setName("Gym A");
        location.setAddress("Street 1");
        location.setCity("Hanoi");
        location.setStatus(GymLocationStatus.ACTIVE);
    }

    @Test
    void givenValidGymDetails_whenCreateGymLocation_thenCreatesLocationAndGeneratesQRSecret() {
        // Given
        when(gymLocationRepository.save(any())).thenAnswer(inv -> {
            GymLocationEntity e = inv.getArgument(0);
            e.setId(gymId);
            return e;
        });

        // When
        GymLocationDto dto = gymLocationService.createGymLocation(chainId, "Central Gym", "123 Main St", "Metropolis");

        // Then
        assertNotNull(dto);
        assertEquals("Central Gym", dto.name());
        verify(qrSecretRepository, times(1)).save(any());
    }

    @Test
    void givenExistingGymLocation_whenUpdateGymLocation_thenUpdatesAndReturnsDto() {
        // Given
        when(gymLocationRepository.findById(gymId)).thenReturn(Optional.of(location));
        when(gymLocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        GymLocationDto dto = gymLocationService.updateGymLocation(gymId, "Updated Gym", "456 St", "City", "INACTIVE");

        // Then
        assertNotNull(dto);
        assertEquals("Updated Gym", dto.name());
        assertEquals("INACTIVE", dto.status());
    }

    @Test
    void givenMissingGymLocation_whenUpdateGymLocation_thenThrowsNotFoundException() {
        // Given
        when(gymLocationRepository.findById(gymId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> gymLocationService.updateGymLocation(gymId, "Name", null, null, null));
    }

    @Test
    void givenExistingGymLocation_whenGetGymLocation_thenReturnsGymLocationDto() {
        // Given
        when(gymLocationRepository.findById(gymId)).thenReturn(Optional.of(location));

        // When
        GymLocationDto dto = gymLocationService.getGymLocation(gymId);

        // Then
        assertNotNull(dto);
        assertEquals(gymId, dto.id().toString());
    }

    @Test
    void givenChainId_whenListGymLocations_thenReturnsLocationsList() {
        // Given
        when(gymLocationRepository.findByChainId(chainId)).thenReturn(List.of(location));

        // When
        List<GymLocationDto> list = gymLocationService.listGymLocations(chainId);

        // Then
        assertNotNull(list);
        assertEquals(1, list.size());
    }

    @Test
    void givenGymId_whenGetPlans_thenReturnsActivePlansList() {
        // Given
        MembershipPlanEntity plan = new MembershipPlanEntity();
        plan.setId(UUID.randomUUID().toString());
        plan.setGymId(gymId);
        when(planRepository.findByGymIdAndActiveTrue(gymId)).thenReturn(List.of(plan));

        // When
        List<PlanDto> list = gymLocationService.getPlans(gymId);

        // Then
        assertNotNull(list);
        assertEquals(1, list.size());
    }
}
