package com.gym.member.location.adapter.out.persistence.repository;

import com.gym.member.location.adapter.out.persistence.entity.GymQRSecretEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GymQRSecretJpaRepository extends JpaRepository<GymQRSecretEntity, String> {
}
