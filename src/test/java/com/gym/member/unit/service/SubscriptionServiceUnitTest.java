package com.gym.member.unit.service;

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
    void activateOrRenewSubscription_newActive() {
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

        SubscriptionDto result = subscriptionService.activateOrRenewSubscription(memberId, planId);

        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
        verify(eventPublisher, times(1)).publish(eq("membership.activated"), eq(memberId), any());
    }

    @Test
    void activateOrRenewSubscription_renewalActive() {
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionDto result = subscriptionService.activateOrRenewSubscription(memberId, planId);

        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
        verify(eventPublisher, times(1)).publish(eq("membership.activated"), eq(memberId), any());
    }

    @Test
    void activateOrRenewSubscription_lifetime() {
        plan.setPlanType(PlanType.LIFETIME);
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionDto result = subscriptionService.activateOrRenewSubscription(memberId, planId);

        assertNotNull(result);
        assertNull(result.endDate());
    }

    @Test
    void pauseSubscription_success() {
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionDto result = subscriptionService.pauseSubscription(memberId);

        assertNotNull(result);
        assertEquals(MembershipStatus.PAUSED, result.status());
        assertEquals(1, result.pauseCount());
        verify(eventPublisher, times(1)).publish(eq("membership.paused"), eq(memberId), any());
    }

    @Test
    void pauseSubscription_lifetimeThrowsCannotPause() {
        plan.setPlanType(PlanType.LIFETIME);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThrows(CannotPauseLifetimeException.class, () -> subscriptionService.pauseSubscription(memberId));
    }

    @Test
    void pauseSubscription_maxPausesExceeded() {
        activeSub.setPauseCount(3);
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThrows(MaxPausesExceededException.class, () -> subscriptionService.pauseSubscription(memberId));
    }

    @Test
    void resumeSubscription_success() {
        SubscriptionEntity pausedSub = new SubscriptionEntity();
        pausedSub.setId(UUID.randomUUID().toString());
        pausedSub.setMemberId(memberId);
        pausedSub.setStatus(MembershipStatus.PAUSED);
        pausedSub.setRemainingDays(15);

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.PAUSED)).thenReturn(Optional.of(pausedSub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionDto result = subscriptionService.resumeSubscription(memberId);

        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
        verify(eventPublisher, times(1)).publish(eq("membership.resumed"), eq(memberId), any());
    }

    @Test
    void getActiveSubscription_success() {
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));

        SubscriptionDto result = subscriptionService.getActiveSubscription(memberId);

        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
    }

    @Test
    void processExpiredSubscriptions_success() {
        when(subscriptionRepository.findExpiredActiveSubscriptions(any())).thenReturn(List.of(activeSub));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        subscriptionService.processExpiredSubscriptions();

        verify(subscriptionRepository, times(1)).save(activeSub);
        verify(eventPublisher, times(1)).publish(eq("membership.expired"), eq(memberId), any());
    }

    @Test
    void processExpiringSoonWarnings_success() {
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(3, 7, 30);
        when(memberProperties.subscription()).thenReturn(subProps);
        when(subscriptionRepository.findExpiringSoonSubscriptions(any())).thenReturn(List.of(activeSub));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        subscriptionService.processExpiringSoonWarnings();

        verify(eventPublisher, times(1)).publish(eq("membership.expiring-soon"), eq(memberId), any());
    }
}
