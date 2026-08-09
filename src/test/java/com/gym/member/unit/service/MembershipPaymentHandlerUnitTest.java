package com.gym.member.unit.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.member.adapter.out.persistence.entity.PendingPurchaseEntity;
import com.gym.member.member.adapter.out.persistence.repository.PendingPurchaseJpaRepository;
import com.gym.member.member.application.port.in.SubscriptionActivationUseCase;
import com.gym.member.member.application.service.strategy.MembershipPaymentHandler;
import com.gym.member.member.domain.dto.PurchasedPlanTerms;
import com.gym.member.member.domain.model.PlanType;
import com.gym.member.member.domain.model.PurchaseStatus;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipPaymentHandlerUnitTest {

    @Mock
    private PendingPurchaseJpaRepository pendingPurchaseRepository;

    @Mock
    private SubscriptionActivationUseCase subscriptionActivationUseCase;

    @InjectMocks
    private MembershipPaymentHandler handler;

    private String userId;
    private String purchaseId;
    private String memberId;
    private String gymId;
    private String planId;
    private String paymentId;
    private PendingPurchaseEntity purchase;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().toString();
        purchaseId = UUID.randomUUID().toString();
        memberId = UUID.randomUUID().toString();
        gymId = UUID.randomUUID().toString();
        planId = UUID.randomUUID().toString();
        paymentId = "pay-" + UUID.randomUUID();

        purchase = new PendingPurchaseEntity();
        purchase.setId(purchaseId);
        purchase.setMemberId(memberId);
        purchase.setUserId(userId);
        purchase.setGymId(gymId);
        purchase.setPlanId(planId);
        purchase.setPlanTypeSnapshot(PlanType.MONTHLY);
        purchase.setDurationDaysSnapshot(30);
        purchase.setPriceVndSnapshot(500_000L);
        purchase.setProvider("STRIPE");
        purchase.setPaymentId(paymentId);
        purchase.setStatus(PurchaseStatus.PENDING);
    }

    @Test
    void givenMembershipType_whenSupports_thenReturnsTrueOnlyForMembership() {
        assertTrue(handler.supports(com.gym.proto.common.v1.PaymentType.PAYMENT_TYPE_MEMBERSHIP));
        assertTrue(handler.supports(com.gym.proto.common.v1.PaymentType.PAYMENT_TYPE_MEMBERSHIP));
        assertFalse(handler.supports(com.gym.proto.common.v1.PaymentType.PAYMENT_TYPE_TRAINER_BOOKING));
        assertFalse(handler.supports(null));
    }

    @Test
    void givenValidPendingPurchase_whenHandle_thenActivatesFromFrozenTermsAndCompletes() {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setUserId(userId)
                .setReferenceId(purchaseId)
                .setType(com.gym.proto.common.v1.PaymentType.PAYMENT_TYPE_MEMBERSHIP)
                .setGymId(gymId)
                .setPaymentId(paymentId)
                .setAmountVnd(500_000L)
                .build();
        when(pendingPurchaseRepository.findWithLockingById(purchaseId)).thenReturn(Optional.of(purchase));
        when(pendingPurchaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(event, "fallback-key");

        ArgumentCaptor<PurchasedPlanTerms> termsCaptor = ArgumentCaptor.forClass(PurchasedPlanTerms.class);
        verify(subscriptionActivationUseCase).activateOrRenewSubscription(eq(memberId), termsCaptor.capture());
        PurchasedPlanTerms terms = termsCaptor.getValue();
        assertEquals(planId, terms.planId());
        assertEquals(gymId, terms.gymId());
        assertEquals(PlanType.MONTHLY, terms.planType());
        assertEquals(30, terms.durationDays());
        assertEquals(500_000L, terms.priceVnd());
        assertEquals(PurchaseStatus.COMPLETED, purchase.getStatus());
        verify(pendingPurchaseRepository).save(purchase);
    }

    @Test
    void givenAlreadyCompletedPurchase_whenHandle_thenIsIdempotentNoOp() {
        purchase.setStatus(PurchaseStatus.COMPLETED);
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setUserId(userId)
                .setReferenceId(purchaseId)
                .setType(com.gym.proto.common.v1.PaymentType.PAYMENT_TYPE_MEMBERSHIP)
                .setGymId(gymId)
                .setPaymentId(paymentId)
                .setAmountVnd(500_000L)
                .build();
        when(pendingPurchaseRepository.findWithLockingById(purchaseId)).thenReturn(Optional.of(purchase));

        handler.handle(event, userId);

        verify(subscriptionActivationUseCase, never()).activateOrRenewSubscription(any(), any());
        verify(pendingPurchaseRepository, never()).save(any());
    }

    @Test
    void givenBlankUserIdAndValidFallback_whenHandle_thenUsesFallbackUser() {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setUserId("")
                .setReferenceId(purchaseId)
                .setType(com.gym.proto.common.v1.PaymentType.PAYMENT_TYPE_MEMBERSHIP)
                .setGymId(gymId)
                .setPaymentId(paymentId)
                .setAmountVnd(500_000L)
                .build();
        when(pendingPurchaseRepository.findWithLockingById(purchaseId)).thenReturn(Optional.of(purchase));
        when(pendingPurchaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(event, userId);

        verify(subscriptionActivationUseCase).activateOrRenewSubscription(eq(memberId), any(PurchasedPlanTerms.class));
        assertEquals(PurchaseStatus.COMPLETED, purchase.getStatus());
    }

    @Test
    void givenMissingReferenceId_whenHandle_thenThrowsIllegalArgumentException() {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setUserId(userId)
                .setReferenceId("")
                .build();

        assertThrows(IllegalArgumentException.class, () -> handler.handle(event, userId));
    }

    @Test
    void givenUnknownPurchase_whenHandle_thenThrowsNotFoundException() {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setUserId(userId)
                .setReferenceId(purchaseId)
                .setGymId(gymId)
                .setPaymentId(paymentId)
                .setAmountVnd(500_000L)
                .build();
        when(pendingPurchaseRepository.findWithLockingById(purchaseId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> handler.handle(event, userId));
    }

    @Test
    void givenUserMismatch_whenHandle_thenThrowsIllegalArgumentException() {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setUserId(UUID.randomUUID().toString())
                .setReferenceId(purchaseId)
                .setGymId(gymId)
                .setPaymentId(paymentId)
                .setAmountVnd(500_000L)
                .build();
        when(pendingPurchaseRepository.findWithLockingById(purchaseId)).thenReturn(Optional.of(purchase));

        assertThrows(IllegalArgumentException.class, () -> handler.handle(event, "fallback"));
        verify(subscriptionActivationUseCase, never()).activateOrRenewSubscription(any(), any());
    }

    @Test
    void givenGymMismatch_whenHandle_thenThrowsIllegalArgumentException() {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setUserId(userId)
                .setReferenceId(purchaseId)
                .setGymId(UUID.randomUUID().toString())
                .setPaymentId(paymentId)
                .setAmountVnd(500_000L)
                .build();
        when(pendingPurchaseRepository.findWithLockingById(purchaseId)).thenReturn(Optional.of(purchase));

        assertThrows(IllegalArgumentException.class, () -> handler.handle(event, userId));
    }

    @Test
    void givenAmountMismatch_whenHandle_thenThrowsIllegalArgumentException() {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setUserId(userId)
                .setReferenceId(purchaseId)
                .setGymId(gymId)
                .setPaymentId(paymentId)
                .setAmountVnd(1L)
                .build();
        when(pendingPurchaseRepository.findWithLockingById(purchaseId)).thenReturn(Optional.of(purchase));

        assertThrows(IllegalArgumentException.class, () -> handler.handle(event, userId));
    }

    @Test
    void givenPaymentIdMismatch_whenHandle_thenThrowsIllegalArgumentException() {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setUserId(userId)
                .setReferenceId(purchaseId)
                .setGymId(gymId)
                .setPaymentId("other-pay")
                .setAmountVnd(500_000L)
                .build();
        when(pendingPurchaseRepository.findWithLockingById(purchaseId)).thenReturn(Optional.of(purchase));

        assertThrows(IllegalArgumentException.class, () -> handler.handle(event, userId));
    }

    @Test
    void givenNonPendingNonCompletedStatus_whenHandle_thenThrowsIllegalStateException() {
        purchase.setStatus(PurchaseStatus.FAILED);
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setUserId(userId)
                .setReferenceId(purchaseId)
                .setGymId(gymId)
                .setPaymentId(paymentId)
                .setAmountVnd(500_000L)
                .build();
        when(pendingPurchaseRepository.findWithLockingById(purchaseId)).thenReturn(Optional.of(purchase));

        assertThrows(IllegalStateException.class, () -> handler.handle(event, userId));
        verify(subscriptionActivationUseCase, never()).activateOrRenewSubscription(any(), any());
    }

    @Test
    void givenBlankPaymentId_whenHandle_thenThrowsIllegalArgumentException() {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setUserId(userId)
                .setReferenceId(purchaseId)
                .setGymId(gymId)
                .setPaymentId("")
                .setAmountVnd(500_000L)
                .build();
        when(pendingPurchaseRepository.findWithLockingById(purchaseId)).thenReturn(Optional.of(purchase));

        assertThrows(IllegalArgumentException.class, () -> handler.handle(event, userId));
    }
}
