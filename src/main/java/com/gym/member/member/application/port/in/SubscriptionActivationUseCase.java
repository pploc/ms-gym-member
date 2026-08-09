package com.gym.member.member.application.port.in;

import com.gym.member.member.domain.dto.PurchasedPlanTerms;
import com.gym.member.member.domain.dto.SubscriptionDto;

public interface SubscriptionActivationUseCase {
    SubscriptionDto activateOrRenewSubscription(String memberId, PurchasedPlanTerms terms);
}
