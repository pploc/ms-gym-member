package com.gym.member.adapter.out.persistence.specification;

import com.gym.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.domain.model.MembershipStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public class MemberSpecifications {

    public static Specification<MemberEntity> hasGymId(String gymId) {
        return (root, query, cb) -> (gymId == null || gymId.isBlank()) ? null : cb.equal(root.get("gymId"), gymId);
    }

    public static Specification<MemberEntity> hasStatus(MembershipStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<MemberEntity> hasGymIdIn(List<String> gymIds) {
        return (root, query, cb) -> (gymIds == null || gymIds.isEmpty()) ? null : root.get("gymId").in(gymIds);
    }
}
