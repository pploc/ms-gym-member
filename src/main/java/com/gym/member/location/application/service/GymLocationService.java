package com.gym.member.location.application.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.location.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.member.adapter.out.persistence.entity.MembershipPlanEntity;
import com.gym.member.location.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.MembershipPlanJpaRepository;
import com.gym.member.location.domain.dto.GymLocationDto;
import com.gym.member.member.domain.dto.PlanDto;
import com.gym.member.location.domain.model.GymLocationStatus;
import com.gym.member.location.adapter.out.persistence.mapper.GymLocationMapper;
import com.gym.member.location.application.port.in.GymLocationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GymLocationService implements GymLocationUseCase {

    private final GymLocationJpaRepository gymLocationRepository;
    private final MembershipPlanJpaRepository planRepository;
    private final GymLocationMapper gymLocationMapper;

    @Transactional
    public GymLocationDto createGymLocation(String chainId, String name, String address, String city) {
        GymLocationEntity entity = new GymLocationEntity();
        entity.setChainId(chainId);
        entity.setName(name);
        entity.setAddress(address);
        entity.setCity(city);
        entity.setStatus(GymLocationStatus.ACTIVE);

        GymLocationEntity saved = gymLocationRepository.save(entity);
        return gymLocationMapper.toDto(saved);
    }

    @Transactional
    public GymLocationDto updateGymLocation(String id, String name, String address, String city, String status) {
        GymLocationEntity entity = gymLocationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Gym location not found: " + id));

        if (name != null && !name.isBlank()) entity.setName(name);
        if (address != null && !address.isBlank()) entity.setAddress(address);
        if (city != null && !city.isBlank()) entity.setCity(city);
        if (status != null && !status.isBlank()) {
            entity.setStatus(GymLocationStatus.valueOf(status.toUpperCase()));
        }

        GymLocationEntity saved = gymLocationRepository.save(entity);
        return gymLocationMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public GymLocationDto getGymLocation(String id) {
        GymLocationEntity entity = gymLocationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Gym location not found: " + id));
        return gymLocationMapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<GymLocationDto> listGymLocations(String chainId) {
        List<GymLocationEntity> entities = (chainId != null && !chainId.isBlank())
                ? gymLocationRepository.findByChainId(chainId)
                : gymLocationRepository.findAll();
        return entities.stream().map(gymLocationMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PlanDto> getPlans(String gymId) {
        List<MembershipPlanEntity> plans = (gymId != null && !gymId.isBlank())
                ? planRepository.findByGymIdAndActiveTrue(gymId)
                : planRepository.findAll();
        return plans.stream().map(gymLocationMapper::toPlanDto).toList();
    }
}
