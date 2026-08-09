package com.gym.member.member.application.service;

import com.gym.member.config.MemberProperties;
import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.gym.member.member.adapter.out.persistence.specification.SubscriptionSpecifications;
import com.gym.member.member.application.port.in.SubscriptionExpiryUseCase;
import com.gym.member.member.domain.constant.MemberEventTopics;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.member.shared.outbox.service.OutboxEventWriter;
import com.gym.proto.events.v1.MembershipExpiredEvent;
import com.gym.proto.events.v1.MembershipExpiringSoonEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionExpiryService implements SubscriptionExpiryUseCase {

    private static final int EXPIRY_BATCH_SIZE = 100;

    private final SubscriptionJpaRepository subscriptionRepository;
    private final MemberJpaRepository memberRepository;
    private final MemberStatusService memberStatusService;
    private final ExpiryWarningPublisher expiryWarningPublisher;
    private final OutboxEventWriter outboxEventWriter;
    private final MembershipEventFactory eventFactory;
    private final MemberProperties memberProperties;
    private final Clock clock;

    @Transactional
    public void processExpiredSubscriptions() {
        LocalDate today = LocalDate.now(clock);
        List<SubscriptionEntity> expiredSubscriptions = subscriptionRepository.findExpiredForUpdate(
                MembershipStatus.ACTIVE, today, Pageable.ofSize(EXPIRY_BATCH_SIZE));
        for (SubscriptionEntity subscription : expiredSubscriptions) {
            subscription.setStatus(MembershipStatus.EXPIRED);
            subscriptionRepository.save(subscription);

            memberRepository.findById(subscription.getMemberId()).ifPresent(member -> {
                memberStatusService.refresh(member);
                MembershipExpiredEvent event = eventFactory.createExpiredEvent(member, subscription, clock);
                outboxEventWriter.write(
                        MemberEventTopics.AGGREGATE_TYPE_MEMBER,
                        member.getId(),
                        MemberEventTopics.MEMBERSHIP_EXPIRED,
                        event);
            });
        }
    }

    @Transactional
    public void processExpiringSoonWarnings() {
        LocalDate today = LocalDate.now(clock);
        LocalDate warningDate = today.plusDays(memberProperties.subscription().warningNoticeDays());
        List<SubscriptionEntity> warningSubscriptions =
                subscriptionRepository.findAll(SubscriptionSpecifications.isExpiringSoon(warningDate));

        for (SubscriptionEntity subscription : warningSubscriptions) {
            Optional<MemberEntity> member = memberRepository.findById(subscription.getMemberId());
            if (member.isEmpty() || subscription.getPlanTypeSnapshot() == null) {
                continue;
            }
            MembershipExpiringSoonEvent event = eventFactory.createExpiringSoonEvent(member.get(), subscription);
            String dedupeKey = "membership-expiring-soon:" + subscription.getId() + ":" + warningDate;
            try {
                expiryWarningPublisher.publish(member.get().getId(), event, dedupeKey);
            } catch (DataIntegrityViolationException duplicateWarning) {
                log.debug("Expiry warning already queued for subscription {} on {}", subscription.getId(), warningDate);
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
        List<SubscriptionEntity> currentSubscriptions = subscriptionRepository.findAll(
                SubscriptionSpecifications.hasMemberId(member.getId())
                        .and(SubscriptionSpecifications.hasAnyStatus(
                                List.of(MembershipStatus.ACTIVE, MembershipStatus.PAUSED))));
        for (SubscriptionEntity subscription : currentSubscriptions) {
            subscription.setStatus(MembershipStatus.EXPIRED);
            subscriptionRepository.save(subscription);
            MembershipExpiredEvent event = eventFactory.createExpiredEvent(member, subscription, clock);
            outboxEventWriter.write(
                    MemberEventTopics.AGGREGATE_TYPE_MEMBER,
                    member.getId(),
                    MemberEventTopics.MEMBERSHIP_EXPIRED,
                    event);
        }
        memberStatusService.refresh(member);
        log.info("Successfully suspended member id {} for userId: {}", member.getId(), userId);
    }
}
