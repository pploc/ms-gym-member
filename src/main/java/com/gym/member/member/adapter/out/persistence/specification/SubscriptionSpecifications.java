package com.gym.member.member.adapter.out.persistence.specification;

import com.gym.member.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.member.domain.model.MembershipStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public class SubscriptionSpecifications {

    public static Specification<SubscriptionEntity> hasStatus(MembershipStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<SubscriptionEntity> endDateOnOrBefore(LocalDate date) {
        return (root, query, cb) -> date == null ? null : cb.lessThanOrEqualTo(root.get("endDate"), date);
    }

    public static Specification<SubscriptionEntity> endDateEquals(LocalDate date) {
        return (root, query, cb) -> date == null ? null : cb.equal(root.get("endDate"), date);
    }

    public static Specification<SubscriptionEntity> isExpiredActive(LocalDate today) {
        return hasStatus(MembershipStatus.ACTIVE).and(endDateOnOrBefore(today));
    }

    public static Specification<SubscriptionEntity> isExpiringSoon(LocalDate warningDate) {
        return hasStatus(MembershipStatus.ACTIVE).and(endDateEquals(warningDate));
    }
}
