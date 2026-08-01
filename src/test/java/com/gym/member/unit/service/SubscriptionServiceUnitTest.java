package com.gym.member.unit.service;

import com.gym.common.error.NotFoundException;
import com.gym.common.kafka.producer.EventPublisher;
import com.gym.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.adapter.out.persistence.entity.MembershipPlanEntity;
import com.gym.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.adapter.out.persistence.repository.MembershipPlanJpaRepository;
import com.gym.member.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.gym.member.application.service.SubscriptionService;
import com.gym.member.config.MemberProperties;
import com.gym.member.domain.dto.SubscriptionDto;
import com.gym.member.domain.exception.CannotPauseLifetimeException;
import com.gym.member.domain.exception.MaxPausesExceededException;
import com.gym.member.domain.model.MembershipStatus;
import com.gym.member.domain.model.PlanType;
import com.gym.member.mapper.SubscriptionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
    private EventPublisher eventPublisher;

    @Mock
    private MemberProperties memberProperties;

    @Spy
    private SubscriptionMapper subscriptionMapper = Mappers.getMapper(SubscriptionMapper.class);

    @InjectMocks
    private SubscriptionService subscriptionService;

    private String memberId;
    private String userId;
    private String planId;
    private MemberEntity member;
    private MembershipPlanEntity plan;
    private SubscriptionEntity activeSub;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        planId = UUID.randomUUID().toString();

        member = new MemberEntity();
        member.setId(memberId);
        member.setUserId(userId);
        member.setStatus(MembershipStatus.NONE);
        member.setGymId(UUID.randomUUID().toString());

        plan = new MembershipPlanEntity();
        plan.setId(planId);
        plan.setPlanType(PlanType.MONTHLY);
        plan.setDurationDays(30);

        activeSub = new SubscriptionEntity();
        activeSub.setId(UUID.randomUUID().toString());
        activeSub.setMemberId(memberId);
        activeSub.setPlanId(planId);
        activeSub.setStatus(MembershipStatus.ACTIVE);
        activeSub.setStartDate(LocalDate.now());
        activeSub.setEndDate(LocalDate.now().plusDays(30));
        activeSub.setPauseCount(0);
    }

    @Test
    void givenNoActiveSubscription_whenActivateOrRenewSubscription_thenCreatesNewActiveSubscription() {
        // Given
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any())).thenAnswer(inv -> {
            SubscriptionEntity entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID().toString());
            return entity;
        });

        // When
        SubscriptionDto result = subscriptionService.activateOrRenewSubscription(memberId, planId);

        // Then
        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
        verify(eventPublisher, times(1)).publish(eq("membership.activated"), eq(memberId), any());
    }

    @Test
    void givenActiveSubscription_whenActivateOrRenewSubscription_thenExtendsEndDateAndPublishesEvent() {
        // Given
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        SubscriptionDto result = subscriptionService.activateOrRenewSubscription(memberId, planId);

        // Then
        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
        verify(eventPublisher, times(1)).publish(eq("membership.activated"), eq(memberId), any());
    }

    @Test
    void givenLifetimePlan_whenActivateOrRenewSubscription_thenCreatesSubscriptionWithNullEndDate() {
        // Given
        plan.setPlanType(PlanType.LIFETIME);
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        SubscriptionDto result = subscriptionService.activateOrRenewSubscription(memberId, planId);

        // Then
        assertNotNull(result);
        assertNull(result.endDate());
    }

    @Test
    void givenMissingMember_whenActivateOrRenewSubscription_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> subscriptionService.activateOrRenewSubscription(memberId, planId));
    }

    @Test
    void givenMissingPlan_whenActivateOrRenewSubscription_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> subscriptionService.activateOrRenewSubscription(memberId, planId));
    }

    @Test
    void givenActiveMonthlySubscription_whenPauseSubscription_thenPausesSubscriptionAndIncrementsPauseCount() {
        // Given
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        SubscriptionDto result = subscriptionService.pauseSubscription(memberId);

        // Then
        assertNotNull(result);
        assertEquals(MembershipStatus.PAUSED, result.status());
        assertEquals(1, result.pauseCount());
        verify(eventPublisher, times(1)).publish(eq("membership.paused"), eq(memberId), any());
    }

    @Test
    void givenMissingMember_whenPauseSubscription_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> subscriptionService.pauseSubscription(memberId));
    }

    @Test
    void givenNoActiveSubscription_whenPauseSubscription_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> subscriptionService.pauseSubscription(memberId));
    }

    @Test
    void givenMissingPlan_whenPauseSubscription_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> subscriptionService.pauseSubscription(memberId));
    }

    @Test
    void givenLifetimeSubscription_whenPauseSubscription_thenThrowsCannotPauseLifetimeException() {
        // Given
        plan.setPlanType(PlanType.LIFETIME);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        // When & Then
        assertThrows(CannotPauseLifetimeException.class, () -> subscriptionService.pauseSubscription(memberId));
    }

    @Test
    void givenMaxPausesReached_whenPauseSubscription_thenThrowsMaxPausesExceededException() {
        // Given
        activeSub.setPauseCount(3);
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        // When & Then
        assertThrows(MaxPausesExceededException.class, () -> subscriptionService.pauseSubscription(memberId));
    }

    @Test
    void givenPausedSubscription_whenResumeSubscription_thenResumesSubscriptionToActive() {
        // Given
        SubscriptionEntity pausedSub = new SubscriptionEntity();
        pausedSub.setId(UUID.randomUUID().toString());
        pausedSub.setMemberId(memberId);
        pausedSub.setStatus(MembershipStatus.PAUSED);
        pausedSub.setRemainingDays(15);

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.PAUSED)).thenReturn(Optional.of(pausedSub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        SubscriptionDto result = subscriptionService.resumeSubscription(memberId);

        // Then
        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
        verify(eventPublisher, times(1)).publish(eq("membership.resumed"), eq(memberId), any());
    }

    @Test
    void givenMissingMember_whenResumeSubscription_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> subscriptionService.resumeSubscription(memberId));
    }

    @Test
    void givenNoPausedSubscription_whenResumeSubscription_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.PAUSED)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> subscriptionService.resumeSubscription(memberId));
    }

    @Test
    void givenActiveSubscription_whenGetActiveSubscription_thenReturnsSubscriptionDto() {
        // Given
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));

        // When
        SubscriptionDto result = subscriptionService.getActiveSubscription(memberId);

        // Then
        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
    }

    @Test
    void givenNoActiveOrPausedSubscription_whenGetActiveSubscription_thenThrowsNotFoundException() {
        // Given
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.empty());
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.PAUSED)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> subscriptionService.getActiveSubscription(memberId));
    }

    @Test
    void givenExpiredActiveSubscriptions_whenProcessExpiredSubscriptions_thenUpdatesStatusToExpiredAndPublishesEvent() {
        // Given
        when(subscriptionRepository.findAll(any(Specification.class))).thenReturn(List.of(activeSub));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        // When
        subscriptionService.processExpiredSubscriptions();

        // Then
        verify(subscriptionRepository, times(1)).save(activeSub);
        verify(eventPublisher, times(1)).publish(eq("membership.expired"), eq(memberId), any());
    }

    @Test
    void givenExpiringSoonSubscriptions_whenProcessExpiringSoonWarnings_thenPublishesExpiringSoonEvent() {
        // Given
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(subscriptionRepository.findAll(any(Specification.class))).thenReturn(List.of(activeSub));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        // When
        subscriptionService.processExpiringSoonWarnings();

        // Then
        verify(eventPublisher, times(1)).publish(eq("membership.expiring-soon"), eq(memberId), any());
    }

    @Test
    void givenUserSuspended_whenSuspendMemberAndSubscription_thenExpiresMemberAndActiveSubscription() {
        // Given
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));

        // When
        subscriptionService.suspendMemberAndSubscription(userId);

        // Then
        assertEquals(MembershipStatus.EXPIRED, member.getStatus());
        assertEquals(MembershipStatus.EXPIRED, activeSub.getStatus());
        verify(memberRepository, times(1)).save(member);
        verify(subscriptionRepository, times(1)).save(activeSub);
        verify(eventPublisher, times(1)).publish(eq("membership.expired"), eq(memberId), any());
    }
}
