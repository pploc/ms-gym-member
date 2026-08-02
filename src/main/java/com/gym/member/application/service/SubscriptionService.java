package com.gym.member.application.service;

import com.gym.member.domain.dto.SubscriptionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionActivationService activationService;
    private final SubscriptionLifecycleService lifecycleService;
    private final SubscriptionExpiryService expiryService;

    public SubscriptionDto activateOrRenewSubscription(String memberId, String planId) {
        return activationService.activateOrRenewSubscription(memberId, planId);
    }

    public SubscriptionDto pauseSubscription(String memberId) {
        return lifecycleService.pauseSubscription(memberId);
    }

    public SubscriptionDto resumeSubscription(String memberId) {
        return lifecycleService.resumeSubscription(memberId);
    }

    public SubscriptionDto getActiveSubscription(String memberId) {
        return lifecycleService.getActiveSubscription(memberId);
    }

    public void processExpiredSubscriptions() {
        expiryService.processExpiredSubscriptions();
    }

    public void processExpiringSoonWarnings() {
        expiryService.processExpiringSoonWarnings();
    }

    public void suspendMemberAndSubscription(String userId) {
        expiryService.suspendMemberAndSubscription(userId);
    }
}
