package com.gym.member.adapter.out.persistence.repository;

import com.gym.member.adapter.out.persistence.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID>, JpaSpecificationExecutor<OutboxEventEntity> {
}
