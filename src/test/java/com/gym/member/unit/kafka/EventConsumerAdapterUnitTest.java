package com.gym.member.unit.kafka;

import com.google.protobuf.Message;
import com.gym.common.kafka.consumer.DecodedKafkaRecord;
import com.gym.common.kafka.consumer.DeliverySleeper;
import com.gym.common.kafka.consumer.PermanentKafkaException;
import com.gym.common.kafka.consumer.RawDeliveryCoordinatorFactory;
import com.gym.common.kafka.consumer.RawDlqPublisher;
import com.gym.common.kafka.consumer.RawKafkaDecoder;
import com.gym.common.kafka.consumer.RawKafkaRecord;
import com.gym.member.config.MemberProperties;
import com.gym.member.member.application.service.MemberEventProcessingService;
import com.gym.member.payment.adapter.in.kafka.EventConsumerAdapter;
import com.gym.member.payment.adapter.in.kafka.KafkaEventMetadata;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.events.v1.UserRegisteredEvent;
import com.gym.proto.events.v1.UserSuspendedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventConsumerAdapterUnitTest {

    @Mock
    private MemberEventProcessingService eventProcessingService;

    @Mock
    private MemberProperties properties;

    @Mock
    private Acknowledgment acknowledgment;

    @Mock
    private RawDlqPublisher dlqPublisher;

    private String eventId;
    private String userId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
    }

    @Test
    void givenRawUserRegisteredEvent_whenConsume_thenDecodesProcessesAndAcknowledges() {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder()
                .setUserId(userId)
                .setFullName("John Doe")
                .build();
        EventConsumerAdapter adapter = adapterDecoding(payload);
        ConsumerRecord<byte[], byte[]> record = rawRecord("identity.user.registered.v1", payload, eventId, userId);

        // When
        adapter.consume(record, acknowledgment);

        // Then
        verify(eventProcessingService).processUserRegistered(
                eventId, payload.getDescriptorForType().getFullName(), payload
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    void givenRawPaymentCompletedEvent_whenConsume_thenDecodesProcessesAndAcknowledges() {
        // Given
        PaymentCompletedEvent payload = PaymentCompletedEvent.newBuilder()
                .setPaymentId(UUID.randomUUID().toString())
                .setUserId(userId)
                .setType("MEMBERSHIP")
                .setReferenceId(UUID.randomUUID().toString())
                .build();
        EventConsumerAdapter adapter = adapterDecoding(payload);
        ConsumerRecord<byte[], byte[]> record = rawRecord("payment.completed.v1", payload, eventId, userId);

        // When
        adapter.consume(record, acknowledgment);

        // Then
        verify(eventProcessingService).processPaymentCompleted(
                eventId, payload.getDescriptorForType().getFullName(), payload, userId
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    void givenRawUserSuspendedEvent_whenConsume_thenDecodesProcessesAndAcknowledges() {
        // Given
        UserSuspendedEvent payload = UserSuspendedEvent.newBuilder().setUserId(userId).build();
        EventConsumerAdapter adapter = adapterDecoding(payload);
        ConsumerRecord<byte[], byte[]> record = rawRecord("identity.user.suspended.v1", payload, eventId, userId);

        // When
        adapter.consume(record, acknowledgment);

        // Then
        verify(eventProcessingService).processUserSuspended(
                eventId, payload.getDescriptorForType().getFullName(), payload
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    void givenMissingEventIdAndStrictRequirement_whenHandle_thenRejectsWithoutProcessing() throws Exception {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder().setUserId(userId).build();
        EventConsumerAdapter adapter = adapterDecoding(payload);
        ConsumerRecord<byte[], byte[]> record = rawRecord("identity.user.registered.v1", payload, null, userId);
        when(properties.requireEventId()).thenReturn(true);

        // When
        adapter.consume(record, acknowledgment);

        // Then
        verify(eventProcessingService, never()).processUserRegistered(eq(eventId), eq(payload.getDescriptorForType().getFullName()), eq(payload));
        verify(dlqPublisher).publish(
                org.mockito.ArgumentMatchers.any(RawKafkaRecord.class),
                eq("Kafka message is permanently invalid"),
                eq(1)
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    void givenMissingEventIdAndNonStrictRequirement_whenHandle_thenUsesLegacyFallbackId() {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder().setUserId(userId).build();
        EventConsumerAdapter adapter = adapterDecoding(payload);
        ConsumerRecord<byte[], byte[]> record = rawRecord("identity.user.registered.v1", payload, null, userId);

        // When
        adapter.consume(record, acknowledgment);

        // Then
        verify(eventProcessingService).processUserRegistered(
                "legacy:identity.user.registered.v1:0:10",
                payload.getDescriptorForType().getFullName(),
                payload
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    void givenUnsupportedDecodedMessage_whenHandle_thenThrowsPermanentKafkaException() {
        // Given
        com.google.protobuf.Empty payload = com.google.protobuf.Empty.getDefaultInstance();
        EventConsumerAdapter adapter = adapterDecoding(payload);
        RawKafkaRecord raw = RawKafkaRecord.from(rawRecord("unsupported.v1", payload, eventId, userId));

        // When / Then
        assertThrows(PermanentKafkaException.class, () -> adapter.handle(new DecodedKafkaRecord(raw, payload)));
    }

    @Test
    void givenDynamicMessageUserRegistered_whenHandle_thenConvertsAndProcesses() throws Exception {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder()
                .setUserId(userId)
                .setFullName("Jane Dynamic")
                .build();
        com.google.protobuf.DynamicMessage dynamic = com.google.protobuf.DynamicMessage.parseFrom(
                payload.getDescriptorForType(), payload.toByteString()
        );
        EventConsumerAdapter adapter = adapterDecoding(dynamic);
        ConsumerRecord<byte[], byte[]> record = rawRecord("identity.user.registered.v1", payload, eventId, userId);

        // When
        adapter.consume(record, acknowledgment);

        // Then
        verify(eventProcessingService).processUserRegistered(
                eq(eventId),
                eq(payload.getDescriptorForType().getFullName()),
                org.mockito.ArgumentMatchers.argThat(event ->
                        userId.equals(event.getUserId()) && "Jane Dynamic".equals(event.getFullName())
                )
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    void givenMissingEventTypeHeader_whenExtractAndValidate_thenUsesDescriptorFullName() {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder().setUserId(userId).build();
        ConsumerRecord<String, UserRegisteredEvent> record = new ConsumerRecord<>(
                "identity.user.registered.v1", 0, 0L, userId, payload
        );
        record.headers().add(new RecordHeader(KafkaEventMetadata.HEADER_EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8)));

        // When
        KafkaEventMetadata metadata = KafkaEventMetadata.extractAndValidate(record, properties);

        // Then
        assertEquals(eventId, metadata.eventId());
        assertEquals(payload.getDescriptorForType().getFullName(), metadata.eventType());
    }

    @Test
    void givenNullHeaders_whenGetHeaderValue_thenReturnsNull() {
        assertNull(KafkaEventMetadata.getHeaderValue(null, "key"));
    }

    private EventConsumerAdapter adapterDecoding(Message payload) {
        RawKafkaDecoder decoder = raw -> new DecodedKafkaRecord(raw, payload);
        DeliverySleeper sleeper = duration -> { };
        return new EventConsumerAdapter(
                eventProcessingService,
                properties,
                new RawDeliveryCoordinatorFactory(decoder, dlqPublisher, sleeper)
        );
    }

    private ConsumerRecord<byte[], byte[]> rawRecord(String topic, Message payload, String id, String key) {
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>(
                topic,
                0,
                10L,
                key == null ? null : key.getBytes(StandardCharsets.UTF_8),
                payload.toByteArray()
        );
        if (id != null) {
            record.headers().add(new RecordHeader(KafkaEventMetadata.HEADER_EVENT_ID, id.getBytes(StandardCharsets.UTF_8)));
        }
        record.headers().add(new RecordHeader(
                KafkaEventMetadata.HEADER_EVENT_TYPE,
                payload.getDescriptorForType().getFullName().getBytes(StandardCharsets.UTF_8)
        ));
        record.headers().add(new RecordHeader(KafkaEventMetadata.HEADER_SOURCE, "test-service".getBytes(StandardCharsets.UTF_8)));
        return record;
    }
}
