package com.gym.member.member.adapter.out.persistence.repository;

import com.gym.member.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.member.domain.model.MembershipStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, String>, JpaSpecificationExecutor<SubscriptionEntity> {
    Optional<SubscriptionEntity> findByMemberIdAndStatus(String memberId, MembershipStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SubscriptionEntity s where s.memberId = :memberId and s.status in :statuses")
    Optional<SubscriptionEntity> findCurrentForUpdate(
            @Param("memberId") String memberId,
            @Param("statuses") Collection<MembershipStatus> statuses
    );
}
