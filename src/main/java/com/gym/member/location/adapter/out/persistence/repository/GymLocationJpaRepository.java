package com.gym.member.location.adapter.out.persistence.repository;

import com.gym.member.location.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.location.domain.model.GymLocationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GymLocationJpaRepository extends JpaRepository<GymLocationEntity, String> {
    List<GymLocationEntity> findByChainId(String chainId);
    List<GymLocationEntity> findByStatus(GymLocationStatus status);
}
