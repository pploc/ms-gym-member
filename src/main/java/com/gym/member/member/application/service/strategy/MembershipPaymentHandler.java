package com.gym.member.member.application.service.strategy;

import com.gym.common.error.NotFoundException;
import com.gym.member.member.adapter.out.persistence.entity.PendingPurchaseEntity;
import com.gym.member.member.adapter.out.persistence.repository.PendingPurchaseJpaRepository;
import com.gym.member.member.application.port.in.SubscriptionActivationUseCase;
import com.gym.member.member.domain.dto.PurchasedPlanTerms;
import com.gym.member.member.domain.model.PurchaseStatus;
import com.gym.proto.common.v1.PaymentType;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipPaymentHandler implements PaymentTypeHandler {

    private final PendingPurchaseJpaRepository pendingPurchaseRepository;
    private final SubscriptionActivationUseCase subscriptionActivationUseCase;

    @Override
    public boolean supports(PaymentType paymentType) {
        return paymentType == PaymentType.PAYMENT_TYPE_MEMBERSHIP;
    }

    @Override
    public void handle(PaymentCompletedEvent event, String fallbackKey) {
        String purchaseId = event.getReferenceId();
        if (purchaseId == null || purchaseId.isBlank()) {
            throw new IllegalArgumentException("PaymentCompletedEvent missing referenceId (purchase_id)");
        }

        PendingPurchaseEntity purchase = pendingPurchaseRepository
                .findWithLockingById(purchaseId)
                .orElseThrow(() -> new NotFoundException("Pending purchase not found: " + purchaseId));

        if (purchase.getStatus() == PurchaseStatus.COMPLETED) {
            log.info("Purchase {} already completed; treating payment completion as idempotent", purchaseId);
            return;
        }
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new IllegalStateException("Purchase " + purchaseId + " is not pending: " + purchase.getStatus());
        }

        String userId = event.getUserId().isBlank() ? fallbackKey : event.getUserId();
        if (userId == null || userId.isBlank() || !userId.equals(purchase.getUserId())) {
            throw new IllegalArgumentException("PaymentCompletedEvent user mismatch for purchase " + purchaseId);
        }
        if (event.getGymId() == null || event.getGymId().isBlank() || !event.getGymId().equals(purchase.getGymId())) {
            throw new IllegalArgumentException("PaymentCompletedEvent gym mismatch for purchase " + purchaseId);
        }
        if (event.getPaymentId() == null || event.getPaymentId().isBlank()) {
            throw new IllegalArgumentException("PaymentCompletedEvent missing paymentId");
        }
        if (purchase.getPaymentId() != null
                && !purchase.getPaymentId().isBlank()
                && !purchase.getPaymentId().equals(event.getPaymentId())) {
            throw new IllegalArgumentException("PaymentCompletedEvent paymentId mismatch for purchase " + purchaseId);
        }
        if (event.getAmountVnd() != purchase.getPriceVndSnapshot()) {
            throw new IllegalArgumentException("PaymentCompletedEvent amount mismatch for purchase " + purchaseId);
        }
        if (event.getProvider() == null || event.getProvider().isBlank()) {
            throw new IllegalArgumentException("PaymentCompletedEvent missing provider");
        }
        if (!event.getProvider().equals(purchase.getProvider())) {
            throw new IllegalArgumentException("PaymentCompletedEvent provider mismatch for purchase " + purchaseId);
        }

        subscriptionActivationUseCase.activateOrRenewSubscription(
                purchase.getMemberId(),
                new PurchasedPlanTerms(
                        purchase.getPlanId(),
                        purchase.getGymId(),
                        purchase.getPlanTypeSnapshot(),
                        purchase.getDurationDaysSnapshot(),
                        purchase.getPriceVndSnapshot()));

        purchase.setPaymentId(event.getPaymentId());
        purchase.setStatus(PurchaseStatus.COMPLETED);
        pendingPurchaseRepository.save(purchase);
        log.info("Successfully completed purchase {} for member {}", purchaseId, purchase.getMemberId());
    }
}
