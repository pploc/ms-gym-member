package com.gym.member.member.application.service;

import com.gym.member.shared.outbox.service.OutboxEventWriter;

import com.gym.common.error.NotFoundException;
import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.adapter.out.persistence.entity.MembershipPlanEntity;
import com.gym.member.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.MembershipPlanJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.gym.member.member.application.port.in.SubscriptionLifecycleUseCase;
import com.gym.member.config.MemberProperties;
import com.gym.member.member.domain.constant.MemberEventTopics;
import com.gym.member.member.domain.dto.SubscriptionDto;
import com.gym.member.member.domain.exception.CannotPauseLifetimeException;
import com.gym.member.member.domain.exception.MaxPausesExceededException;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.member.member.domain.model.PlanType;
import com.gym.member.member.adapter.out.persistence.mapper.SubscriptionMapper;
import com.gym.proto.events.v1.MembershipPausedEvent;
import com.gym.proto.events.v1.MembershipResumedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionLifecycleService implements SubscriptionLifecycleUseCase {

    private final SubscriptionJpaRepository subscriptionRepository;
    private final MemberJpaRepository memberRepository;
    private final MembershipPlanJpaRepository planRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final MembershipEventFactory eventFactory;
    private final MemberProperties memberProperties;
    private final SubscriptionMapper subscriptionMapper;
    private final Clock clock;

    @Transactional
    public SubscriptionDto pauseSubscription(String memberId) {
        MemberEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));

        SubscriptionEntity sub = subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active subscription not found for member: " + memberId));

        MembershipPlanEntity plan = planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new NotFoundException("Plan not found: " + sub.getPlanId()));

        if (plan.getPlanType() == PlanType.LIFETIME) {
            throw new CannotPauseLifetimeException("LIFETIME subscriptions cannot be paused.");
        }

        int maxPauses = memberProperties.subscription().maxPauseCount();
        if (sub.getPauseCount() >= maxPauses) {
            throw new MaxPausesExceededException("Maximum allowed pauses (" + maxPauses + ") reached for this subscription cycle.");
        }

        LocalDate today = LocalDate.now(clock);
        int remainingDays = (sub.getEndDate() != null) ? (int) java.time.temporal.ChronoUnit.DAYS.between(today, sub.getEndDate()) : 0;
        if (remainingDays < 0) remainingDays = 0;

        sub.setStatus(MembershipStatus.PAUSED);
        sub.setPausedAt(today);
        sub.setRemainingDays(remainingDays);
        sub.setPauseCount(sub.getPauseCount() + 1);

        SubscriptionEntity savedSub = subscriptionRepository.save(sub);

        member.setStatus(MembershipStatus.PAUSED);
        memberRepository.save(member);

        MembershipPausedEvent event = eventFactory.createPausedEvent(member, remainingDays, today);
        outboxEventWriter.write(MemberEventTopics.AGGREGATE_TYPE_MEMBER, member.getId(), MemberEventTopics.MEMBERSHIP_PAUSED, event);

        return subscriptionMapper.toDto(savedSub);
    }

    @Transactional
    public SubscriptionDto resumeSubscription(String memberId) {
        MemberEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));

        SubscriptionEntity sub = subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.PAUSED)
                .orElseThrow(() -> new NotFoundException("Paused subscription not found for member: " + memberId));

        LocalDate today = LocalDate.now(clock);
        int remainingDays = sub.getRemainingDays() != null ? sub.getRemainingDays() : 0;
        LocalDate newEndDate = today.plusDays(remainingDays);

        sub.setStatus(MembershipStatus.ACTIVE);
        sub.setEndDate(newEndDate);
        sub.setPausedAt(null);
        sub.setRemainingDays(null);

        SubscriptionEntity savedSub = subscriptionRepository.save(sub);

        member.setStatus(MembershipStatus.ACTIVE);
        memberRepository.save(member);

        MembershipResumedEvent event = eventFactory.createResumedEvent(member, newEndDate);
        outboxEventWriter.write(MemberEventTopics.AGGREGATE_TYPE_MEMBER, member.getId(), MemberEventTopics.MEMBERSHIP_RESUMED, event);

        return subscriptionMapper.toDto(savedSub);
    }

    @Transactional(readOnly = true)
    public SubscriptionDto getActiveSubscription(String memberId) {
        SubscriptionEntity sub = subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)
                .or(() -> subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.PAUSED))
                .orElseThrow(() -> new NotFoundException("No active or paused subscription found for member: " + memberId));
        return subscriptionMapper.toDto(sub);
    }
}
