package com.gym.member.member.application.service;

import com.gym.member.member.adapter.out.persistence.entity.PendingPurchaseEntity;
import com.gym.member.member.application.port.in.MemberUseCase;
import com.gym.member.member.application.port.in.MembershipPurchaseUseCase;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.model.PlanType;
import com.gym.member.payment.adapter.out.grpc.PaymentGrpcClient;
import com.gym.member.plans.adapter.out.grpc.PlansGrpcClient;
import com.gym.member.shared.mapper.ProtoEnums;
import com.gym.proto.common.v1.PaymentType;
import com.gym.proto.member.v1.PurchaseMembershipResponse;
import com.gym.proto.payment.v1.InitiatePaymentRequest;
import com.gym.proto.payment.v1.InitiatePaymentResponse;
import com.gym.proto.plans.v1.ResolvePurchasablePlanResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipPurchaseService implements MembershipPurchaseUseCase {

    private final MemberUseCase memberUseCase;
    private final PlansGrpcClient plansGrpcClient;
    private final PaymentGrpcClient paymentGrpcClient;
    private final PendingPurchasePersistenceService persistenceService;

    @Override
    public PurchaseMembershipResponse purchaseMembership(
            String userId,
            String gymId,
            String planId,
            String provider,
            String discountCode,
            String idempotencyKey) {
        requireNonBlank(userId, "userId is required");
        requireNonBlank(gymId, "selected gym_id is required");
        requireNonBlank(planId, "plan_id is required");
        requireNonBlank(provider, "provider is required");
        String normalizedProvider = provider.trim().toUpperCase(Locale.ROOT);
        if (!"SEPAY".equals(normalizedProvider)) {
            throw new IllegalArgumentException("unsupported payment provider: " + normalizedProvider);
        }
        requireNonBlank(idempotencyKey, "idempotency_key is required");
        if (discountCode != null && !discountCode.isBlank()) {
            throw new IllegalArgumentException("discount codes are not supported until authoritative discount pricing exists");
        }

        String normalizedKey = idempotencyKey.trim();
        MemberDto member = memberUseCase.getMemberByUserId(userId);
        PendingPurchaseEntity purchase;
        try {
            purchase = persistenceService.load(userId, normalizedKey);
            requireSameRequest(purchase, member, gymId, planId, normalizedProvider);
        } catch (com.gym.common.error.NotFoundException ignored) {
            ResolvePurchasablePlanResponse resolved = plansGrpcClient.resolvePurchasablePlan(planId, gymId);
            PlanType planType = ProtoEnums.toDomain(resolved.getPlanType());
            try {
                purchase = persistenceService.create(member, userId, normalizedProvider, normalizedKey, resolved, planType);
            } catch (DataIntegrityViolationException concurrentCreate) {
                purchase = persistenceService.load(userId, normalizedKey);
                PendingPurchasePersistenceService.requireSameIntent(purchase, member, normalizedProvider, resolved, planType);
            }
        }

        InitiatePaymentResponse payment = paymentGrpcClient.initiatePayment(InitiatePaymentRequest.newBuilder()
                .setGymId(purchase.getGymId())
                .setPaymentType(PaymentType.PAYMENT_TYPE_MEMBERSHIP)
                .setReferenceId(purchase.getId())
                .setProvider(purchase.getProvider())
                .setUserId(userId)
                .setAmountVnd(purchase.getPriceVndSnapshot())
                .build());

        persistenceService.attachPayment(purchase.getId(), payment.getPaymentId());
        log.info("Created or reused pending purchase {} for member {} plan {}", purchase.getId(), member.id(), purchase.getPlanId());
        return PurchaseMembershipResponse.newBuilder()
                .setPaymentId(payment.getPaymentId())
                .setPaymentUrl(payment.getPaymentUrl())
                .build();
    }

    private static void requireSameRequest(
            PendingPurchaseEntity purchase, MemberDto member, String gymId, String planId, String provider) {
        if (!Objects.equals(purchase.getMemberId(), member.id().toString())
                || !Objects.equals(purchase.getGymId(), gymId)
                || !Objects.equals(purchase.getPlanId(), planId)
                || !Objects.equals(purchase.getProvider(), provider)) {
            throw new IllegalArgumentException("Idempotency key was already used for a different purchase");
        }
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
