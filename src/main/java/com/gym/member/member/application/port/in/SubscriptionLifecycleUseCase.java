package com.gym.member.member.application.port.in;

import com.gym.member.member.domain.dto.SubscriptionDto;

public interface SubscriptionLifecycleUseCase {
    SubscriptionDto pauseSubscription(String memberId, String gymId);
    SubscriptionDto resumeSubscription(String memberId, String gymId);
    SubscriptionDto getActiveSubscription(String memberId, String gymId);
}
