package com.gym.member.unit.kafka;

import com.gym.common.kafka.message.EventEnvelope;
import com.gym.member.payment.adapter.in.kafka.EventConsumerAdapter;
import com.gym.member.member.application.service.MemberEventProcessingService;
import com.gym.member.config.MemberProperties;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.events.v1.UserRegisteredEvent;
import com.gym.proto.events.v1.UserSuspendedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventConsumerAdapterUnitTest {

    @Mock
    private MemberEventProcessingService eventProcessingService;

    @Mock
    private MemberProperties properties;

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
    void givenUserRegisteredEvent_whenHandleUserRegistered_thenInvokesProcessingServiceAndAcknowledges() {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder()
                .setUserId(userId)
                .setFullName("John Doe")
                .setGymId(gymId)
                .build();

        EventEnvelope<UserRegisteredEvent> envelope = new EventEnvelope<>(
                "identity.user.registered", userId, payload, System.currentTimeMillis(), "trace-123", "user-service", eventId
        );

        when(eventProcessingService.processUserRegistered(eq(eventId), eq("identity.user.registered"), eq(payload)))
                .thenReturn(MemberEventProcessingService.EventProcessingResult.PROCESSED);

        // When
        adapter.handleUserRegistered(envelope, ack);

        // Then
        verify(eventProcessingService, times(1)).processUserRegistered(eq(eventId), eq("identity.user.registered"), eq(payload));
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void givenPaymentCompletedEvent_whenHandlePaymentCompleted_thenInvokesProcessingServiceAndAcknowledges() {
        // Given
        PaymentCompletedEvent payload = PaymentCompletedEvent.newBuilder()
                .setPaymentId(UUID.randomUUID().toString())
                .setUserId(userId)
                .setType("MEMBERSHIP")
                .setReferenceId(UUID.randomUUID().toString())
                .build();

        EventEnvelope<PaymentCompletedEvent> envelope = new EventEnvelope<>(
                "payment.completed", userId, payload, System.currentTimeMillis(), "trace-123", "payment-service", eventId
        );

        when(eventProcessingService.processPaymentCompleted(eq(eventId), eq("payment.completed"), eq(payload), eq(userId)))
                .thenReturn(MemberEventProcessingService.EventProcessingResult.PROCESSED);

        // When
        adapter.handlePaymentCompleted(envelope, ack);

        // Then
        verify(eventProcessingService, times(1)).processPaymentCompleted(eq(eventId), eq("payment.completed"), eq(payload), eq(userId));
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void givenUserSuspendedEvent_whenHandleUserSuspended_thenInvokesProcessingServiceAndAcknowledges() {
        // Given
        UserSuspendedEvent payload = UserSuspendedEvent.newBuilder()
                .setUserId(userId)
                .build();

        EventEnvelope<UserSuspendedEvent> envelope = new EventEnvelope<>(
                "identity.user.suspended", userId, payload, System.currentTimeMillis(), "trace-123", "user-service", eventId
        );

        when(eventProcessingService.processUserSuspended(eq(eventId), eq("identity.user.suspended"), eq(payload)))
                .thenReturn(MemberEventProcessingService.EventProcessingResult.PROCESSED);

        // When
        adapter.handleUserSuspended(envelope, ack);

        // Then
        verify(eventProcessingService, times(1)).processUserSuspended(eq(eventId), eq("identity.user.suspended"), eq(payload));
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void givenMissingEventIdAndStrictRequirement_whenHandleUserRegistered_thenThrowsIllegalArgumentException() {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder().setUserId(userId).build();
        EventEnvelope<UserRegisteredEvent> envelope = new EventEnvelope<>(
                "identity.user.registered", userId, payload, System.currentTimeMillis(), "trace-123", "user-service"
        );

        when(properties.requireEventId()).thenReturn(true);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> adapter.handleUserRegistered(envelope, ack));
        verify(ack, never()).acknowledge();
    }
}
