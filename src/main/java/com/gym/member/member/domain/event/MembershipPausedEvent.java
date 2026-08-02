package com.gym.member.member.domain.event;

import java.time.LocalDate;
import java.util.UUID;

public record MembershipPausedEvent(
        UUID memberId,
        LocalDate pausedAt,
        int remainingDays,
        UUID gymId
) {}
