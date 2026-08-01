package com.gym.member.application.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gym.member.config.MemberProperties;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

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

    @InjectMocks
    private SubscriptionService subscriptionService;

    private String memberId;
    private String planId;
    private MemberEntity member;
    private SubscriptionEntity activeSub;
    private MembershipPlanEntity monthlyPlan;
    private MembershipPlanEntity lifetimePlan;

    @BeforeEach
    void setUp() {
        MemberProperties.SubscriptionProperties subProps = new MemberProperties.SubscriptionProperties(2, 7, 30);
        lenient().when(memberProperties.subscription()).thenReturn(subProps);

        memberId = UUID.randomUUID().toString();
        planId = UUID.randomUUID().toString();

        member = new MemberEntity();
        member.setId(memberId);
        member.setUserId(UUID.randomUUID().toString());
        member.setGymId(UUID.randomUUID().toString());
        member.setStatus(MembershipStatus.ACTIVE);

        activeSub = new SubscriptionEntity();
        activeSub.setId(UUID.randomUUID().toString());
        activeSub.setMemberId(memberId);
        activeSub.setPlanId(planId);
        activeSub.setStatus(MembershipStatus.ACTIVE);
        activeSub.setStartDate(LocalDate.now().minusDays(10));
        activeSub.setEndDate(LocalDate.now().plusDays(20));
        activeSub.setPauseCount(0);

        monthlyPlan = new MembershipPlanEntity();
        monthlyPlan.setId(planId);
        monthlyPlan.setPlanType(PlanType.MONTHLY);
        monthlyPlan.setDurationDays(30);

        lifetimePlan = new MembershipPlanEntity();
        lifetimePlan.setId(planId);
        lifetimePlan.setPlanType(PlanType.LIFETIME);
    }

    @Test
    void pauseSubscription_success() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(monthlyPlan));
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = subscriptionService.pauseSubscription(memberId);

        assertNotNull(result);
        assertEquals(MembershipStatus.PAUSED, result.status());
        assertEquals(1, result.pauseCount());
        verify(eventPublisher, times(1)).publish(eq("membership.paused"), anyString(), any());
    }

    @Test
    void pauseSubscription_throwsCannotPauseLifetimeException() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(lifetimePlan));

        assertThrows(CannotPauseLifetimeException.class, () -> subscriptionService.pauseSubscription(memberId));
    }

    @Test
    void pauseSubscription_throwsMaxPausesExceededException() {
        activeSub.setPauseCount(2);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)).thenReturn(Optional.of(activeSub));
        when(planRepository.findById(planId)).thenReturn(Optional.of(monthlyPlan));

        assertThrows(MaxPausesExceededException.class, () -> subscriptionService.pauseSubscription(memberId));
    }

    @Test
    void resumeSubscription_success() {
        activeSub.setStatus(MembershipStatus.PAUSED);
        activeSub.setRemainingDays(15);
        activeSub.setPausedAt(LocalDate.now().minusDays(5));

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.findByMemberIdAndStatus(memberId, MembershipStatus.PAUSED)).thenReturn(Optional.of(activeSub));
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto result = subscriptionService.resumeSubscription(memberId);

        assertNotNull(result);
        assertEquals(MembershipStatus.ACTIVE, result.status());
        assertEquals(LocalDate.now().plusDays(15), result.endDate());
        verify(eventPublisher, times(1)).publish(eq("membership.resumed"), anyString(), any());
    }
}
