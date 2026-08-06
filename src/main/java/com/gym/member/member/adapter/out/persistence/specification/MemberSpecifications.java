package com.gym.member.member.adapter.out.persistence.specification;

import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.member.domain.model.MembershipStatus;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public class MemberSpecifications {

    public static Specification<MemberEntity> hasSubscriptionAtGym(String gymId) {
        return (root, query, cb) -> {
            if (gymId == null || gymId.isBlank()) {
                return null;
            }
            Subquery<String> sub = query.subquery(String.class);
            Root<SubscriptionEntity> s = sub.from(SubscriptionEntity.class);
            sub.select(s.get("memberId")).where(cb.equal(s.get("gymId"), gymId));
            return root.get("id").in(sub);
        };
    }

    public static Specification<MemberEntity> hasStatus(MembershipStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<MemberEntity> hasSubscriptionAtGymIn(List<String> gymIds) {
        return (root, query, cb) -> {
            if (gymIds == null || gymIds.isEmpty()) {
                return null;
            }
            Subquery<String> sub = query.subquery(String.class);
            Root<SubscriptionEntity> s = sub.from(SubscriptionEntity.class);
            sub.select(s.get("memberId")).where(s.get("gymId").in(gymIds));
            return root.get("id").in(sub);
        };
    }
}
