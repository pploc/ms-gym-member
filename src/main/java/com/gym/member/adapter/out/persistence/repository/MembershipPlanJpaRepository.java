package com.gym.member.adapter.out.persistence.repository;

import com.gym.member.adapter.out.persistence.entity.MembershipPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MembershipPlanJpaRepository extends JpaRepository<MembershipPlanEntity, String>, JpaSpecificationExecutor<MembershipPlanEntity> {
    List<MembershipPlanEntity> findByGymIdAndActiveTrue(String gymId);
}
