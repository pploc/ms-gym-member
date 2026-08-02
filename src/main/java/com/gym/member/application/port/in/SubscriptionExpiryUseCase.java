package com.gym.member.application.port.in;

public interface SubscriptionExpiryUseCase {
    void processExpiredSubscriptions();
    void processExpiringSoonWarnings();
    void suspendMemberAndSubscription(String userId);
}
