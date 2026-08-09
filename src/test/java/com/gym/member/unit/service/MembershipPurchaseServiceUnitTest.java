package com.gym.member.unit.service;

import com.gym.member.member.adapter.out.persistence.entity.PendingPurchaseEntity;
import com.gym.member.member.adapter.out.persistence.repository.PendingPurchaseJpaRepository;
import com.gym.member.member.application.port.in.MemberUseCase;
import com.gym.member.member.application.service.MembershipPurchaseService;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.member.member.domain.model.PlanType;
import com.gym.member.member.domain.model.PurchaseStatus;
import com.gym.member.payment.adapter.out.grpc.PaymentGrpcClient;
import com.gym.member.plans.adapter.out.grpc.PlansGrpcClient;
import com.gym.proto.member.v1.PurchaseMembershipResponse;
import com.gym.proto.payment.v1.InitiatePaymentRequest;
import com.gym.proto.payment.v1.InitiatePaymentResponse;
import com.gym.proto.plans.v1.ResolvePurchasablePlanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipPurchaseServiceUnitTest {

    @Mock
    private MemberUseCase memberUseCase;

    @Mock
    private PlansGrpcClient plansGrpcClient;

    @Mock
    private PaymentGrpcClient paymentGrpcClient;

    @Mock
    private PendingPurchaseJpaRepository pendingPurchaseRepository;

    @InjectMocks
    private MembershipPurchaseService service;

    private String userId;
    private String gymId;
    private String planId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().toString();
        gymId = UUID.randomUUID().toString();
        planId = UUID.randomUUID().toString();
        memberId = UUID.randomUUID();
    }

    @Test
    void givenNonblankDiscountCode_whenPurchaseMembership_thenRejectsWithoutCallingPlansOrPayment() {
        assertThrows(IllegalArgumentException.class, () ->
                service.purchaseMembership(userId, gymId, planId, "STRIPE", "SAVE10"));

        verify(plansGrpcClient, never()).resolvePurchasablePlan(any(), any());
        verify(paymentGrpcClient, never()).initiatePayment(any());
        verify(pendingPurchaseRepository, never()).save(any());
    }

    @Test
    void givenBlankUserId_whenPurchaseMembership_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.purchaseMembership(" ", gymId, planId, "STRIPE", null));
        verify(plansGrpcClient, never()).resolvePurchasablePlan(any(), any());
    }

    @Test
    void givenBlankGymId_whenPurchaseMembership_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.purchaseMembership(userId, "", planId, "STRIPE", null));
    }

    @Test
    void givenBlankPlanId_whenPurchaseMembership_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.purchaseMembership(userId, gymId, " ", "STRIPE", null));
    }

    @Test
    void givenBlankProvider_whenPurchaseMembership_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.purchaseMembership(userId, gymId, planId, "", null));
    }

    @Test
    void givenResolvedPlan_whenPurchaseMembership_thenPersistsPendingAndPaysWithPurchaseId() {
        MemberDto member = new MemberDto(
                memberId, UUID.fromString(userId), "Buyer", null, null, null,
                MembershipStatus.NONE, Instant.now(), Instant.now());
        when(memberUseCase.getMemberByUserId(userId)).thenReturn(member);
        when(plansGrpcClient.resolvePurchasablePlan(planId, gymId)).thenReturn(
                ResolvePurchasablePlanResponse.newBuilder()
                        .setPlanId(planId)
                        .setGymId(gymId)
                        .setPlanType(com.gym.proto.common.v1.PlanType.PLAN_TYPE_MONTHLY)
                        .setDurationDays(30)
                        .setPriceVnd(450_000L)
                        .build());

        String purchaseId = UUID.randomUUID().toString();
        when(pendingPurchaseRepository.save(any(PendingPurchaseEntity.class))).thenAnswer(inv -> {
            PendingPurchaseEntity entity = inv.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(purchaseId);
            }
            return entity;
        });
        when(paymentGrpcClient.initiatePayment(any())).thenReturn(
                InitiatePaymentResponse.newBuilder()
                        .setPaymentId("pay-1")
                        .setPaymentUrl("https://pay.example/1")
                        .build());

        PurchaseMembershipResponse response = service.purchaseMembership(userId, gymId, planId, "STRIPE", "");

        assertEquals("pay-1", response.getPaymentId());
        assertEquals("https://pay.example/1", response.getPaymentUrl());

        ArgumentCaptor<PendingPurchaseEntity> purchaseCaptor = ArgumentCaptor.forClass(PendingPurchaseEntity.class);
        verify(pendingPurchaseRepository, org.mockito.Mockito.atLeastOnce()).save(purchaseCaptor.capture());
        PendingPurchaseEntity firstSave = purchaseCaptor.getAllValues().getFirst();
        assertEquals(memberId.toString(), firstSave.getMemberId());
        assertEquals(userId, firstSave.getUserId());
        assertEquals(gymId, firstSave.getGymId());
        assertEquals(planId, firstSave.getPlanId());
        assertEquals(PlanType.MONTHLY, firstSave.getPlanTypeSnapshot());
        assertEquals(30, firstSave.getDurationDaysSnapshot());
        assertEquals(450_000L, firstSave.getPriceVndSnapshot());
        assertEquals(PurchaseStatus.PENDING, firstSave.getStatus());

        ArgumentCaptor<InitiatePaymentRequest> paymentCaptor = ArgumentCaptor.forClass(InitiatePaymentRequest.class);
        verify(paymentGrpcClient).initiatePayment(paymentCaptor.capture());
        InitiatePaymentRequest paymentReq = paymentCaptor.getValue();
        assertEquals(purchaseId, paymentReq.getReferenceId());
        assertEquals(userId, paymentReq.getUserId());
        assertEquals(gymId, paymentReq.getGymId());
        assertEquals(450_000L, paymentReq.getAmountVnd());
        assertEquals(com.gym.proto.common.v1.PaymentType.PAYMENT_TYPE_MEMBERSHIP, paymentReq.getPaymentType());
    }
}
