package com.gym.member.unit.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.config.MemberProperties;
import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.member.adapter.out.persistence.mapper.SubscriptionMapper;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.gym.member.member.application.service.ExpiryWarningPublisher;
import com.gym.member.member.application.service.MemberStatusService;
import com.gym.member.member.application.service.MembershipEventFactory;
import com.gym.member.member.application.service.SubscriptionActivationService;
import com.gym.member.member.application.service.SubscriptionExpiryService;
import com.gym.member.member.application.service.SubscriptionLifecycleService;
import com.gym.member.member.domain.dto.PurchasedPlanTerms;
import com.gym.member.member.domain.dto.SubscriptionDto;
import com.gym.member.member.domain.exception.CannotPauseLifetimeException;
import com.gym.member.member.domain.exception.MaxPausesExceededException;
import com.gym.member.member.domain.exception.PlanSwitchNotAllowedException;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.member.member.domain.model.PlanType;
import com.gym.member.shared.outbox.service.OutboxEventWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceUnitTest {

    @Mock
    private SubscriptionJpaRepository subscriptionRepository;

    @Mock
    private MemberJpaRepository memberRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Mock
    private MemberStatusService memberStatusService;

    @Mock
    private ExpiryWarningPublisher expiryWarningPublisher;

    @Spy
    private MembershipEventFactory eventFactory = new MembershipEventFactory();

    @Mock
    private MemberProperties memberProperties;

    @Spy
    private SubscriptionMapper subscriptionMapper = Mappers.getMapper(SubscriptionMapper.class);

    @Spy
    private Clock clock = Clock.systemUTC();

    private SubscriptionActivationService activationService;
    private SubscriptionLifecycleService lifecycleService;
    private SubscriptionExpiryService expiryService;

    private String memberId;
    private String userId;
    private String planId;
    private String gymId;
    private MemberEntity member;
    private SubscriptionEntity activeSub;
    private PurchasedPlanTerms monthlyTerms;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        planId = UUID.randomUUID().toString();
        gymId = UUID.randomUUID().toString();

        member = new MemberEntity();
        member.setId(memberId);
        member.setUserId(userId);
        member.setStatus(MembershipStatus.NONE);

        activeSub = new SubscriptionEntity();
        activeSub.setId(UUID.randomUUID().toString());
        activeSub.setMemberId(memberId);
        activeSub.setGymId(gymId);
        activeSub.setPlanId(planId);
        activeSub.setPlanTypeSnapshot(PlanType.MONTHLY);
        activeSub.setDurationDaysSnapshot(30);
        activeSub.setPriceVndSnapshot(500_000L);
        activeSub.setStatus(MembershipStatus.ACTIVE);
        activeSub.setStartDate(LocalDate.now(clock));
        activeSub.setEndDate(LocalDate.now(clock).plusDays(30));
        activeSub.setPauseCount(0);

        monthlyTerms = new PurchasedPlanTerms(planId, gymId, PlanType.MONTHLY, 30, 500_000L);

        activationService = new SubscriptionActivationService(
                subscriptionRepository,
                memberRepository,
                memberStatusService,
                outboxEventWriter,
                eventFactory,
                memberProperties,
                subscriptionMapper,
                clock);
        lifecycleService = new SubscriptionLifecycleService(
                subscriptionRepository,
                memberRepository,
                memberStatusService,
                outboxEventWriter,
                eventFactory,
                memberProperties,
                subscriptionMapper,
                clock);
        expiryService = new SubscriptionExpiryService(
                subscriptionRepository,
                memberRepository,
                memberStatusService,
                expiryWarningPublisher,
                outboxEventWriter,
                eventFactory,
                memberProperties,
                clock);
    }

    @Test
    void givenNoActiveSubscription_whenActivateOrRenewSubscription_thenCreatesNewActiveSubscription() {
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findCurrentForUpdate(eq(memberId), eq(gymId), any())).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any())).thenAnswer(inv -> {
            SubscriptionEntity entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID().toString());
            return entity;
        });

        SubscriptionDto result = activationService.activateOrRenewSubscription(memberId, monthlyTerms);

        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
        verify(outboxEventWriter, times(1)).write(eq("member"), eq(memberId), eq("membership.activated.v1"), any());
    }

    @Test
    void givenActiveSubscription_whenActivateOrRenewSubscription_thenExtendsEndDateAndPublishesEvent() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findCurrentForUpdate(eq(memberId), eq(gymId), any()))
                .thenReturn(Optional.of(activeSub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionDto result = activationService.activateOrRenewSubscription(memberId, monthlyTerms);

        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
        verify(outboxEventWriter, times(1)).write(eq("member"), eq(memberId), eq("membership.activated.v1"), any());
    }

    @Test
    void givenLifetimePlan_whenActivateOrRenewSubscription_thenCreatesSubscriptionWithNullEndDate() {
        PurchasedPlanTerms lifetime = new PurchasedPlanTerms(planId, gymId, PlanType.LIFETIME, null, 5_000_000L);
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findCurrentForUpdate(eq(memberId), eq(gymId), any())).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionDto result = activationService.activateOrRenewSubscription(memberId, lifetime);

        assertNotNull(result);
        assertNull(result.endDate());
    }

    @Test
    void givenMissingMember_whenActivateOrRenewSubscription_thenThrowsNotFoundException() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> activationService.activateOrRenewSubscription(memberId, monthlyTerms));
    }

    @Test
    void givenActiveSubscriptionDifferentPlan_whenActivateOrRenewSubscription_thenThrowsPlanSwitchNotAllowed() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findCurrentForUpdate(eq(memberId), eq(gymId), any()))
                .thenReturn(Optional.of(activeSub));
        PurchasedPlanTerms otherPlan =
                new PurchasedPlanTerms(UUID.randomUUID().toString(), gymId, PlanType.MONTHLY, 30, 500_000L);

        assertThrows(PlanSwitchNotAllowedException.class,
                () -> activationService.activateOrRenewSubscription(memberId, otherPlan));
    }

    @Test
    void givenActiveSubscription_whenRenewLifetime_thenClearsEndDate() {
        PurchasedPlanTerms lifetime = new PurchasedPlanTerms(planId, gymId, PlanType.LIFETIME, null, 5_000_000L);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findCurrentForUpdate(eq(memberId), eq(gymId), any()))
                .thenReturn(Optional.of(activeSub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionDto result = activationService.activateOrRenewSubscription(memberId, lifetime);

        assertNull(result.endDate());
    }

    @Test
    void givenActiveSubscriptionWithPastEndDate_whenRenewWithNullDuration_thenUsesDefaultDaysFromToday() {
        activeSub.setEndDate(LocalDate.now(clock).minusDays(1));
        PurchasedPlanTerms terms = new PurchasedPlanTerms(planId, gymId, PlanType.MONTHLY, null, 500_000L);
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 45);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findCurrentForUpdate(eq(memberId), eq(gymId), any()))
                .thenReturn(Optional.of(activeSub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionDto result = activationService.activateOrRenewSubscription(memberId, terms);

        assertEquals(LocalDate.now(clock).plusDays(45), result.endDate());
    }

    @Test
    void given_no_expired_subscriptions_when_process_expired_subscriptions_then_does_nothing() {
        // given
        when(subscriptionRepository.findExpiredForUpdate(eq(MembershipStatus.ACTIVE), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(List.of());

        // when
        expiryService.processExpiredSubscriptions();

        // then
        verify(subscriptionRepository, never()).save(any());
        verify(outboxEventWriter, never()).write(any(), any(), any(), any());
        verify(memberStatusService, never()).refresh(any());
    }

    @Test
    void given_no_expiring_subscriptions_when_process_expiring_soon_warnings_then_does_nothing() {
        // given
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(subscriptionRepository.findAll(any(Specification.class))).thenReturn(List.of());

        // when
        expiryService.processExpiringSoonWarnings();

        // then
        verify(expiryWarningPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void given_missing_member_for_warning_when_process_expiring_soon_warnings_then_skips_event() {
        // given
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(subscriptionRepository.findAll(any(Specification.class))).thenReturn(List.of(activeSub));
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        // when
        expiryService.processExpiringSoonWarnings();

        // then
        verify(expiryWarningPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void givenMissingMember_whenSuspendMemberAndSubscription_thenNoOps() {
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.empty());

        expiryService.suspendMemberAndSubscription(userId);

        verify(memberRepository, never()).save(any());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void givenActiveMonthlySubscription_whenPauseSubscription_thenPausesSubscriptionAndIncrementsPauseCount() {
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(activeSub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionDto result = lifecycleService.pauseSubscription(memberId, gymId);

        assertNotNull(result);
        assertEquals(MembershipStatus.PAUSED, result.status());
        assertEquals(1, result.pauseCount());
        verify(outboxEventWriter, times(1)).write(eq("member"), eq(memberId), eq("membership.paused.v1"), any());
    }

    @Test
    void givenMissingMember_whenPauseSubscription_thenThrowsNotFoundException() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> lifecycleService.pauseSubscription(memberId, gymId));
    }

    @Test
    void givenNoActiveSubscription_whenPauseSubscription_thenThrowsNotFoundException() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> lifecycleService.pauseSubscription(memberId, gymId));
    }

    @Test
    void givenLifetimeSubscription_whenPauseSubscription_thenThrowsCannotPauseLifetimeException() {
        activeSub.setPlanTypeSnapshot(PlanType.LIFETIME);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(activeSub));

        assertThrows(CannotPauseLifetimeException.class, () -> lifecycleService.pauseSubscription(memberId, gymId));
    }

    @Test
    void givenMaxPausesReached_whenPauseSubscription_thenThrowsMaxPausesExceededException() {
        activeSub.setPauseCount(3);
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(activeSub));

        assertThrows(MaxPausesExceededException.class, () -> lifecycleService.pauseSubscription(memberId, gymId));
    }

    @Test
    void givenPausedSubscription_whenResumeSubscription_thenResumesSubscriptionToActive() {
        SubscriptionEntity pausedSub = new SubscriptionEntity();
        pausedSub.setId(UUID.randomUUID().toString());
        pausedSub.setMemberId(memberId);
        pausedSub.setGymId(gymId);
        pausedSub.setPlanId(planId);
        pausedSub.setPlanTypeSnapshot(PlanType.MONTHLY);
        pausedSub.setDurationDaysSnapshot(30);
        pausedSub.setPriceVndSnapshot(500_000L);
        pausedSub.setStatus(MembershipStatus.PAUSED);
        pausedSub.setRemainingDays(15);

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.PAUSED))
                .thenReturn(Optional.of(pausedSub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionDto result = lifecycleService.resumeSubscription(memberId, gymId);

        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
        verify(outboxEventWriter, times(1)).write(eq("member"), eq(memberId), eq("membership.resumed.v1"), any());
    }

    @Test
    void givenMissingMember_whenResumeSubscription_thenThrowsNotFoundException() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> lifecycleService.resumeSubscription(memberId, gymId));
    }

    @Test
    void givenNoPausedSubscription_whenResumeSubscription_thenThrowsNotFoundException() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.PAUSED))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> lifecycleService.resumeSubscription(memberId, gymId));
    }

    @Test
    void givenActiveSubscription_whenGetActiveSubscription_thenReturnsSubscriptionDto() {
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(activeSub));

        SubscriptionDto result = lifecycleService.getActiveSubscription(memberId, gymId);

        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
    }

    @Test
    void givenNoActiveOrPausedSubscription_whenGetActiveSubscription_thenThrowsNotFoundException() {
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.PAUSED))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> lifecycleService.getActiveSubscription(memberId, gymId));
    }

    @Test
    void given_expired_active_subscriptions_when_process_expired_subscriptions_then_updates_status_and_publishes_event() {
        // given
        when(subscriptionRepository.findExpiredForUpdate(eq(MembershipStatus.ACTIVE), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(List.of(activeSub));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        // when
        expiryService.processExpiredSubscriptions();

        // then
        assertEquals(MembershipStatus.EXPIRED, activeSub.getStatus());
        verify(subscriptionRepository, times(1)).save(activeSub);
        verify(memberStatusService).refresh(member);
        verify(outboxEventWriter, times(1)).write(eq("member"), eq(memberId), eq("membership.expired.v1"), any());
    }

    @Test
    void given_expiring_soon_subscriptions_when_process_expiring_soon_warnings_then_publishes_deduped_warning() {
        // given
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(subscriptionRepository.findAll(any(Specification.class))).thenReturn(List.of(activeSub));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        // when
        expiryService.processExpiringSoonWarnings();

        // then
        verify(expiryWarningPublisher, times(1)).publish(eq(memberId), any(), any());
    }

    @Test
    void given_active_and_paused_subscriptions_when_suspend_member_and_subscription_then_expires_every_current_subscription() {
        // given
        SubscriptionEntity pausedSub = new SubscriptionEntity();
        pausedSub.setId(UUID.randomUUID().toString());
        pausedSub.setMemberId(memberId);
        pausedSub.setGymId(UUID.randomUUID().toString());
        pausedSub.setPlanId(planId);
        pausedSub.setPlanTypeSnapshot(PlanType.MONTHLY);
        pausedSub.setDurationDaysSnapshot(30);
        pausedSub.setPriceVndSnapshot(500_000L);
        pausedSub.setStatus(MembershipStatus.PAUSED);
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findAll(any(Specification.class))).thenReturn(List.of(activeSub, pausedSub));

        // when
        expiryService.suspendMemberAndSubscription(userId);

        // then
        assertEquals(MembershipStatus.EXPIRED, activeSub.getStatus());
        assertEquals(MembershipStatus.EXPIRED, pausedSub.getStatus());
        verify(subscriptionRepository).save(activeSub);
        verify(subscriptionRepository).save(pausedSub);
        verify(memberStatusService).refresh(member);
        verify(outboxEventWriter, times(2)).write(eq("member"), eq(memberId), eq("membership.expired.v1"), any());
    }

}
