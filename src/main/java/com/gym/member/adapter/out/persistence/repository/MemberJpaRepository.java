package com.gym.member.adapter.out.persistence.repository;

import com.gym.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.domain.model.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberJpaRepository extends JpaRepository<MemberEntity, String>, JpaSpecificationExecutor<MemberEntity> {
    Optional<MemberEntity> findByUserId(String userId);
    Page<MemberEntity> findByGymId(String gymId, Pageable pageable);
    List<MemberEntity> findByStatusAndGymIdIn(MembershipStatus status, List<String> gymIds);
    List<MemberEntity> findByStatusAndGymIdIn(MembershipStatus status, List<String> gymIds, Pageable pageable);
    List<MemberEntity> findByStatus(MembershipStatus status);
    List<MemberEntity> findByStatus(MembershipStatus status, Pageable pageable);
}
