package com.gym.member.member.application.port.in;

import com.gym.proto.member.v1.PurchaseMembershipResponse;

public interface MembershipPurchaseUseCase {
    PurchaseMembershipResponse purchaseMembership(String userId, String gymId, String planId, String provider, String discountCode);
}
