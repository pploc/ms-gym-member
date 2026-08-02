package com.gym.member.member.application.service.strategy;

import com.gym.member.member.application.port.in.MemberUseCase;
import com.gym.member.member.application.port.in.SubscriptionActivationUseCase;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipPaymentHandler implements PaymentTypeHandler {

    private final MemberUseCase memberUseCase;
    private final SubscriptionActivationUseCase subscriptionActivationUseCase;

    @Override
    public boolean supports(String paymentType) {
        return "MEMBERSHIP".equalsIgnoreCase(paymentType);
    }

    @Override
    public void handle(PaymentCompletedEvent event, String fallbackKey) {
        String userId = event.getUserId().isBlank() ? fallbackKey : event.getUserId();
        String planId = event.getReferenceId();
        if (userId == null || userId.isBlank() || planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("PaymentCompletedEvent missing userId or planId (userId=" + userId + ", planId=" + planId + ")");
        }

        MemberDto member = memberUseCase.getMemberByUserId(userId);
        subscriptionActivationUseCase.activateOrRenewSubscription(member.id().toString(), planId);
        log.info("Successfully processed payment completed event for member: {}", member.id());
    }
}
