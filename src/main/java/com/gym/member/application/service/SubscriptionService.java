package com.gym.member.application.service;

import com.gym.member.application.port.in.SubscriptionActivationUseCase;
import com.gym.member.application.port.in.SubscriptionExpiryUseCase;
import com.gym.member.application.port.in.SubscriptionLifecycleUseCase;
import com.gym.member.domain.dto.SubscriptionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubscriptionService implements SubscriptionActivationUseCase, SubscriptionLifecycleUseCase, SubscriptionExpiryUseCase {

    private final SubscriptionActivationUseCase activationUseCase;
    private final SubscriptionLifecycleUseCase lifecycleUseCase;
    private final SubscriptionExpiryUseCase expiryUseCase;

    @Override
    public SubscriptionDto activateOrRenewSubscription(String memberId, String planId) {
        return activationUseCase.activateOrRenewSubscription(memberId, planId);
    }

    @Override
    public SubscriptionDto pauseSubscription(String memberId) {
        return lifecycleUseCase.pauseSubscription(memberId);
    }

    @Override
    public SubscriptionDto resumeSubscription(String memberId) {
        return lifecycleUseCase.resumeSubscription(memberId);
    }

    @Override
    public SubscriptionDto getActiveSubscription(String memberId) {
        return lifecycleUseCase.getActiveSubscription(memberId);
    }

    @Override
    public void processExpiredSubscriptions() {
        expiryUseCase.processExpiredSubscriptions();
    }

    @Override
    public void processExpiringSoonWarnings() {
        expiryUseCase.processExpiringSoonWarnings();
    }

    @Override
    public void suspendMemberAndSubscription(String userId) {
        expiryUseCase.suspendMemberAndSubscription(userId);
    }
}
