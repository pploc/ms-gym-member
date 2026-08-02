package com.gym.member.application.port.in;

import com.gym.member.domain.dto.SubscriptionDto;

public interface SubscriptionActivationUseCase {
    SubscriptionDto activateOrRenewSubscription(String memberId, String planId);
}
