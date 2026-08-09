package com.gym.member.unit.service;

import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.gym.member.member.application.service.MemberStatusService;
import com.gym.member.member.domain.model.MembershipStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberStatusServiceUnitTest {

    @Mock
    private MemberJpaRepository memberRepository;

    @Mock
    private SubscriptionJpaRepository subscriptionRepository;

    @InjectMocks
    private MemberStatusService memberStatusService;

    private MemberEntity member;

    @BeforeEach
    void setUp() {
        member = new MemberEntity();
        member.setId(UUID.randomUUID().toString());
        member.setStatus(MembershipStatus.NONE);
    }

    @Test
    void given_active_and_expired_subscriptions_when_refresh_then_aggregate_is_active() {
        // given
        when(subscriptionRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(sub(MembershipStatus.EXPIRED), sub(MembershipStatus.ACTIVE)));

        // when
        memberStatusService.refresh(member);

        // then
        assertEquals(MembershipStatus.ACTIVE, member.getStatus());
        verify(memberRepository).save(member);
    }

    @Test
    void given_paused_and_expired_subscriptions_when_refresh_then_aggregate_is_paused() {
        // given
        when(subscriptionRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(sub(MembershipStatus.EXPIRED), sub(MembershipStatus.PAUSED)));

        // when
        memberStatusService.refresh(member);

        // then
        assertEquals(MembershipStatus.PAUSED, member.getStatus());
    }

    @Test
    void given_only_expired_subscriptions_when_refresh_then_aggregate_is_expired() {
        // given
        when(subscriptionRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(sub(MembershipStatus.EXPIRED)));

        // when
        memberStatusService.refresh(member);

        // then
        assertEquals(MembershipStatus.EXPIRED, member.getStatus());
    }

    @Test
    void given_no_subscriptions_when_refresh_then_aggregate_is_none() {
        // given
        when(subscriptionRepository.findAll(any(Specification.class))).thenReturn(List.of());

        // when
        memberStatusService.refresh(member);

        // then
        assertEquals(MembershipStatus.NONE, member.getStatus());
    }

    private static SubscriptionEntity sub(MembershipStatus status) {
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setStatus(status);
        return entity;
    }
}
