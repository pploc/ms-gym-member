package com.gym.member.application.service;

import com.gym.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.adapter.out.persistence.entity.MembershipPlanEntity;
import com.gym.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.adapter.out.persistence.repository.MembershipPlanJpaRepository;
import com.gym.member.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.gym.member.adapter.out.persistence.specification.SubscriptionSpecifications;
import com.gym.member.config.MemberProperties;
import com.gym.member.domain.model.MembershipStatus;
import com.gym.proto.events.v1.MembershipExpiredEvent;
import com.gym.proto.events.v1.MembershipExpiringSoonEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionExpiryService {

    private final SubscriptionJpaRepository subscriptionRepository;
    private final MemberJpaRepository memberRepository;
    private final MembershipPlanJpaRepository planRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final MembershipEventFactory eventFactory;
    private final MemberProperties memberProperties;
    private final Clock clock;

    @Transactional
    public void processExpiredSubscriptions() {
        LocalDate today = LocalDate.now(clock);
        List<SubscriptionEntity> expiredSubs = subscriptionRepository.findAll(
                SubscriptionSpecifications.isExpiredActive(today)
        );
        if (expiredSubs.isEmpty()) return;

        Set<String> memberIds = expiredSubs.stream().map(SubscriptionEntity::getMemberId).collect(Collectors.toSet());
        List<MemberEntity> members = memberRepository.findAllById(memberIds);
        java.util.Map<String, MemberEntity> memberMap = members.stream().collect(Collectors.toMap(MemberEntity::getId, m -> m));

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

        Set<String> memberIds = warningSubs.stream().map(SubscriptionEntity::getMemberId).collect(Collectors.toSet());
        Set<String> planIds = warningSubs.stream().map(SubscriptionEntity::getPlanId).collect(Collectors.toSet());

        java.util.Map<String, MemberEntity> memberMap = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(MemberEntity::getId, m -> m));
        java.util.Map<String, MembershipPlanEntity> planMap = planRepository.findAllById(planIds).stream()
                .collect(Collectors.toMap(MembershipPlanEntity::getId, p -> p));

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
