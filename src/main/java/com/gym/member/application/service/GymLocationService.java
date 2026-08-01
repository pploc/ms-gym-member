package com.gym.member.application.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.adapter.out.persistence.entity.GymQRSecretEntity;
import com.gym.member.adapter.out.persistence.entity.MembershipPlanEntity;
import com.gym.member.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.adapter.out.persistence.repository.GymQRSecretJpaRepository;
import com.gym.member.adapter.out.persistence.repository.MembershipPlanJpaRepository;
import com.gym.member.domain.dto.GymLocationDto;
import com.gym.member.domain.dto.PlanDto;
import com.gym.member.mapper.GymLocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GymLocationService {

    private final GymLocationJpaRepository gymLocationRepository;
    private final GymQRSecretJpaRepository qrSecretRepository;
    private final MembershipPlanJpaRepository planRepository;
    private final GymLocationMapper gymLocationMapper;

    @Transactional
    public GymLocationDto createGymLocation(String chainId, String name, String address, String city) {
        GymLocationEntity entity = new GymLocationEntity();
        entity.setChainId(chainId);
        entity.setName(name);
        entity.setAddress(address);
        entity.setCity(city);
        entity.setStatus("ACTIVE");

        GymLocationEntity saved = gymLocationRepository.save(entity);

        // Initialize daily secret
        GymQRSecretEntity secretEntity = new GymQRSecretEntity();
        secretEntity.setGymId(saved.getId());
        secretEntity.setDailySecret(UUID.randomUUID().toString().replace("-", ""));
        secretEntity.setUpdatedAt(Instant.now());
        qrSecretRepository.save(secretEntity);

        return gymLocationMapper.toDto(saved);
    }

    @Transactional
    public GymLocationDto updateGymLocation(String id, String name, String address, String city, String status) {
        GymLocationEntity entity = gymLocationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Gym location not found: " + id));

        if (name != null && !name.isBlank()) entity.setName(name);
        if (address != null && !address.isBlank()) entity.setAddress(address);
        if (city != null && !city.isBlank()) entity.setCity(city);
        if (status != null && !status.isBlank()) entity.setStatus(status);

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
        List<GymLocationEntity> entities;
        if (chainId != null && !chainId.isBlank()) {
            entities = gymLocationRepository.findByChainId(chainId);
        } else {
            entities = gymLocationRepository.findAll();
        }
        return entities.stream().map(gymLocationMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PlanDto> getPlans(String gymId) {
        List<MembershipPlanEntity> plans;
        if (gymId != null && !gymId.isBlank()) {
            plans = planRepository.findByGymIdAndActiveTrue(gymId);
        } else {
            plans = planRepository.findAll();
        }
        return plans.stream().map(gymLocationMapper::toPlanDto).toList();
    }
}
