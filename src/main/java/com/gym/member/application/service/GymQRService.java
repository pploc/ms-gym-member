package com.gym.member.application.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.adapter.out.persistence.entity.GymQRSecretEntity;
import com.gym.member.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.adapter.out.persistence.repository.GymQRSecretJpaRepository;
import com.gym.member.domain.dto.GymDailySecretDto;
import com.gym.member.mapper.GymQRSecretMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymQRService {

    private final GymQRSecretJpaRepository qrSecretRepository;
    private final GymLocationJpaRepository gymLocationRepository;
    private final GymQRSecretMapper gymQRSecretMapper;

    @Transactional(readOnly = true)
    public GymDailySecretDto getGymDailySecret(String gymId) {
        GymQRSecretEntity entity = qrSecretRepository.findById(gymId)
                .orElseThrow(() -> new NotFoundException("Gym QR secret not found for gymId: " + gymId));
        return gymQRSecretMapper.toDto(entity);
    }

    @Transactional
    public void rotateAllGymDailySecrets() {
        List<GymLocationEntity> activeGyms = gymLocationRepository.findByStatus("ACTIVE");
        for (GymLocationEntity gym : activeGyms) {
            String newSecret = UUID.randomUUID().toString().replace("-", "");
            GymQRSecretEntity secretEntity = qrSecretRepository.findById(gym.getId())
                    .orElseGet(() -> {
                        GymQRSecretEntity newEntity = new GymQRSecretEntity();
                        newEntity.setGymId(gym.getId());
                        return newEntity;
                    });
            secretEntity.setDailySecret(newSecret);
            secretEntity.setUpdatedAt(Instant.now());
            qrSecretRepository.save(secretEntity);
        }
        log.info("Rotated daily QR secrets for {} active gyms", activeGyms.size());
    }

    public String computeDailyToken(String gymId, String dailySecret, LocalDate date) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = gymId + ":" + date.toString() + ":" + dailySecret;
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
