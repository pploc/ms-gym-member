package com.gym.member.member.application.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.config.MemberProperties;
import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.member.adapter.out.persistence.mapper.SubscriptionMapper;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.gym.member.member.application.port.in.SubscriptionLifecycleUseCase;
import com.gym.member.member.domain.constant.MemberEventTopics;
import com.gym.member.member.domain.dto.SubscriptionDto;
import com.gym.member.member.domain.exception.CannotPauseLifetimeException;
import com.gym.member.member.domain.exception.MaxPausesExceededException;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.member.member.domain.model.PlanType;
import com.gym.member.shared.outbox.service.OutboxEventWriter;
import com.gym.proto.events.v1.MembershipPausedEvent;
import com.gym.proto.events.v1.MembershipResumedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionLifecycleService implements SubscriptionLifecycleUseCase {

    private final SubscriptionJpaRepository subscriptionRepository;
    private final MemberJpaRepository memberRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final MembershipEventFactory eventFactory;
    private final MemberProperties memberProperties;
    private final SubscriptionMapper subscriptionMapper;
    private final Clock clock;

    @Transactional
    public SubscriptionDto pauseSubscription(String memberId, String gymId) {
        MemberEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));

        SubscriptionEntity sub = subscriptionRepository
                .findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException(
                        "Active subscription not found for member: " + memberId + " at gym: " + gymId));

        if (sub.getPlanTypeSnapshot() == PlanType.LIFETIME) {
            throw new CannotPauseLifetimeException("LIFETIME subscriptions cannot be paused.");
        }

        int maxPauses = memberProperties.subscription().maxPauseCount();
        if (sub.getPauseCount() >= maxPauses) {
            throw new MaxPausesExceededException(
                    "Maximum allowed pauses (" + maxPauses + ") reached for this subscription cycle.");
        }

        LocalDate today = LocalDate.now(clock);
        int remainingDays = (sub.getEndDate() != null)
                ? (int) java.time.temporal.ChronoUnit.DAYS.between(today, sub.getEndDate())
                : 0;
        if (remainingDays < 0) {
            remainingDays = 0;
        }

        sub.setStatus(MembershipStatus.PAUSED);
        sub.setPausedAt(today);
        sub.setRemainingDays(remainingDays);
        sub.setPauseCount(sub.getPauseCount() + 1);

        SubscriptionEntity savedSub = subscriptionRepository.save(sub);

        MembershipPausedEvent event = eventFactory.createPausedEvent(member, savedSub, remainingDays, today);
        outboxEventWriter.write(
                MemberEventTopics.AGGREGATE_TYPE_MEMBER,
                member.getId(),
                MemberEventTopics.MEMBERSHIP_PAUSED,
                event);

        return subscriptionMapper.toDto(savedSub);
    }

    @Transactional
    public SubscriptionDto resumeSubscription(String memberId, String gymId) {
        MemberEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));

        SubscriptionEntity sub = subscriptionRepository
                .findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.PAUSED)
                .orElseThrow(() -> new NotFoundException(
                        "Paused subscription not found for member: " + memberId + " at gym: " + gymId));

        LocalDate today = LocalDate.now(clock);
        int remainingDays = sub.getRemainingDays() != null ? sub.getRemainingDays() : 0;
        LocalDate newEndDate = today.plusDays(remainingDays);

        sub.setStatus(MembershipStatus.ACTIVE);
        sub.setEndDate(newEndDate);
        sub.setPausedAt(null);
        sub.setRemainingDays(null);

        SubscriptionEntity savedSub = subscriptionRepository.save(sub);

        MembershipResumedEvent event = eventFactory.createResumedEvent(member, savedSub, newEndDate);
        outboxEventWriter.write(
                MemberEventTopics.AGGREGATE_TYPE_MEMBER,
                member.getId(),
                MemberEventTopics.MEMBERSHIP_RESUMED,
                event);

        return subscriptionMapper.toDto(savedSub);
    }

    @Transactional(readOnly = true)
    public SubscriptionDto getActiveSubscription(String memberId, String gymId) {
        SubscriptionEntity sub = subscriptionRepository
                .findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.ACTIVE)
                .or(() -> subscriptionRepository.findByMemberIdAndGymIdAndStatus(
                        memberId, gymId, MembershipStatus.PAUSED))
                .orElseThrow(() -> new NotFoundException(
                        "No active or paused subscription found for member: " + memberId + " at gym: " + gymId));
        return subscriptionMapper.toDto(sub);
    }

    @Transactional(readOnly = true)
    public SubscriptionDto getMembershipStatusByUserIdAndGymId(String userId, String gymId) {
        MemberEntity member = memberRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Member not found for user: " + userId));

        Optional<SubscriptionEntity> subOpt = subscriptionRepository
                .findByMemberIdAndGymIdAndStatus(member.getId(), gymId, MembershipStatus.ACTIVE)
                .or(() -> subscriptionRepository.findByMemberIdAndGymIdAndStatus(
                        member.getId(), gymId, MembershipStatus.PAUSED));

        if (subOpt.isPresent()) {
            return subscriptionMapper.toDto(subOpt.get());
        }

        return new SubscriptionDto(
                null,
                UUID.fromString(member.getId()),
                UUID.fromString(gymId),
                null,
                MembershipStatus.NONE,
                null,
                null,
                null,
                0,
                0);
    }
}
