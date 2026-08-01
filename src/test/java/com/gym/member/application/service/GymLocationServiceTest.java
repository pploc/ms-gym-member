package com.gym.member.application.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.adapter.out.persistence.entity.MembershipPlanEntity;
import com.gym.member.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.adapter.out.persistence.repository.GymQRSecretJpaRepository;
import com.gym.member.adapter.out.persistence.repository.MembershipPlanJpaRepository;
import com.gym.member.domain.dto.GymLocationDto;
import com.gym.member.domain.dto.PlanDto;
import com.gym.member.domain.model.PlanType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GymLocationServiceTest {

    @Mock
    private GymLocationJpaRepository gymLocationRepository;

    @Mock
    private GymQRSecretJpaRepository qrSecretRepository;

    @Mock
    private MembershipPlanJpaRepository planRepository;

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
        location.setName("Downtown Gym");
        location.setAddress("123 Main St");
        location.setCity("Saigon");
        location.setStatus("ACTIVE");
    }

    @Test
    void createGymLocation_success() {
        when(gymLocationRepository.save(any())).thenAnswer(inv -> {
            GymLocationEntity entity = inv.getArgument(0);
            entity.setId(gymId);
            return entity;
        });

        GymLocationDto dto = gymLocationService.createGymLocation(chainId, "Downtown Gym", "123 Main St", "Saigon");

        assertNotNull(dto);
        assertEquals("Downtown Gym", dto.name());
        verify(qrSecretRepository, times(1)).save(any());
    }

    @Test
    void updateGymLocation_success() {
        when(gymLocationRepository.findById(gymId)).thenReturn(Optional.of(location));
        when(gymLocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GymLocationDto dto = gymLocationService.updateGymLocation(gymId, "Updated Gym", "New St", "Danang", "INACTIVE");

        assertNotNull(dto);
        assertEquals("Updated Gym", dto.name());
        assertEquals("New St", dto.address());
        assertEquals("Danang", dto.city());
        assertEquals("INACTIVE", dto.status());
    }

    @Test
    void updateGymLocation_notFound() {
        when(gymLocationRepository.findById(gymId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> gymLocationService.updateGymLocation(gymId, "Updated", null, null, null));
    }

    @Test
    void getGymLocation_success() {
        when(gymLocationRepository.findById(gymId)).thenReturn(Optional.of(location));

        GymLocationDto dto = gymLocationService.getGymLocation(gymId);

        assertNotNull(dto);
        assertEquals(gymId, dto.id().toString());
    }

    @Test
    void getGymLocation_notFound() {
        when(gymLocationRepository.findById(gymId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> gymLocationService.getGymLocation(gymId));
    }

    @Test
    void listGymLocations_withChainId() {
        when(gymLocationRepository.findByChainId(chainId)).thenReturn(List.of(location));

        List<GymLocationDto> list = gymLocationService.listGymLocations(chainId);

        assertNotNull(list);
        assertEquals(1, list.size());
    }

    @Test
    void listGymLocations_withoutChainId() {
        when(gymLocationRepository.findAll()).thenReturn(List.of(location));

        List<GymLocationDto> list = gymLocationService.listGymLocations(null);

        assertNotNull(list);
        assertEquals(1, list.size());
    }

    @Test
    void getPlans_withGymId() {
        MembershipPlanEntity plan = new MembershipPlanEntity();
        plan.setId(UUID.randomUUID().toString());
        plan.setGymId(gymId);
        plan.setName("Monthly Pass");
        plan.setPlanType(PlanType.MONTHLY);
        plan.setDurationDays(30);
        plan.setPriceVnd(500000L);
        plan.setActive(true);

        when(planRepository.findByGymIdAndActiveTrue(gymId)).thenReturn(List.of(plan));

        List<PlanDto> plans = gymLocationService.getPlans(gymId);

        assertNotNull(plans);
        assertEquals(1, plans.size());
        assertEquals("Monthly Pass", plans.get(0).name());
    }

    @Test
    void getPlans_withoutGymId() {
        MembershipPlanEntity plan = new MembershipPlanEntity();
        plan.setId(UUID.randomUUID().toString());
        plan.setGymId(gymId);
        plan.setName("Monthly Pass");
        plan.setPlanType(PlanType.MONTHLY);
        plan.setDurationDays(30);
        plan.setPriceVnd(500000L);
        plan.setActive(true);

        when(planRepository.findAll()).thenReturn(List.of(plan));

        List<PlanDto> plans = gymLocationService.getPlans(null);

        assertNotNull(plans);
        assertEquals(1, plans.size());
    }
}
