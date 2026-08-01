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
    void givenNewUserRegisteredEvent_whenHandleUserRegistered_thenCreatesMemberShellAndMarksProcessed() {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder()
                .setUserId(userId)
                .setFullName("John Doe")
                .setGymId(gymId)
                .build();

        EventEnvelope<UserRegisteredEvent> envelope = new EventEnvelope<>(
                "identity.user.registered", userId, payload, System.currentTimeMillis(), eventId, "user-service"
        );

        when(idempotencyService.isEventProcessed(eventId)).thenReturn(false);

        // When
        adapter.handleUserRegistered(envelope, ack);

        // Then
        verify(memberService, times(1)).createMemberShell(userId, "John Doe", gymId);
        verify(idempotencyService, times(1)).markEventProcessed(eventId, "identity.user.registered");
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void givenDuplicateUserRegisteredEvent_whenHandleUserRegistered_thenSkipsCreationAndAcknowledges() {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder().setUserId(userId).setGymId(gymId).build();
        EventEnvelope<UserRegisteredEvent> envelope = new EventEnvelope<>(
                "identity.user.registered", userId, payload, System.currentTimeMillis(), eventId, "user-service"
        );

        when(idempotencyService.isEventProcessed(eventId)).thenReturn(true);

        // When
        adapter.handleUserRegistered(envelope, ack);

        // Then
        verify(memberService, never()).createMemberShell(any(), any(), any());
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void givenNullPayloadUserRegisteredEvent_whenHandleUserRegistered_thenThrowsIllegalArgumentException() {
        // Given
        EventEnvelope<UserRegisteredEvent> envelope = new EventEnvelope<>(
                "identity.user.registered", userId, null, System.currentTimeMillis(), eventId, "user-service"
        );

        when(idempotencyService.isEventProcessed(eventId)).thenReturn(false);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> adapter.handleUserRegistered(envelope, ack));
    }

    @Test
    void givenMemberServiceError_whenHandleUserRegistered_thenRethrowsExceptionForKafkaRetry() {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder()
                .setUserId(userId)
                .setFullName("John Doe")
                .setGymId(gymId)
                .build();

        EventEnvelope<UserRegisteredEvent> envelope = new EventEnvelope<>(
                "identity.user.registered", userId, payload, System.currentTimeMillis(), eventId, "user-service"
        );

        when(idempotencyService.isEventProcessed(eventId)).thenReturn(false);
        doThrow(new RuntimeException("Database error")).when(memberService).createMemberShell(any(), any(), any());

        // When & Then
        assertThrows(RuntimeException.class, () -> adapter.handleUserRegistered(envelope, ack));
        verify(ack, never()).acknowledge();
    }

    @Test
    void givenNewPaymentCompletedEvent_whenHandlePaymentCompleted_thenActivatesSubscriptionAndMarksProcessed() {
        // Given
        com.gym.member.domain.dto.MemberDto memberDto = new com.gym.member.domain.dto.MemberDto(
                UUID.fromString(userId), UUID.fromString(userId), UUID.fromString(gymId), "John", "123", "", null, com.gym.member.domain.model.MembershipStatus.ACTIVE, null, null
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

        // When
        adapter.handlePaymentCompleted(envelope, ack);

        // Then
        verify(subscriptionService, times(1)).activateOrRenewSubscription(eq(userId), any());
        verify(idempotencyService, times(1)).markEventProcessed(eventId, "payment.completed");
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void givenNonMembershipPayment_whenHandlePaymentCompleted_thenSkipsSubscriptionAndMarksProcessed() {
        // Given
        PaymentCompletedEvent payload = PaymentCompletedEvent.newBuilder()
                .setUserId(userId)
                .setType("MERCHANDISE")
                .setReferenceId(UUID.randomUUID().toString())
                .build();

        EventEnvelope<PaymentCompletedEvent> envelope = new EventEnvelope<>(
                "payment.completed", userId, payload, System.currentTimeMillis(), eventId, "payment-service"
        );

        when(idempotencyService.isEventProcessed(eventId)).thenReturn(false);

        // When
        adapter.handlePaymentCompleted(envelope, ack);

        // Then
        verify(subscriptionService, never()).activateOrRenewSubscription(any(), any());
        verify(idempotencyService, times(1)).markEventProcessed(eventId, "payment.completed");
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void givenMissingUserIdAndPlanId_whenHandlePaymentCompleted_thenSkipsActivationAndMarksProcessed() {
        // Given
        PaymentCompletedEvent payload = PaymentCompletedEvent.newBuilder()
                .setType("MEMBERSHIP")
                .build();

        EventEnvelope<PaymentCompletedEvent> envelope = new EventEnvelope<>(
                "payment.completed", "", payload, System.currentTimeMillis(), eventId, "payment-service"
        );

        when(idempotencyService.isEventProcessed(eventId)).thenReturn(false);

        // When
        adapter.handlePaymentCompleted(envelope, ack);

        // Then
        verify(subscriptionService, never()).activateOrRenewSubscription(any(), any());
        verify(idempotencyService, times(1)).markEventProcessed(eventId, "payment.completed");
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void givenNullPayloadPaymentCompletedEvent_whenHandlePaymentCompleted_thenThrowsIllegalArgumentException() {
        // Given
        EventEnvelope<PaymentCompletedEvent> envelope = new EventEnvelope<>(
                "payment.completed", userId, null, System.currentTimeMillis(), eventId, "payment-service"
        );

        when(idempotencyService.isEventProcessed(eventId)).thenReturn(false);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> adapter.handlePaymentCompleted(envelope, ack));
    }

    @Test
    void givenUserSuspendedEvent_whenHandleUserSuspended_thenSuspendsMemberAndSubscription() {
        // Given
        com.gym.proto.events.v1.UserSuspendedEvent payload = com.gym.proto.events.v1.UserSuspendedEvent.newBuilder()
                .setUserId(userId)
                .build();

        EventEnvelope<com.gym.proto.events.v1.UserSuspendedEvent> envelope = new EventEnvelope<>(
                "identity.user.suspended", userId, payload, System.currentTimeMillis(), eventId, "user-service"
        );

        when(idempotencyService.isEventProcessed(eventId)).thenReturn(false);

        // When
        adapter.handleUserSuspended(envelope, ack);

        // Then
        verify(subscriptionService, times(1)).suspendMemberAndSubscription(userId);
        verify(idempotencyService, times(1)).markEventProcessed(eventId, "identity.user.suspended");
        verify(ack, times(1)).acknowledge();
    }
}
