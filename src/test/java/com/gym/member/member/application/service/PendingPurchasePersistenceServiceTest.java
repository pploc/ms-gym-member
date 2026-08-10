package com.gym.member.member.application.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.member.adapter.out.persistence.entity.PendingPurchaseEntity;
import com.gym.member.member.adapter.out.persistence.repository.PendingPurchaseJpaRepository;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.member.member.domain.model.PlanType;
import com.gym.member.member.domain.model.PurchaseStatus;
import com.gym.proto.plans.v1.ResolvePurchasablePlanResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingPurchasePersistenceServiceTest {

    @Mock
    private PendingPurchaseJpaRepository repository;

    @Test
    void given_resolved_monthly_plan_when_create_then_persists_canonical_snapshot() {
        // given
        PendingPurchasePersistenceService service = new PendingPurchasePersistenceService(repository);
        MemberDto member = member();
        ResolvePurchasablePlanResponse plan = plan(PlanType.MONTHLY, 30);
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        PendingPurchaseEntity result = service.create(member, "user-1", "STRIPE", "key-1", plan, PlanType.MONTHLY);

        // then
        ArgumentCaptor<PendingPurchaseEntity> captor = ArgumentCaptor.forClass(PendingPurchaseEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        PendingPurchaseEntity saved = captor.getValue();
        assertSame(saved, result);
        assertEquals(member.id().toString(), saved.getMemberId());
        assertEquals("user-1", saved.getUserId());
        assertEquals("gym-1", saved.getGymId());
        assertEquals("plan-1", saved.getPlanId());
        assertEquals(PlanType.MONTHLY, saved.getPlanTypeSnapshot());
        assertEquals(30, saved.getDurationDaysSnapshot());
        assertEquals(450_000L, saved.getPriceVndSnapshot());
        assertEquals("STRIPE", saved.getProvider());
        assertEquals("key-1", saved.getIdempotencyKey());
        assertEquals(PurchaseStatus.PENDING, saved.getStatus());
    }

    @Test
    void given_resolved_lifetime_plan_when_create_then_persists_null_duration() {
        // given
        PendingPurchasePersistenceService service = new PendingPurchasePersistenceService(repository);
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        PendingPurchaseEntity result = service.create(
                member(), "user-1", "STRIPE", "key-1", plan(PlanType.LIFETIME, null), PlanType.LIFETIME);

        // then
        assertNull(result.getDurationDaysSnapshot());
    }

    @Test
    void given_existing_purchase_when_load_then_returns_purchase() {
        // given
        PendingPurchasePersistenceService service = new PendingPurchasePersistenceService(repository);
        PendingPurchaseEntity purchase = purchase();
        when(repository.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.of(purchase));

        // when
        PendingPurchaseEntity result = service.load("user-1", "key-1");

        // then
        assertSame(purchase, result);
    }

    @Test
    void given_missing_purchase_when_load_then_throws_not_found() {
        // given
        PendingPurchasePersistenceService service = new PendingPurchasePersistenceService(repository);
        when(repository.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.empty());

        // when / then
        assertThrows(NotFoundException.class, () -> service.load("user-1", "key-1"));
    }

    @Test
    void given_unattached_purchase_when_attach_payment_then_saves_payment_id() {
        // given
        PendingPurchasePersistenceService service = new PendingPurchasePersistenceService(repository);
        PendingPurchaseEntity purchase = purchase();
        when(repository.findWithLockingById("purchase-1")).thenReturn(Optional.of(purchase));

        // when
        service.attachPayment("purchase-1", "payment-1");

        // then
        assertEquals("payment-1", purchase.getPaymentId());
        verify(repository).save(purchase);
    }

    @Test
    void given_missing_purchase_when_attach_payment_then_throws_not_found() {
        // given
        PendingPurchasePersistenceService service = new PendingPurchasePersistenceService(repository);
        when(repository.findWithLockingById("purchase-1")).thenReturn(Optional.empty());

        // when / then
        assertThrows(NotFoundException.class, () -> service.attachPayment("purchase-1", "payment-1"));
    }

    @Test
    void given_different_existing_payment_when_attach_payment_then_throws_conflict() {
        // given
        PendingPurchasePersistenceService service = new PendingPurchasePersistenceService(repository);
        PendingPurchaseEntity purchase = purchase();
        purchase.setPaymentId("payment-1");
        when(repository.findWithLockingById("purchase-1")).thenReturn(Optional.of(purchase));

        // when / then
        assertThrows(IllegalStateException.class, () -> service.attachPayment("purchase-1", "payment-2"));
    }

    @Test
    void given_matching_purchase_intent_when_require_same_intent_then_allows() {
        // given
        PendingPurchaseEntity purchase = purchase();

        // when / then
        assertDoesNotThrow(() -> PendingPurchasePersistenceService.requireSameIntent(
                purchase, member(), "STRIPE", plan(PlanType.MONTHLY, 30), PlanType.MONTHLY));
    }

    @Test
    void given_different_purchase_intent_when_require_same_intent_then_throws() {
        // given
        PendingPurchaseEntity purchase = purchase();
        purchase.setProvider("VNPAY");

        // when / then
        assertThrows(
                IllegalArgumentException.class,
                () -> PendingPurchasePersistenceService.requireSameIntent(
                        purchase, member(), "STRIPE", plan(PlanType.MONTHLY, 30), PlanType.MONTHLY));
    }

    private static MemberDto member() {
        return new MemberDto(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Buyer",
                null,
                null,
                null,
                MembershipStatus.NONE,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static ResolvePurchasablePlanResponse plan(PlanType type, Integer durationDays) {
        ResolvePurchasablePlanResponse.Builder builder = ResolvePurchasablePlanResponse.newBuilder()
                .setPlanId("plan-1")
                .setGymId("gym-1")
                .setPlanType(switch (type) {
                    case MONTHLY -> com.gym.proto.common.v1.PlanType.PLAN_TYPE_MONTHLY;
                    case YEARLY -> com.gym.proto.common.v1.PlanType.PLAN_TYPE_YEARLY;
                    case LIFETIME -> com.gym.proto.common.v1.PlanType.PLAN_TYPE_LIFETIME;
                })
                .setPriceVnd(450_000L);
        if (durationDays != null) {
            builder.setDurationDays(durationDays);
        }
        return builder.build();
    }

    private static PendingPurchaseEntity purchase() {
        PendingPurchaseEntity purchase = new PendingPurchaseEntity();
        purchase.setId("purchase-1");
        purchase.setMemberId(member().id().toString());
        purchase.setUserId("user-1");
        purchase.setGymId("gym-1");
        purchase.setPlanId("plan-1");
        purchase.setPlanTypeSnapshot(PlanType.MONTHLY);
        purchase.setDurationDaysSnapshot(30);
        purchase.setPriceVndSnapshot(450_000L);
        purchase.setProvider("STRIPE");
        purchase.setIdempotencyKey("key-1");
        purchase.setStatus(PurchaseStatus.PENDING);
        return purchase;
    }
}
