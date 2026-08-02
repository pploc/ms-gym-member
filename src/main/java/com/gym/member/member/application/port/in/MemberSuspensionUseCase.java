package com.gym.member.member.application.port.in;

public interface MemberSuspensionUseCase {
    void suspendMemberAndSubscription(String userId);
}
