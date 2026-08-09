package com.gym.member.member.application.service;

import com.gym.member.member.adapter.out.persistence.entity.PendingPurchaseEntity;
import com.gym.member.member.adapter.out.persistence.repository.PendingPurchaseJpaRepository;
import com.gym.member.member.application.port.in.MemberUseCase;
import com.gym.member.member.application.port.in.MembershipPurchaseUseCase;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.model.PlanType;
import com.gym.member.member.domain.model.PurchaseStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipPurchaseService implements MembershipPurchaseUseCase {

    private final MemberUseCase memberUseCase;
    private final PlansGrpcClient plansGrpcClient;
    private final PaymentGrpcClient paymentGrpcClient;
    private final PendingPurchaseJpaRepository pendingPurchaseRepository;

    @Override
    @Transactional
    public PurchaseMembershipResponse purchaseMembership(
            String userId, String gymId, String planId, String provider, String discountCode) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (gymId == null || gymId.isBlank()) {
            throw new IllegalArgumentException("selected gym_id is required");
        }
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("plan_id is required");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        if (discountCode != null && !discountCode.isBlank()) {
            throw new IllegalArgumentException("discount codes are not supported until authoritative discount pricing exists");
        }

        MemberDto member = memberUseCase.getMemberByUserId(userId);
        ResolvePurchasablePlanResponse resolved = plansGrpcClient.resolvePurchasablePlan(planId, gymId);
        PlanType planType = ProtoEnums.toDomain(resolved.getPlanType());

        PendingPurchaseEntity purchase = new PendingPurchaseEntity();
        purchase.setMemberId(member.id().toString());
        purchase.setUserId(userId);
        purchase.setGymId(resolved.getGymId());
        purchase.setPlanId(resolved.getPlanId());
        purchase.setPlanTypeSnapshot(planType);
        purchase.setDurationDaysSnapshot(resolved.hasDurationDays() ? resolved.getDurationDays() : null);
        purchase.setPriceVndSnapshot(resolved.getPriceVnd());
        purchase.setProvider(provider);
        purchase.setStatus(PurchaseStatus.PENDING);
        PendingPurchaseEntity saved = pendingPurchaseRepository.save(purchase);

        InitiatePaymentResponse payment = paymentGrpcClient.initiatePayment(InitiatePaymentRequest.newBuilder()
                .setGymId(resolved.getGymId())
                .setPaymentType(PaymentType.PAYMENT_TYPE_MEMBERSHIP)
                .setReferenceId(saved.getId())
                .setProvider(provider)
                .setUserId(userId)
                .setAmountVnd(resolved.getPriceVnd())
                .build());

        saved.setPaymentId(payment.getPaymentId());
        pendingPurchaseRepository.save(saved);

        log.info("Created pending purchase {} for member {} plan {}", saved.getId(), member.id(), resolved.getPlanId());
        return PurchaseMembershipResponse.newBuilder()
                .setPaymentId(payment.getPaymentId())
                .setPaymentUrl(payment.getPaymentUrl())
                .build();
    }
}
