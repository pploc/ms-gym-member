package com.gym.member.adapter.out.persistence.repository;

import com.gym.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.domain.model.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, String> {
    Optional<SubscriptionEntity> findByMemberIdAndStatus(String memberId, MembershipStatus status);
    List<SubscriptionEntity> findByMemberId(String memberId);

    @Query("SELECT s FROM SubscriptionEntity s WHERE s.status = 'ACTIVE' AND s.endDate <= :today")
    List<SubscriptionEntity> findExpiredActiveSubscriptions(@Param("today") LocalDate today);

    @Query("SELECT s FROM SubscriptionEntity s WHERE s.status = 'ACTIVE' AND s.endDate = :warningDate")
    List<SubscriptionEntity> findExpiringSoonSubscriptions(@Param("warningDate") LocalDate warningDate);
}
