package com.gym.member.member.domain.event;

import java.time.Instant;
import java.util.UUID;

public record MembershipExpiredEvent(
        UUID memberId,
        Instant expiredAt,
        UUID gymId
) {}
