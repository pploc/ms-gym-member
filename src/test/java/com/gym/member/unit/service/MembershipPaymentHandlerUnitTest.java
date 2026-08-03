package com.gym.member.unit.service;

import com.gym.member.member.application.port.in.MemberUseCase;
import com.gym.member.member.application.port.in.SubscriptionActivationUseCase;
import com.gym.member.member.application.service.strategy.MembershipPaymentHandler;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembershipPaymentHandlerUnitTest {

    @Mock
    private MemberUseCase memberUseCase;

    @Mock
    private SubscriptionActivationUseCase subscriptionActivationUseCase;

    @InjectMocks
    private MembershipPaymentHandler handler;

    private String userId;
    private String planId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().toString();
        planId = UUID.randomUUID().toString();
        memberId = UUID.randomUUID();
    }

    @Test
    void givenMembershipType_whenSupports_returnsTrue() {
        assertTrue(handler.supports("MEMBERSHIP"));
        assertTrue(handler.supports("membership"));
        assertFalse(handler.supports("OTHER"));
        assertFalse(handler.supports(null));
    }

    @Test
    void givenValidEvent_whenHandle_thenActivatesSubscription() {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setUserId(userId)
                .setReferenceId(planId)
                .setType("MEMBERSHIP")
                .build();

        MemberDto member = new MemberDto(memberId, UUID.fromString(userId), UUID.randomUUID(), "Test Member", null, null, null, MembershipStatus.ACTIVE, Instant.now(), Instant.now());
        when(memberUseCase.getMemberByUserId(userId)).thenReturn(member);

        handler.handle(event, "fallback-key");

        verify(subscriptionActivationUseCase).activateOrRenewSubscription(memberId.toString(), planId);
    }

    @Test
    void givenBlankUserIdAndValidFallbackKey_whenHandle_thenUsesFallbackKey() {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setUserId("")
                .setReferenceId(planId)
                .setType("MEMBERSHIP")
                .build();

        MemberDto member = new MemberDto(memberId, UUID.fromString(userId), UUID.randomUUID(), "Test Member", null, null, null, MembershipStatus.ACTIVE, Instant.now(), Instant.now());
        when(memberUseCase.getMemberByUserId(userId)).thenReturn(member);

        handler.handle(event, userId);

        verify(subscriptionActivationUseCase).activateOrRenewSubscription(memberId.toString(), planId);
    }

    @Test
    void givenMissingUserIdAndPlanId_whenHandle_thenThrowsIllegalArgumentException() {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setUserId("")
                .setReferenceId("")
                .build();

        assertThrows(IllegalArgumentException.class, () -> handler.handle(event, ""));
    }
}
