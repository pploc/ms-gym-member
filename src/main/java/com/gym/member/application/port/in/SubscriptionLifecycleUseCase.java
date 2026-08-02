package com.gym.member.application.port.in;

import com.gym.member.domain.dto.SubscriptionDto;

public interface SubscriptionLifecycleUseCase {
    SubscriptionDto pauseSubscription(String memberId);
    SubscriptionDto resumeSubscription(String memberId);
    SubscriptionDto getActiveSubscription(String memberId);
}
