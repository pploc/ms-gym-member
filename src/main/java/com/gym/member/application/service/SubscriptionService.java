package com.gym.member.application.service;

import com.gym.common.error.NotFoundException;
import com.gym.common.kafka.producer.EventPublisher;
import com.gym.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.adapter.out.persistence.entity.MembershipPlanEntity;
import com.gym.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.adapter.out.persistence.repository.MembershipPlanJpaRepository;
import com.gym.member.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.gym.member.domain.dto.SubscriptionDto;
import com.gym.member.domain.exception.CannotPauseLifetimeException;
import com.gym.member.domain.exception.MaxPausesExceededException;
import com.gym.member.domain.model.MembershipStatus;
import com.gym.member.domain.model.PlanType;
import com.gym.proto.events.v1.MembershipActivatedEvent;
import com.gym.proto.events.v1.MembershipExpiredEvent;
import com.gym.proto.events.v1.MembershipExpiringSoonEvent;
import com.gym.proto.events.v1.MembershipPausedEvent;
import com.gym.proto.events.v1.MembershipResumedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionJpaRepository subscriptionRepository;
    private final MemberJpaRepository memberRepository;
    private final MembershipPlanJpaRepository planRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public SubscriptionDto activateOrRenewSubscription(String memberId, String planId) {
        MemberEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));

        MembershipPlanEntity plan = planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("Membership plan not found: " + planId));

        LocalDate today = LocalDate.now();
        Optional<SubscriptionEntity> activeSubOpt = subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE);

        SubscriptionEntity sub;
        boolean isRenewal = false;

        if (activeSubOpt.isPresent()) {
            sub = activeSubOpt.get();
            isRenewal = true;

            if (plan.getPlanType() == PlanType.LIFETIME) {
                sub.setEndDate(null);
            } else if (sub.getEndDate() != null && sub.getEndDate().isAfter(today)) {
                sub.setEndDate(sub.getEndDate().plusDays(plan.getDurationDays() != null ? plan.getDurationDays() : 30));
            } else {
                sub.setStartDate(today);
                sub.setEndDate(today.plusDays(plan.getDurationDays() != null ? plan.getDurationDays() : 30));
            }
        } else {
            sub = new SubscriptionEntity();
            sub.setMemberId(memberId);
            sub.setPlanId(planId);
            sub.setStatus(MembershipStatus.ACTIVE);
            sub.setStartDate(today);
            sub.setEndDate(plan.getPlanType() == PlanType.LIFETIME ? null : today.plusDays(plan.getDurationDays() != null ? plan.getDurationDays() : 30));
        }

        sub.setStatus(MembershipStatus.ACTIVE);
        SubscriptionEntity savedSub = subscriptionRepository.save(sub);

        member.setStatus(MembershipStatus.ACTIVE);
        memberRepository.save(member);

        // Publish Event
        MembershipActivatedEvent event = MembershipActivatedEvent.newBuilder()
                .setMemberId(member.getId())
                .setUserId(member.getUserId())
                .setPlanType(plan.getPlanType().name())
                .setStartDate(savedSub.getStartDate().toString())
                .setEndDate(savedSub.getEndDate() != null ? savedSub.getEndDate().toString() : "")
                .setGymId(member.getGymId())
                .setIsRenewal(isRenewal)
                .setTimestamp(Instant.now().toEpochMilli())
                .build();

        eventPublisher.publish("membership.activated", member.getId(), event);

        return toDto(savedSub);
    }

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

        if (sub.getPauseCount() >= 2) {
            throw new MaxPausesExceededException("Maximum allowed pauses (2) reached for this subscription cycle.");
        }

        LocalDate today = LocalDate.now();
        int remainingDays = (sub.getEndDate() != null) ? (int) ChronoUnit.DAYS.between(today, sub.getEndDate()) : 0;
        if (remainingDays < 0) remainingDays = 0;

        sub.setStatus(MembershipStatus.PAUSED);
        sub.setPausedAt(today);
        sub.setRemainingDays(remainingDays);
        sub.setPauseCount(sub.getPauseCount() + 1);

        SubscriptionEntity savedSub = subscriptionRepository.save(sub);

        member.setStatus(MembershipStatus.PAUSED);
        memberRepository.save(member);

        // Publish Event
        MembershipPausedEvent event = MembershipPausedEvent.newBuilder()
                .setMemberId(member.getId())
                .setPausedAt(today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
                .setRemainingDays(remainingDays)
                .setGymId(member.getGymId())
                .build();

        eventPublisher.publish("membership.paused", member.getId(), event);

        return toDto(savedSub);
    }

    @Transactional
    public SubscriptionDto resumeSubscription(String memberId) {
        MemberEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));

        SubscriptionEntity sub = subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.PAUSED)
                .orElseThrow(() -> new NotFoundException("Paused subscription not found for member: " + memberId));

        LocalDate today = LocalDate.now();
        int remainingDays = sub.getRemainingDays() != null ? sub.getRemainingDays() : 0;
        LocalDate newEndDate = today.plusDays(remainingDays);

        sub.setStatus(MembershipStatus.ACTIVE);
        sub.setEndDate(newEndDate);
        sub.setPausedAt(null);
        sub.setRemainingDays(null);

        SubscriptionEntity savedSub = subscriptionRepository.save(sub);

        member.setStatus(MembershipStatus.ACTIVE);
        memberRepository.save(member);

        // Publish Event
        MembershipResumedEvent event = MembershipResumedEvent.newBuilder()
                .setMemberId(member.getId())
                .setNewEndDate(newEndDate.toString())
                .setGymId(member.getGymId())
                .build();

        eventPublisher.publish("membership.resumed", member.getId(), event);

        return toDto(savedSub);
    }

    @Transactional(readOnly = true)
    public SubscriptionDto getActiveSubscription(String memberId) {
        SubscriptionEntity sub = subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)
                .or(() -> subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.PAUSED))
                .orElseThrow(() -> new NotFoundException("No active or paused subscription found for member: " + memberId));
        return toDto(sub);
    }

    @Transactional
    public void processExpiredSubscriptions() {
        LocalDate today = LocalDate.now();
        List<SubscriptionEntity> expiredSubs = subscriptionRepository.findExpiredActiveSubscriptions(today);
        for (SubscriptionEntity sub : expiredSubs) {
            sub.setStatus(MembershipStatus.EXPIRED);
            subscriptionRepository.save(sub);

            Optional<MemberEntity> memberOpt = memberRepository.findById(sub.getMemberId());
            if (memberOpt.isPresent()) {
                MemberEntity member = memberOpt.get();
                member.setStatus(MembershipStatus.EXPIRED);
                memberRepository.save(member);

                MembershipExpiredEvent event = MembershipExpiredEvent.newBuilder()
                        .setMemberId(member.getId())
                        .setExpiredAt(Instant.now().toEpochMilli())
                        .setGymId(member.getGymId())
                        .build();

                eventPublisher.publish("membership.expired", member.getId(), event);
            }
        }
    }

    @Transactional(readOnly = true)
    public void processExpiringSoonWarnings() {
        LocalDate warningDate = LocalDate.now().plusDays(7);
        List<SubscriptionEntity> warningSubs = subscriptionRepository.findExpiringSoonSubscriptions(warningDate);
        for (SubscriptionEntity sub : warningSubs) {
            Optional<MemberEntity> memberOpt = memberRepository.findById(sub.getMemberId());
            Optional<MembershipPlanEntity> planOpt = planRepository.findById(sub.getPlanId());
            if (memberOpt.isPresent() && planOpt.isPresent()) {
                MemberEntity member = memberOpt.get();
                MembershipPlanEntity plan = planOpt.get();

                MembershipExpiringSoonEvent event = MembershipExpiringSoonEvent.newBuilder()
                        .setMemberId(member.getId())
                        .setEndDate(sub.getEndDate() != null ? sub.getEndDate().toString() : "")
                        .setPlanType(plan.getPlanType().name())
                        .setGymId(member.getGymId())
                        .build();

                eventPublisher.publish("membership.expiring-soon", member.getId(), event);
            }
        }
    }

    public SubscriptionDto toDto(SubscriptionEntity entity) {
        return new SubscriptionDto(
                UUID.fromString(entity.getId()),
                UUID.fromString(entity.getMemberId()),
                UUID.fromString(entity.getPlanId()),
                entity.getStatus(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getPausedAt(),
                entity.getRemainingDays(),
                entity.getPauseCount()
        );
    }
}
