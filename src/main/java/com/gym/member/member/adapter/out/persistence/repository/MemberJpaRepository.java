package com.gym.member.member.adapter.out.persistence.repository;

import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.domain.model.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemberJpaRepository extends JpaRepository<MemberEntity, String>, JpaSpecificationExecutor<MemberEntity> {
    Optional<MemberEntity> findByUserId(String userId);
    List<MemberEntity> findByStatus(MembershipStatus status);
    List<MemberEntity> findByStatus(MembershipStatus status, Pageable pageable);

    @Query("""
            select distinct m from MemberEntity m
            join SubscriptionEntity s on s.memberId = m.id
            where s.gymId = :gymId
            """)
    Page<MemberEntity> findDistinctBySubscriptionGymId(@Param("gymId") String gymId, Pageable pageable);

    @Query("""
            select distinct m from MemberEntity m
            join SubscriptionEntity s on s.memberId = m.id
            where s.status = :status and s.gymId in :gymIds
            """)
    List<MemberEntity> findDistinctBySubscriptionStatusAndGymIdIn(
            @Param("status") MembershipStatus status,
            @Param("gymIds") Collection<String> gymIds,
            Pageable pageable
    );
}
