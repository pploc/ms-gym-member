package com.gym.member.member.application.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.config.MemberProperties;
import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.member.adapter.out.persistence.mapper.SubscriptionMapper;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.gym.member.member.application.port.in.SubscriptionActivationUseCase;
import com.gym.member.member.domain.constant.MemberEventTopics;
import com.gym.member.member.domain.dto.PurchasedPlanTerms;
import com.gym.member.member.domain.dto.SubscriptionDto;
import com.gym.member.member.domain.exception.PlanSwitchNotAllowedException;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.member.member.domain.model.PlanType;
import com.gym.member.shared.outbox.service.OutboxEventWriter;
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
    private final MemberStatusService memberStatusService;
    private final OutboxEventWriter outboxEventWriter;
    private final MembershipEventFactory eventFactory;
    private final MemberProperties memberProperties;
    private final SubscriptionMapper subscriptionMapper;
    private final Clock clock;

    @Override
    @Transactional
    public SubscriptionDto activateOrRenewSubscription(String memberId, PurchasedPlanTerms terms) {
        log.info("Activating or renewing subscription for member: {}, plan: {}", memberId, terms.planId());

        MemberEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));

        LocalDate today = LocalDate.now(clock);
        Optional<SubscriptionEntity> currentSubOpt = subscriptionRepository.findCurrentForUpdate(
                memberId,
                terms.gymId(),
                List.of(MembershipStatus.ACTIVE, MembershipStatus.PAUSED)
        );

        SubscriptionEntity sub;
        boolean isRenewal = false;

        if (currentSubOpt.isPresent()) {
            sub = currentSubOpt.get();
            if (!sub.getPlanId().equals(terms.planId())) {
                throw new PlanSwitchNotAllowedException(
                        "Cannot switch plan on an active or paused subscription; plan changes are permitted only after expiration.");
            }

            isRenewal = true;
            if (terms.planType() == PlanType.LIFETIME) {
                sub.setEndDate(null);
            } else {
                int addDays = terms.durationDays() != null
                        ? terms.durationDays()
                        : memberProperties.subscription().defaultDurationDays();
                LocalDate baseDate = (sub.getEndDate() != null && sub.getEndDate().isAfter(today))
                        ? sub.getEndDate()
                        : today;
                sub.setEndDate(baseDate.plusDays(addDays));
            }
            sub.setPausedAt(null);
            sub.setRemainingDays(null);
        } else {
            int defaultDays = memberProperties.subscription().defaultDurationDays();
            sub = new SubscriptionEntity();
            sub.setMemberId(memberId);
            sub.setGymId(terms.gymId());
            sub.setPlanId(terms.planId());
            sub.setStartDate(today);
            sub.setEndDate(terms.planType() == PlanType.LIFETIME
                    ? null
                    : today.plusDays(terms.durationDays() != null ? terms.durationDays() : defaultDays));
        }

        sub.setPlanTypeSnapshot(terms.planType());
        sub.setDurationDaysSnapshot(terms.durationDays());
        sub.setPriceVndSnapshot(terms.priceVnd());
        sub.setStatus(MembershipStatus.ACTIVE);
        SubscriptionEntity savedSub = subscriptionRepository.save(sub);

        memberStatusService.refresh(member);

        MembershipActivatedEvent event = eventFactory.createActivatedEvent(member, savedSub, isRenewal, clock);
        outboxEventWriter.write(
                MemberEventTopics.AGGREGATE_TYPE_MEMBER,
                member.getId(),
                MemberEventTopics.MEMBERSHIP_ACTIVATED,
                event);

        return subscriptionMapper.toDto(savedSub);
    }
}
