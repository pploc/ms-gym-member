package com.gym.member.member.application.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.member.adapter.out.persistence.entity.PendingPurchaseEntity;
import com.gym.member.member.adapter.out.persistence.repository.PendingPurchaseJpaRepository;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.model.PlanType;
import com.gym.member.member.domain.model.PurchaseStatus;
import com.gym.proto.plans.v1.ResolvePurchasablePlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PendingPurchasePersistenceService {

    private final PendingPurchaseJpaRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PendingPurchaseEntity create(
            MemberDto member,
            String userId,
            String provider,
            String idempotencyKey,
            ResolvePurchasablePlanResponse plan,
            PlanType planType) {
        PendingPurchaseEntity purchase = new PendingPurchaseEntity();
        purchase.setMemberId(member.id().toString());
        purchase.setUserId(userId);
        purchase.setGymId(plan.getGymId());
        purchase.setPlanId(plan.getPlanId());
        purchase.setPlanTypeSnapshot(planType);
        purchase.setDurationDaysSnapshot(plan.hasDurationDays() ? plan.getDurationDays() : null);
        purchase.setPriceVndSnapshot(plan.getPriceVnd());
        purchase.setProvider(provider);
        purchase.setIdempotencyKey(idempotencyKey);
        purchase.setStatus(PurchaseStatus.PENDING);
        return repository.saveAndFlush(purchase);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public PendingPurchaseEntity load(String userId, String idempotencyKey) {
        return repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseThrow(() -> new NotFoundException("Pending purchase not found for idempotency key"));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachPayment(String purchaseId, String paymentId) {
        PendingPurchaseEntity purchase = repository.findWithLockingById(purchaseId)
                .orElseThrow(() -> new NotFoundException("Pending purchase not found: " + purchaseId));
        if (purchase.getPaymentId() != null && !purchase.getPaymentId().equals(paymentId)) {
            throw new IllegalStateException("Payment ID conflicts with existing purchase");
        }
        purchase.setPaymentId(paymentId);
        repository.save(purchase);
    }

    static void requireSameIntent(
            PendingPurchaseEntity purchase,
            MemberDto member,
            String provider,
            ResolvePurchasablePlanResponse plan,
            PlanType planType) {
        if (!Objects.equals(purchase.getMemberId(), member.id().toString())
                || !Objects.equals(purchase.getGymId(), plan.getGymId())
                || !Objects.equals(purchase.getPlanId(), plan.getPlanId())
                || !Objects.equals(purchase.getProvider(), provider)
                || purchase.getPlanTypeSnapshot() != planType
                || !Objects.equals(
                        purchase.getDurationDaysSnapshot(), plan.hasDurationDays() ? plan.getDurationDays() : null)
                || purchase.getPriceVndSnapshot() != plan.getPriceVnd()) {
            throw new IllegalArgumentException("Idempotency key was already used for a different purchase");
        }
    }
}
