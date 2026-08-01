package com.gym.member.unit.kafka;

import com.gym.common.kafka.message.EventEnvelope;
import com.gym.member.adapter.in.kafka.EventConsumerAdapter;
import com.gym.member.application.service.IdempotencyService;
import com.gym.member.application.service.MemberService;
import com.gym.member.application.service.SubscriptionService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventConsumerAdapterUnitTest {

    @Mock
    private MemberService memberService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private EventConsumerAdapter adapter;

    private String eventId;
    private String userId;
    private String gymId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        gymId = UUID.randomUUID().toString();
    }

    @Test
    void handleUserRegistered_newMember_success() {
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder()
                .setUserId(userId)
                .setFullName("John Doe")
                .setGymId(gymId)
                .build();

        EventEnvelope<UserRegisteredEvent> envelope = new EventEnvelope<>(
                "identity.user.registered", userId, payload, System.currentTimeMillis(), eventId, "user-service"
        );

        when(idempotencyService.isEventProcessed(eventId)).thenReturn(false);

        adapter.handleUserRegistered(envelope, ack);

        verify(memberService, times(1)).createMemberShell(userId, "John Doe", gymId);
        verify(idempotencyService, times(1)).markEventProcessed(eventId, "identity.user.registered");
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void handleUserRegistered_duplicateEvent_skipped() {
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder().setUserId(userId).setGymId(gymId).build();
        EventEnvelope<UserRegisteredEvent> envelope = new EventEnvelope<>(
                "identity.user.registered", userId, payload, System.currentTimeMillis(), eventId, "user-service"
        );

        when(idempotencyService.isEventProcessed(eventId)).thenReturn(true);

        adapter.handleUserRegistered(envelope, ack);

        verify(memberService, never()).createMemberShell(any(), any(), any());
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void handleUserRegistered_nullPayload_throwsException() {
        EventEnvelope<UserRegisteredEvent> envelope = new EventEnvelope<>(
                "identity.user.registered", userId, null, System.currentTimeMillis(), eventId, "user-service"
        );

        when(idempotencyService.isEventProcessed(eventId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> adapter.handleUserRegistered(envelope, ack));
    }

    @Test
    void handlePaymentCompleted_newPayment_success() {
        com.gym.member.domain.dto.MemberDto memberDto = new com.gym.member.domain.dto.MemberDto(
                UUID.fromString(userId), UUID.fromString(userId), UUID.fromString(gymId), "John", "123", "", "", com.gym.member.domain.model.MembershipStatus.ACTIVE, null, null
        );
        PaymentCompletedEvent payload = PaymentCompletedEvent.newBuilder()
                .setUserId(userId)
                .setType("MEMBERSHIP")
                .setReferenceId(UUID.randomUUID().toString())
                .build();

        EventEnvelope<PaymentCompletedEvent> envelope = new EventEnvelope<>(
                "payment.completed", userId, payload, System.currentTimeMillis(), eventId, "payment-service"
        );

        when(idempotencyService.isEventProcessed(eventId)).thenReturn(false);
        when(memberService.getMemberByUserId(userId)).thenReturn(memberDto);

        adapter.handlePaymentCompleted(envelope, ack);

        verify(subscriptionService, times(1)).activateOrRenewSubscription(eq(userId), any());
        verify(idempotencyService, times(1)).markEventProcessed(eventId, "payment.completed");
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void handlePaymentCompleted_nullPayload_throwsException() {
        EventEnvelope<PaymentCompletedEvent> envelope = new EventEnvelope<>(
                "payment.completed", userId, null, System.currentTimeMillis(), eventId, "payment-service"
        );

        when(idempotencyService.isEventProcessed(eventId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> adapter.handlePaymentCompleted(envelope, ack));
    }
}
