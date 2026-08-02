package com.gym.member.member.application.service;

import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.adapter.out.persistence.entity.MembershipPlanEntity;
import com.gym.member.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.proto.events.v1.MembershipActivatedEvent;
import com.gym.proto.events.v1.MembershipExpiredEvent;
import com.gym.proto.events.v1.MembershipExpiringSoonEvent;
import com.gym.proto.events.v1.MembershipPausedEvent;
import com.gym.proto.events.v1.MembershipResumedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class MembershipEventFactory {

    private final Clock clock;

    public MembershipEventFactory() {
        this(Clock.systemUTC());
    }

    public MembershipActivatedEvent createActivatedEvent(MemberEntity member, SubscriptionEntity sub, MembershipPlanEntity plan, boolean isRenewal, Clock clock) {
        Clock useClock = clock != null ? clock : this.clock;
        return MembershipActivatedEvent.newBuilder()
                .setMemberId(member.getId())
                .setUserId(member.getUserId())
                .setPlanType(plan.getPlanType().name())
                .setStartDate(sub.getStartDate().toString())
                .setEndDate(sub.getEndDate() != null ? sub.getEndDate().toString() : "")
                .setGymId(member.getGymId())
                .setIsRenewal(isRenewal)
                .setTimestamp(Instant.now(useClock).toEpochMilli())
                .build();
    }

    public MembershipPausedEvent createPausedEvent(MemberEntity member, int remainingDays, LocalDate today) {
        return MembershipPausedEvent.newBuilder()
                .setMemberId(member.getId())
                .setPausedAt(today.atStartOfDay(clock.getZone()).toInstant().toEpochMilli())
                .setRemainingDays(remainingDays)
                .setGymId(member.getGymId())
                .build();
    }

    public MembershipResumedEvent createResumedEvent(MemberEntity member, LocalDate newEndDate) {
        return MembershipResumedEvent.newBuilder()
                .setMemberId(member.getId())
                .setNewEndDate(newEndDate.toString())
                .setGymId(member.getGymId())
                .build();
    }

    public MembershipExpiringSoonEvent createExpiringSoonEvent(MemberEntity member, SubscriptionEntity sub, MembershipPlanEntity plan) {
        return MembershipExpiringSoonEvent.newBuilder()
                .setMemberId(member.getId())
                .setEndDate(sub.getEndDate() != null ? sub.getEndDate().toString() : "")
                .setPlanType(plan.getPlanType().name())
                .setGymId(member.getGymId())
                .build();
    }

    public MembershipExpiredEvent createExpiredEvent(MemberEntity member, Clock clock) {
        Clock useClock = clock != null ? clock : this.clock;
        return MembershipExpiredEvent.newBuilder()
                .setMemberId(member.getId())
                .setExpiredAt(Instant.now(useClock).toEpochMilli())
                .setGymId(member.getGymId())
                .build();
    }
}
