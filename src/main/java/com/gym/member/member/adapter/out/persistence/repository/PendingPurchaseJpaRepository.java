package com.gym.member.member.adapter.out.persistence.repository;

import com.gym.member.member.adapter.out.persistence.entity.PendingPurchaseEntity;
import com.gym.member.member.domain.model.PurchaseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PendingPurchaseJpaRepository
        extends JpaRepository<PendingPurchaseEntity, String>, JpaSpecificationExecutor<PendingPurchaseEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PendingPurchaseEntity> findWithLockingById(String id);

    Optional<PendingPurchaseEntity> findByIdAndStatus(String id, PurchaseStatus status);
}
