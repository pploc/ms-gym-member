package com.gym.member.unit.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.adapter.out.persistence.entity.MembershipPlanEntity;
import com.gym.member.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.MembershipPlanJpaRepository;
import com.gym.member.shared.outbox.repository.OutboxEventJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.gym.member.member.application.service.MembershipEventFactory;
import com.gym.member.shared.outbox.service.OutboxEventWriter;
import com.gym.member.member.application.service.SubscriptionActivationService;
import com.gym.member.member.application.service.SubscriptionExpiryService;
import com.gym.member.member.application.service.SubscriptionLifecycleService;
import com.gym.member.config.MemberProperties;
import com.gym.member.member.domain.dto.SubscriptionDto;
import com.gym.member.member.domain.exception.CannotPauseLifetimeException;
import com.gym.member.member.domain.exception.MaxPausesExceededException;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.member.member.domain.model.PlanType;
import com.gym.member.member.adapter.out.persistence.mapper.SubscriptionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceUnitTest {

    @Mock
    private SubscriptionJpaRepository subscriptionRepository;

    @Mock
    private MemberJpaRepository memberRepository;

    @Mock
    private MembershipPlanJpaRepository planRepository;

    @Mock
    private OutboxEventJpaRepository outboxEventRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

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
    private MembershipPlanEntity plan;
    private SubscriptionEntity activeSub;

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

        plan = new MembershipPlanEntity();
        plan.setId(planId);
        plan.setGymId(gymId);
        plan.setActive(true);
        plan.setPlanType(PlanType.MONTHLY);
        plan.setDurationDays(30);

        activeSub = new SubscriptionEntity();
        activeSub.setId(UUID.randomUUID().toString());
        activeSub.setMemberId(memberId);
        activeSub.setGymId(gymId);
        activeSub.setPlanId(planId);
        activeSub.setStatus(MembershipStatus.ACTIVE);
        activeSub.setStartDate(LocalDate.now(clock));
        activeSub.setEndDate(LocalDate.now(clock).plusDays(30));
        activeSub.setPauseCount(0);

        activationService = new SubscriptionActivationService(
                subscriptionRepository, memberRepository, planRepository, outboxEventWriter, eventFactory, memberProperties, subscriptionMapper, clock
        );
        lifecycleService = new SubscriptionLifecycleService(
                subscriptionRepository, memberRepository, planRepository, outboxEventWriter, eventFactory, memberProperties, subscriptionMapper, clock
        );
        expiryService = new SubscriptionExpiryService(
                subscriptionRepository, memberRepository, planRepository, outboxEventRepository, outboxEventWriter, eventFactory, memberProperties, clock
        );
    }

    @Test
    void givenNoActiveSubscription_whenActivateOrRenewSubscription_thenCreatesNewActiveSubscription() {
        // Given
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findCurrentForUpdate(eq(memberId), eq(gymId), any())).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any())).thenAnswer(inv -> {
            SubscriptionEntity entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID().toString());
            return entity;
        });

        // When
        SubscriptionDto result = activationService.activateOrRenewSubscription(memberId, planId);

        // Then
        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
        verify(outboxEventWriter, times(1)).write(eq("member"), eq(memberId), eq("membership.activated"), any());
    }

    @Test
    void givenActiveSubscription_whenActivateOrRenewSubscription_thenExtendsEndDateAndPublishesEvent() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findCurrentForUpdate(eq(memberId), eq(gymId), any())).thenReturn(Optional.of(activeSub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        SubscriptionDto result = activationService.activateOrRenewSubscription(memberId, planId);

        // Then
        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
        verify(outboxEventWriter, times(1)).write(eq("member"), eq(memberId), eq("membership.activated"), any());
    }

    @Test
    void givenLifetimePlan_whenActivateOrRenewSubscription_thenCreatesSubscriptionWithNullEndDate() {
        // Given
        plan.setPlanType(PlanType.LIFETIME);
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findCurrentForUpdate(eq(memberId), eq(gymId), any())).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        SubscriptionDto result = activationService.activateOrRenewSubscription(memberId, planId);

        // Then
        assertNotNull(result);
        assertNull(result.endDate());
    }

    @Test
    void givenMissingMember_whenActivateOrRenewSubscription_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> activationService.activateOrRenewSubscription(memberId, planId));
    }

    @Test
    void givenMissingPlan_whenActivateOrRenewSubscription_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> activationService.activateOrRenewSubscription(memberId, planId));
    }

    @Test
    void givenActiveMonthlySubscription_whenPauseSubscription_thenPausesSubscriptionAndIncrementsPauseCount() {
        // Given
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        SubscriptionDto result = lifecycleService.pauseSubscription(memberId, gymId);

        // Then
        assertNotNull(result);
        assertEquals(MembershipStatus.PAUSED, result.status());
        assertEquals(1, result.pauseCount());
        verify(outboxEventWriter, times(1)).write(eq("member"), eq(memberId), eq("membership.paused"), any());
    }

    @Test
    void givenMissingMember_whenPauseSubscription_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> lifecycleService.pauseSubscription(memberId, gymId));
    }

    @Test
    void givenNoActiveSubscription_whenPauseSubscription_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.ACTIVE)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> lifecycleService.pauseSubscription(memberId, gymId));
    }

    @Test
    void givenMissingPlan_whenPauseSubscription_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> lifecycleService.pauseSubscription(memberId, gymId));
    }

    @Test
    void givenLifetimeSubscription_whenPauseSubscription_thenThrowsCannotPauseLifetimeException() {
        // Given
        plan.setPlanType(PlanType.LIFETIME);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        // When & Then
        assertThrows(CannotPauseLifetimeException.class, () -> lifecycleService.pauseSubscription(memberId, gymId));
    }

    @Test
    void givenMaxPausesReached_whenPauseSubscription_thenThrowsMaxPausesExceededException() {
        // Given
        activeSub.setPauseCount(3);
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        // When & Then
        assertThrows(MaxPausesExceededException.class, () -> lifecycleService.pauseSubscription(memberId, gymId));
    }

    @Test
    void givenPausedSubscription_whenResumeSubscription_thenResumesSubscriptionToActive() {
        // Given
        SubscriptionEntity pausedSub = new SubscriptionEntity();
        pausedSub.setId(UUID.randomUUID().toString());
        pausedSub.setMemberId(memberId);
        pausedSub.setGymId(gymId);
        pausedSub.setStatus(MembershipStatus.PAUSED);
        pausedSub.setRemainingDays(15);

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.PAUSED)).thenReturn(Optional.of(pausedSub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        SubscriptionDto result = lifecycleService.resumeSubscription(memberId, gymId);

        // Then
        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
        verify(outboxEventWriter, times(1)).write(eq("member"), eq(memberId), eq("membership.resumed"), any());
    }

    @Test
    void givenMissingMember_whenResumeSubscription_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> lifecycleService.resumeSubscription(memberId, gymId));
    }

    @Test
    void givenNoPausedSubscription_whenResumeSubscription_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.PAUSED)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> lifecycleService.resumeSubscription(memberId, gymId));
    }

    @Test
    void givenActiveSubscription_whenGetActiveSubscription_thenReturnsSubscriptionDto() {
        // Given
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));

        // When
        SubscriptionDto result = lifecycleService.getActiveSubscription(memberId, gymId);

        // Then
        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
    }

    @Test
    void givenNoActiveOrPausedSubscription_whenGetActiveSubscription_thenThrowsNotFoundException() {
        // Given
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.ACTIVE)).thenReturn(Optional.empty());
        when(subscriptionRepository.findByMemberIdAndGymIdAndStatus(memberId, gymId, MembershipStatus.PAUSED)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> lifecycleService.getActiveSubscription(memberId, gymId));
    }

    @Test
    void givenExpiredActiveSubscriptions_whenProcessExpiredSubscriptions_thenUpdatesStatusToExpiredAndPublishesEvent() {
        // Given
        when(subscriptionRepository.findAll(any(Specification.class))).thenReturn(List.of(activeSub));
        when(memberRepository.findAllById(Set.of(memberId))).thenReturn(List.of(member));

        // When
        expiryService.processExpiredSubscriptions();

        // Then
        verify(subscriptionRepository, times(1)).save(activeSub);
        verify(outboxEventWriter, times(1)).write(eq("member"), eq(memberId), eq("membership.expired"), any());
    }

    @Test
    void givenExpiringSoonSubscriptions_whenProcessExpiringSoonWarnings_thenPublishesExpiringSoonEvent() {
        // Given
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(subscriptionRepository.findAll(any(Specification.class))).thenReturn(List.of(activeSub));
        when(memberRepository.findAllById(Set.of(memberId))).thenReturn(List.of(member));
        when(planRepository.findAllById(Set.of(planId))).thenReturn(List.of(plan));

        // When
        expiryService.processExpiringSoonWarnings();

        // Then
        verify(outboxEventWriter, times(1)).write(eq("member"), eq(memberId), eq("membership.expiring-soon"), any());
    }

    @Test
    void givenUserSuspended_whenSuspendMemberAndSubscription_thenExpiresMemberAndActiveSubscription() {
        // Given
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));

        // When
        expiryService.suspendMemberAndSubscription(userId);

        // Then
        assertEquals(MembershipStatus.EXPIRED, member.getStatus());
        assertEquals(MembershipStatus.EXPIRED, activeSub.getStatus());
        verify(memberRepository, times(1)).save(member);
        verify(subscriptionRepository, times(1)).save(activeSub);
        verify(outboxEventWriter, times(1)).write(eq("member"), eq(memberId), eq("membership.expired"), any());
    }
}
