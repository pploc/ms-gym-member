package com.gym.member.unit.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.member.adapter.out.persistence.entity.PendingPurchaseEntity;
import com.gym.member.member.application.port.in.MemberUseCase;
import com.gym.member.member.application.service.MembershipPurchaseService;
import com.gym.member.member.application.service.PendingPurchasePersistenceService;
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
import static org.mockito.ArgumentMatchers.eq;
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
    private PendingPurchasePersistenceService persistenceService;

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
    void given_nonblank_discount_code_when_purchase_membership_then_rejects_without_side_effects() {
        // given / when
        assertThrows(
                IllegalArgumentException.class,
                () -> service.purchaseMembership(userId, gymId, planId, "SEPAY", "SAVE10", "key-1"));

        // then
        verify(plansGrpcClient, never()).resolvePurchasablePlan(any(), any());
        verify(paymentGrpcClient, never()).initiatePayment(any());
        verify(persistenceService, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void given_unsupported_provider_when_purchase_membership_then_rejects_before_plans_or_persistence() {
        // given / when
        assertThrows(
                IllegalArgumentException.class,
                () -> service.purchaseMembership(userId, gymId, planId, "stripe", null, "key-1"));

        // then
        verify(plansGrpcClient, never()).resolvePurchasablePlan(any(), any());
        verify(paymentGrpcClient, never()).initiatePayment(any());
        verify(persistenceService, never()).load(any(), any());
        verify(persistenceService, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void given_blank_idempotency_key_when_purchase_membership_then_throws() {
        // given / when / then
        assertThrows(
                IllegalArgumentException.class,
                () -> service.purchaseMembership(userId, gymId, planId, "SEPAY", null, " "));
    }

    @Test
    void given_trimmed_lowercase_sepay_when_purchase_membership_then_persists_normalized_provider_before_payment() {
        // given
        MemberDto member = new MemberDto(
                memberId,
                UUID.fromString(userId),
                "Buyer",
                null,
                null,
                null,
                MembershipStatus.NONE,
                Instant.now(),
                Instant.now());
        when(memberUseCase.getMemberByUserId(userId)).thenReturn(member);
        when(persistenceService.load(userId, "key-1")).thenThrow(new NotFoundException("missing"));
        when(plansGrpcClient.resolvePurchasablePlan(planId, gymId))
                .thenReturn(ResolvePurchasablePlanResponse.newBuilder()
                        .setPlanId(planId)
                        .setGymId(gymId)
                        .setPlanType(com.gym.proto.common.v1.PlanType.PLAN_TYPE_MONTHLY)
                        .setDurationDays(30)
                        .setPriceVnd(450_000L)
                        .build());

        String purchaseId = UUID.randomUUID().toString();
        PendingPurchaseEntity pending = pending(purchaseId);
        when(persistenceService.create(eq(member), eq(userId), eq("SEPAY"), eq("key-1"), any(), eq(PlanType.MONTHLY)))
                .thenReturn(pending);
        when(paymentGrpcClient.initiatePayment(any()))
                .thenReturn(InitiatePaymentResponse.newBuilder()
                        .setPaymentId("pay-1")
                        .setPaymentUrl("https://pay.example/1")
                        .build());

        // when
        PurchaseMembershipResponse response =
                service.purchaseMembership(userId, gymId, planId, " sepay ", "", "key-1");

        // then
        assertEquals("pay-1", response.getPaymentId());
        assertEquals("https://pay.example/1", response.getPaymentUrl());
        verify(persistenceService).create(eq(member), eq(userId), eq("SEPAY"), eq("key-1"), any(), eq(PlanType.MONTHLY));
        ArgumentCaptor<InitiatePaymentRequest> paymentCaptor = ArgumentCaptor.forClass(InitiatePaymentRequest.class);
        verify(paymentGrpcClient).initiatePayment(paymentCaptor.capture());
        assertEquals(purchaseId, paymentCaptor.getValue().getReferenceId());
        assertEquals("SEPAY", paymentCaptor.getValue().getProvider());
        verify(persistenceService).attachPayment(purchaseId, "pay-1");
    }

    @Test
    void given_existing_idempotency_key_when_purchase_membership_then_reuses_purchase_reference() {
        // given
        MemberDto member = new MemberDto(
                memberId,
                UUID.fromString(userId),
                "Buyer",
                null,
                null,
                null,
                MembershipStatus.NONE,
                Instant.now(),
                Instant.now());
        when(memberUseCase.getMemberByUserId(userId)).thenReturn(member);
        PendingPurchaseEntity pending = pending("purchase-1");
        when(persistenceService.load(userId, "key-1")).thenReturn(pending);
        when(paymentGrpcClient.initiatePayment(any()))
                .thenReturn(InitiatePaymentResponse.newBuilder()
                        .setPaymentId("pay-1")
                        .setPaymentUrl("https://pay.example/1")
                        .build());

        // when
        PurchaseMembershipResponse response =
                service.purchaseMembership(userId, gymId, planId, "SEPAY", null, "key-1");

        // then
        assertEquals("pay-1", response.getPaymentId());
        verify(plansGrpcClient, never()).resolvePurchasablePlan(any(), any());
        verify(persistenceService, never()).create(any(), any(), any(), any(), any(), any());
        ArgumentCaptor<InitiatePaymentRequest> paymentCaptor = ArgumentCaptor.forClass(InitiatePaymentRequest.class);
        verify(paymentGrpcClient).initiatePayment(paymentCaptor.capture());
        assertEquals("purchase-1", paymentCaptor.getValue().getReferenceId());
    }

    private PendingPurchaseEntity pending(String purchaseId) {
        PendingPurchaseEntity entity = new PendingPurchaseEntity();
        entity.setId(purchaseId);
        entity.setMemberId(memberId.toString());
        entity.setUserId(userId);
        entity.setGymId(gymId);
        entity.setPlanId(planId);
        entity.setPlanTypeSnapshot(PlanType.MONTHLY);
        entity.setDurationDaysSnapshot(30);
        entity.setPriceVndSnapshot(450_000L);
        entity.setProvider("SEPAY");
        entity.setIdempotencyKey("key-1");
        entity.setStatus(PurchaseStatus.PENDING);
        return entity;
    }
}
