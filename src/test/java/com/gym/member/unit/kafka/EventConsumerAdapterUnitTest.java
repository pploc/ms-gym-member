package com.gym.member.unit.kafka;

import com.gym.member.config.MemberProperties;
import com.gym.member.member.application.service.MemberEventProcessingService;
import com.gym.member.payment.adapter.in.kafka.EventConsumerAdapter;
import com.gym.member.payment.adapter.in.kafka.KafkaEventMetadata;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.events.v1.UserRegisteredEvent;
import com.gym.proto.events.v1.UserSuspendedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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

    private RecordHeaders createCanonicalHeaders(String eventId, String eventType, String source) {
        RecordHeaders headers = new RecordHeaders();
        if (eventId != null) {
            headers.add(new RecordHeader(KafkaEventMetadata.HEADER_EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8)));
        }
        if (eventType != null) {
            headers.add(new RecordHeader(KafkaEventMetadata.HEADER_EVENT_TYPE, eventType.getBytes(StandardCharsets.UTF_8)));
        }
        if (source != null) {
            headers.add(new RecordHeader(KafkaEventMetadata.HEADER_SOURCE, source.getBytes(StandardCharsets.UTF_8)));
        }
        headers.add(new RecordHeader(KafkaEventMetadata.HEADER_TIMESTAMP, String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8)));
        return headers;
    }

    @Test
    void givenUserRegisteredEvent_whenHandleUserRegistered_thenInvokesProcessingServiceAndAcknowledges() {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder()
                .setUserId(userId)
                .setFullName("John Doe")
                .build();

        RecordHeaders headers = createCanonicalHeaders(eventId, payload.getDescriptorForType().getFullName(), "user-service");
        ConsumerRecord<String, UserRegisteredEvent> record = new ConsumerRecord<>(
                "identity.user.registered", 0, 0L, userId, payload
        );
        headers.forEach(h -> record.headers().add(h));

        when(eventProcessingService.processUserRegistered(eq(eventId), eq(payload.getDescriptorForType().getFullName()), eq(payload)))
                .thenReturn(MemberEventProcessingService.EventProcessingResult.PROCESSED);

        // When
        adapter.handleUserRegistered(record, ack);

        // Then
        verify(eventProcessingService, times(1)).processUserRegistered(eq(eventId), eq(payload.getDescriptorForType().getFullName()), eq(payload));
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

        RecordHeaders headers = createCanonicalHeaders(eventId, payload.getDescriptorForType().getFullName(), "payment-service");
        ConsumerRecord<String, PaymentCompletedEvent> record = new ConsumerRecord<>(
                "payment.completed", 0, 0L, userId, payload
        );
        headers.forEach(h -> record.headers().add(h));

        when(eventProcessingService.processPaymentCompleted(eq(eventId), eq(payload.getDescriptorForType().getFullName()), eq(payload), eq(userId)))
                .thenReturn(MemberEventProcessingService.EventProcessingResult.PROCESSED);

        // When
        adapter.handlePaymentCompleted(record, ack);

        // Then
        verify(eventProcessingService, times(1)).processPaymentCompleted(eq(eventId), eq(payload.getDescriptorForType().getFullName()), eq(payload), eq(userId));
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void givenUserSuspendedEvent_whenHandleUserSuspended_thenInvokesProcessingServiceAndAcknowledges() {
        // Given
        UserSuspendedEvent payload = UserSuspendedEvent.newBuilder()
                .setUserId(userId)
                .build();

        RecordHeaders headers = createCanonicalHeaders(eventId, payload.getDescriptorForType().getFullName(), "user-service");
        ConsumerRecord<String, UserSuspendedEvent> record = new ConsumerRecord<>(
                "identity.user.suspended", 0, 0L, userId, payload
        );
        headers.forEach(h -> record.headers().add(h));

        when(eventProcessingService.processUserSuspended(eq(eventId), eq(payload.getDescriptorForType().getFullName()), eq(payload)))
                .thenReturn(MemberEventProcessingService.EventProcessingResult.PROCESSED);

        // When
        adapter.handleUserSuspended(record, ack);

        // Then
        verify(eventProcessingService, times(1)).processUserSuspended(eq(eventId), eq(payload.getDescriptorForType().getFullName()), eq(payload));
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void givenMissingEventIdAndStrictRequirement_whenHandleUserRegistered_thenThrowsIllegalArgumentException() {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder().setUserId(userId).build();
        RecordHeaders headers = createCanonicalHeaders(null, payload.getDescriptorForType().getFullName(), "user-service");
        ConsumerRecord<String, UserRegisteredEvent> record = new ConsumerRecord<>(
                "identity.user.registered", 0, 0L, userId, payload
        );
        headers.forEach(h -> record.headers().add(h));

        when(properties.requireEventId()).thenReturn(true);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> adapter.handleUserRegistered(record, ack));
        verify(ack, never()).acknowledge();
    }

    @Test
    void givenMissingEventIdAndNonStrictRequirement_whenHandleUserRegistered_thenUsesLegacyFallbackId() {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder().setUserId(userId).build();
        RecordHeaders headers = createCanonicalHeaders(null, payload.getDescriptorForType().getFullName(), "user-service");
        ConsumerRecord<String, UserRegisteredEvent> record = new ConsumerRecord<>(
                "identity.user.registered", 0, 10L, userId, payload
        );
        headers.forEach(h -> record.headers().add(h));

        when(properties.requireEventId()).thenReturn(false);
        when(eventProcessingService.processUserRegistered(eq("legacy:identity.user.registered:0:10"), eq(payload.getDescriptorForType().getFullName()), eq(payload)))
                .thenReturn(MemberEventProcessingService.EventProcessingResult.PROCESSED);

        // When
        adapter.handleUserRegistered(record, ack);

        // Then
        verify(eventProcessingService, times(1)).processUserRegistered(eq("legacy:identity.user.registered:0:10"), eq(payload.getDescriptorForType().getFullName()), eq(payload));
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void givenMismatchedEventTypeHeader_whenHandleUserRegistered_thenThrowsIllegalArgumentException() {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder().setUserId(userId).build();
        RecordHeaders headers = createCanonicalHeaders(eventId, "wrong.event.Type", "user-service");
        ConsumerRecord<String, UserRegisteredEvent> record = new ConsumerRecord<>(
                "identity.user.registered", 0, 0L, userId, payload
        );
        headers.forEach(h -> record.headers().add(h));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> adapter.handleUserRegistered(record, ack));
        verify(ack, never()).acknowledge();
    }

    @Test
    void givenMissingEventTypeHeader_whenExtractAndValidate_thenUsesDescriptorFullName() {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder().setUserId(userId).build();
        ConsumerRecord<String, UserRegisteredEvent> record = new ConsumerRecord<>(
                "identity.user.registered", 0, 0L, userId, payload
        );
        record.headers().add(new RecordHeader(KafkaEventMetadata.HEADER_EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8)));

        // When
        KafkaEventMetadata metadata = KafkaEventMetadata.extractAndValidate(record, properties);

        // Then
        assertEquals(eventId, metadata.eventId());
        assertEquals(payload.getDescriptorForType().getFullName(), metadata.eventType());
    }

    @Test
    void givenNullHeaders_whenGetHeaderValue_returnsNull() {
        assertNull(KafkaEventMetadata.getHeaderValue(null, "key"));
    }

    @Test
    void givenHeaderWithNullValue_whenGetHeaderValue_returnsNull() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("test-key", null));
        assertNull(KafkaEventMetadata.getHeaderValue(headers, "test-key"));
    }
}
