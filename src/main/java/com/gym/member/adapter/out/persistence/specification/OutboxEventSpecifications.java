package com.gym.member.adapter.out.persistence.specification;

import com.gym.member.adapter.out.persistence.entity.OutboxEventEntity;
import org.springframework.data.jpa.domain.Specification;

public class OutboxEventSpecifications {

    public static Specification<OutboxEventEntity> hasStatus(String status) {
        return (root, query, cb) -> (status == null || status.isBlank()) ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<OutboxEventEntity> isPending() {
        return hasStatus("PENDING");
    }
}
