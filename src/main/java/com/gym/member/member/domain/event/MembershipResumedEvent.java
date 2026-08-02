package com.gym.member.member.domain.event;

import java.time.LocalDate;
import java.util.UUID;

public record MembershipResumedEvent(
        UUID memberId,
        LocalDate newEndDate,
        UUID gymId
) {}
