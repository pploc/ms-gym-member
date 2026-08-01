package com.gym.member.adapter.out.persistence.repository;

import com.gym.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.domain.model.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, String>, JpaSpecificationExecutor<SubscriptionEntity> {
    Optional<SubscriptionEntity> findByMemberIdAndStatus(String memberId, MembershipStatus status);
}
