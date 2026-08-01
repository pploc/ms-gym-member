package com.gym.member.adapter.in.kafka;

import com.gym.common.kafka.message.EventEnvelope;
import com.gym.member.application.service.IdempotencyService;
import com.gym.member.application.service.MemberService;
import com.gym.member.application.service.SubscriptionService;
import com.gym.member.domain.dto.MemberDto;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.events.v1.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventConsumerAdapterTest {

    @Mock
    private MemberService memberService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private EventConsumerAdapter eventConsumerAdapter;

    private String userId;
    private String gymId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().toString();
        gymId = UUID.randomUUID().toString();
    }

    @Test
    void handleUserRegistered_duplicateEvent_skips() {
        EventEnvelope<UserRegisteredEvent> envelope = new EventEnvelope<>("identity.user.registered", userId, null, System.currentTimeMillis(), "trace-1", "test");
        when(idempotencyService.isEventProcessed("trace-1")).thenReturn(true);

        eventConsumerAdapter.handleUserRegistered(envelope, ack);

        verify(ack, times(1)).acknowledge();
        verify(memberService, never()).createMemberShell(any(), any(), any());
    }

    @Test
    void handleUserRegistered_success() {
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder()
                .setUserId(userId)
                .setFullName("Jane Doe")
                .setGymId(gymId)
                .build();
        EventEnvelope<UserRegisteredEvent> envelope = new EventEnvelope<>("identity.user.registered", userId, payload, System.currentTimeMillis(), "trace-1", "test");

        when(idempotencyService.isEventProcessed("trace-1")).thenReturn(false);

        eventConsumerAdapter.handleUserRegistered(envelope, ack);

        verify(memberService, times(1)).createMemberShell(userId, "Jane Doe", gymId);
        verify(idempotencyService, times(1)).markEventProcessed("trace-1", "identity.user.registered");
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void handleUserRegistered_invalidPayload_throwsException() {
        EventEnvelope<UserRegisteredEvent> envelope = new EventEnvelope<>("identity.user.registered", userId, null, System.currentTimeMillis(), null, "test");
        when(idempotencyService.isEventProcessed("identity.user.registered:" + userId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> eventConsumerAdapter.handleUserRegistered(envelope, ack));
    }

    @Test
    void handlePaymentCompleted_duplicateEvent_skips() {
        EventEnvelope<PaymentCompletedEvent> envelope = new EventEnvelope<>("payment.completed", userId, null, System.currentTimeMillis(), "trace-2", "test");
        when(idempotencyService.isEventProcessed("trace-2")).thenReturn(true);

        eventConsumerAdapter.handlePaymentCompleted(envelope, ack);

        verify(ack, times(1)).acknowledge();
        verify(subscriptionService, never()).activateOrRenewSubscription(any(), any());
    }

    @Test
    void handlePaymentCompleted_nullPayload_throwsException() {
        EventEnvelope<PaymentCompletedEvent> envelope = new EventEnvelope<>("payment.completed", userId, null, System.currentTimeMillis(), "trace-2", "test");
        when(idempotencyService.isEventProcessed("trace-2")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> eventConsumerAdapter.handlePaymentCompleted(envelope, ack));
    }

    @Test
    void handlePaymentCompleted_success_membership() {
        String planId = UUID.randomUUID().toString();
        UUID memberId = UUID.randomUUID();
        PaymentCompletedEvent payload = PaymentCompletedEvent.newBuilder()
                .setType("MEMBERSHIP")
                .setUserId(userId)
                .setReferenceId(planId)
                .build();
        EventEnvelope<PaymentCompletedEvent> envelope = new EventEnvelope<>("payment.completed", userId, payload, System.currentTimeMillis(), "trace-2", "test");

        MemberDto memberDto = new MemberDto(memberId, UUID.fromString(userId), UUID.fromString(gymId), "John", null, null, null, null, null, null);

        when(idempotencyService.isEventProcessed("trace-2")).thenReturn(false);
        when(memberService.getMemberByUserId(userId)).thenReturn(memberDto);

        eventConsumerAdapter.handlePaymentCompleted(envelope, ack);

        verify(subscriptionService, times(1)).activateOrRenewSubscription(memberId.toString(), planId);
        verify(idempotencyService, times(1)).markEventProcessed("trace-2", "payment.completed");
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void handlePaymentCompleted_missingUserIdOrPlanId_skipsSubscriptionCall() {
        PaymentCompletedEvent payload = PaymentCompletedEvent.newBuilder()
                .setType("MEMBERSHIP")
                .setUserId("")
                .setReferenceId("")
                .build();
        EventEnvelope<PaymentCompletedEvent> envelope = new EventEnvelope<>("payment.completed", "", payload, System.currentTimeMillis(), "trace-2", "test");

        when(idempotencyService.isEventProcessed("trace-2")).thenReturn(false);

        eventConsumerAdapter.handlePaymentCompleted(envelope, ack);

        verify(subscriptionService, never()).activateOrRenewSubscription(any(), any());
        verify(idempotencyService, times(1)).markEventProcessed("trace-2", "payment.completed");
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void handlePaymentCompleted_nonMembershipType_skipsSubscriptionCall() {
        PaymentCompletedEvent payload = PaymentCompletedEvent.newBuilder()
                .setType("OTHER")
                .build();
        EventEnvelope<PaymentCompletedEvent> envelope = new EventEnvelope<>("payment.completed", userId, payload, System.currentTimeMillis(), "trace-2", "test");

        when(idempotencyService.isEventProcessed("trace-2")).thenReturn(false);

        eventConsumerAdapter.handlePaymentCompleted(envelope, ack);

        verify(subscriptionService, never()).activateOrRenewSubscription(any(), any());
        verify(idempotencyService, times(1)).markEventProcessed("trace-2", "payment.completed");
        verify(ack, times(1)).acknowledge();
    }
}
