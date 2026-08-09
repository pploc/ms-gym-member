package com.gym.member.member.application.port.in;

import com.gym.proto.member.v1.PurchaseResponse;

public interface MembershipPurchaseUseCase {
    PurchaseResponse purchaseMembership(String userId, String gymId, String planId, String provider, String discountCode);
}
