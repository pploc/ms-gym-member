package com.gym.member.application.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.adapter.out.persistence.entity.MembershipPlanEntity;
import com.gym.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.adapter.out.persistence.repository.MembershipPlanJpaRepository;
import com.gym.member.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.gym.member.application.port.in.SubscriptionActivationUseCase;
import com.gym.member.config.MemberProperties;
import com.gym.member.domain.dto.SubscriptionDto;
import com.gym.member.domain.exception.PlanSwitchNotAllowedException;
import com.gym.member.domain.model.MembershipStatus;
import com.gym.member.domain.model.PlanType;
import com.gym.member.mapper.SubscriptionMapper;
import com.gym.proto.events.v1.MembershipActivatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionActivationService implements SubscriptionActivationUseCase {

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
}
