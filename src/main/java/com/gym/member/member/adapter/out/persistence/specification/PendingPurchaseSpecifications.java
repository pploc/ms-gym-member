package com.gym.member.member.adapter.out.persistence.specification;

import com.gym.member.member.adapter.out.persistence.entity.PendingPurchaseEntity;
import com.gym.member.member.domain.model.PurchaseStatus;
import org.springframework.data.jpa.domain.Specification;

public final class PendingPurchaseSpecifications {

    private PendingPurchaseSpecifications() {}

    public static Specification<PendingPurchaseEntity> hasMemberId(String memberId) {
        return (root, query, cb) -> cb.equal(root.get("memberId"), memberId);
    }

    public static Specification<PendingPurchaseEntity> hasStatus(PurchaseStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<PendingPurchaseEntity> hasPaymentId(String paymentId) {
        return (root, query, cb) -> cb.equal(root.get("paymentId"), paymentId);
    }
}
