package com.gym.member.application.service;

import com.gym.common.error.DomainException;
import com.gym.common.error.NotFoundException;
import com.gym.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.adapter.out.persistence.entity.MembershipPlanEntity;
import com.gym.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.adapter.out.persistence.repository.MembershipPlanJpaRepository;
import com.gym.member.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.gym.member.adapter.out.persistence.specification.SubscriptionSpecifications;
import com.gym.member.config.MemberProperties;
import com.gym.member.domain.dto.SubscriptionDto;
import com.gym.member.domain.exception.CannotPauseLifetimeException;
import com.gym.member.domain.exception.MaxPausesExceededException;
import com.gym.member.domain.exception.PlanSwitchNotAllowedException;
import com.gym.member.domain.model.MembershipStatus;
import com.gym.member.domain.model.PlanType;
import com.gym.member.mapper.SubscriptionMapper;
import com.gym.proto.events.v1.MembershipActivatedEvent;
import com.gym.proto.events.v1.MembershipExpiredEvent;
import com.gym.proto.events.v1.MembershipExpiringSoonEvent;
import com.gym.proto.events.v1.MembershipPausedEvent;
import com.gym.proto.events.v1.MembershipResumedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionJpaRepository subscriptionRepository;
    private final MemberJpaRepository memberRepository;
    private final MembershipPlanJpaRepository planRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final MembershipEventFactory eventFactory;
    private final MemberProperties memberProperties;
    private final SubscriptionMapper subscriptionMapper;
    private final Clock clock;

    @Transactional
    public SubscriptionDto activateOrRenewSubscription(String memberId, String planId) {
        MemberEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));

        MembershipPlanEntity plan = planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("Membership plan not found: " + planId));

        if (!plan.isActive()) {
            throw new IllegalArgumentException("Membership plan is inactive: " + planId);
        }
        if (!member.getGymId().equals(plan.getGymId())) {
            throw new IllegalArgumentException("Membership plan does not belong to the member's gym");
        }

        LocalDate today = LocalDate.now(clock);

        Optional<SubscriptionEntity> currentSubOpt = subscriptionRepository.findCurrentForUpdate(
                memberId,
                List.of(MembershipStatus.ACTIVE, MembershipStatus.PAUSED)
        );

        SubscriptionEntity sub;
        boolean isRenewal = false;

        if (currentSubOpt.isPresent()) {
            sub = currentSubOpt.get();
            if (!sub.getPlanId().equals(planId)) {
                throw new PlanSwitchNotAllowedException("Cannot switch plan on an active or paused subscription; plan changes are permitted only after expiration.");
            }

            isRenewal = true;
            int defaultDays = memberProperties.subscription().defaultDurationDays();
            if (plan.getPlanType() == PlanType.LIFETIME) {
                sub.setEndDate(null);
            } else if (sub.getEndDate() != null && sub.getEndDate().isAfter(today)) {
                sub.setEndDate(sub.getEndDate().plusDays(plan.getDurationDays() != null ? plan.getDurationDays() : defaultDays));
            } else {
                sub.setStartDate(today);
                sub.setEndDate(today.plusDays(plan.getDurationDays() != null ? plan.getDurationDays() : defaultDays));
            }
            sub.setPausedAt(null);
            sub.setRemainingDays(null);
        } else {
            int defaultDays = memberProperties.subscription().defaultDurationDays();
            sub = new SubscriptionEntity();
            sub.setMemberId(memberId);
            sub.setPlanId(planId);
            sub.setStatus(MembershipStatus.ACTIVE);
            sub.setStartDate(today);
            sub.setEndDate(plan.getPlanType() == PlanType.LIFETIME ? null : today.plusDays(plan.getDurationDays() != null ? plan.getDurationDays() : defaultDays));
        }

        sub.setStatus(MembershipStatus.ACTIVE);
        SubscriptionEntity savedSub = subscriptionRepository.save(sub);

        member.setStatus(MembershipStatus.ACTIVE);
        memberRepository.save(member);

        MembershipActivatedEvent event = eventFactory.createActivatedEvent(member, savedSub, plan, isRenewal, clock);
        outboxEventWriter.write("member", member.getId(), "membership.activated", event);

        return subscriptionMapper.toDto(savedSub);
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
        outboxEventWriter.write("member", member.getId(), "membership.paused", event);

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
        outboxEventWriter.write("member", member.getId(), "membership.resumed", event);

        return subscriptionMapper.toDto(savedSub);
    }

    @Transactional(readOnly = true)
    public SubscriptionDto getActiveSubscription(String memberId) {
        SubscriptionEntity sub = subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)
                .or(() -> subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.PAUSED))
                .orElseThrow(() -> new NotFoundException("No active or paused subscription found for member: " + memberId));
        return subscriptionMapper.toDto(sub);
    }

    @Transactional
    public void processExpiredSubscriptions() {
        LocalDate today = LocalDate.now(clock);
        List<SubscriptionEntity> expiredSubs = subscriptionRepository.findAll(
                SubscriptionSpecifications.isExpiredActive(today)
        );
        if (expiredSubs.isEmpty()) return;

        Set<String> memberIds = expiredSubs.stream().map(SubscriptionEntity::getMemberId).collect(java.util.stream.Collectors.toSet());
        List<MemberEntity> members = memberRepository.findAllById(memberIds);
        java.util.Map<String, MemberEntity> memberMap = members.stream().collect(java.util.stream.Collectors.toMap(MemberEntity::getId, m -> m));

        for (SubscriptionEntity sub : expiredSubs) {
            sub.setStatus(MembershipStatus.EXPIRED);
            subscriptionRepository.save(sub);

            MemberEntity member = memberMap.get(sub.getMemberId());
            if (member != null) {
                member.setStatus(MembershipStatus.EXPIRED);
                memberRepository.save(member);

                MembershipExpiredEvent event = eventFactory.createExpiredEvent(member, clock);
                outboxEventWriter.write("member", member.getId(), "membership.expired", event);
            }
        }
    }

    @Transactional(readOnly = true)
    public void processExpiringSoonWarnings() {
        int warningDays = memberProperties.subscription().warningNoticeDays();
        LocalDate warningDate = LocalDate.now(clock).plusDays(warningDays);
        List<SubscriptionEntity> warningSubs = subscriptionRepository.findAll(
                SubscriptionSpecifications.isExpiringSoon(warningDate)
        );
        if (warningSubs.isEmpty()) return;

        Set<String> memberIds = warningSubs.stream().map(SubscriptionEntity::getMemberId).collect(java.util.stream.Collectors.toSet());
        Set<String> planIds = warningSubs.stream().map(SubscriptionEntity::getPlanId).collect(java.util.stream.Collectors.toSet());

        java.util.Map<String, MemberEntity> memberMap = memberRepository.findAllById(memberIds).stream()
                .collect(java.util.stream.Collectors.toMap(MemberEntity::getId, m -> m));
        java.util.Map<String, MembershipPlanEntity> planMap = planRepository.findAllById(planIds).stream()
                .collect(java.util.stream.Collectors.toMap(MembershipPlanEntity::getId, p -> p));

        for (SubscriptionEntity sub : warningSubs) {
            MemberEntity member = memberMap.get(sub.getMemberId());
            MembershipPlanEntity plan = planMap.get(sub.getPlanId());
            if (member != null && plan != null) {
                MembershipExpiringSoonEvent event = eventFactory.createExpiringSoonEvent(member, sub, plan);
                outboxEventWriter.write("member", member.getId(), "membership.expiring-soon", event);
            }
        }
    }

    @Transactional
    public void suspendMemberAndSubscription(String userId) {
        Optional<MemberEntity> memberOpt = memberRepository.findByUserId(userId);
        if (memberOpt.isEmpty()) {
            log.warn("Cannot suspend member: No member found for userId: {}", userId);
            return;
        }

        MemberEntity member = memberOpt.get();
        member.setStatus(MembershipStatus.EXPIRED);
        memberRepository.save(member);

        Optional<SubscriptionEntity> activeSubOpt = subscriptionRepository.findByMemberIdAndStatus(member.getId(), MembershipStatus.ACTIVE)
                .or(() -> subscriptionRepository.findByMemberIdAndStatus(member.getId(), MembershipStatus.PAUSED));

        if (activeSubOpt.isPresent()) {
            SubscriptionEntity sub = activeSubOpt.get();
            sub.setStatus(MembershipStatus.EXPIRED);
            subscriptionRepository.save(sub);
            log.info("Cancelled subscription id {} for suspended user: {}", sub.getId(), userId);

            MembershipExpiredEvent event = eventFactory.createExpiredEvent(member, clock);
            outboxEventWriter.write("member", member.getId(), "membership.expired", event);
        }

        log.info("Successfully suspended member id {} for userId: {}", member.getId(), userId);
    }
}
