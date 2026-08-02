package com.gym.member.unit.scheduler;


import com.google.protobuf.Message;

import com.gym.common.kafka.producer.EventPublisher;
import com.gym.member.shared.outbox.entity.OutboxEventEntity;
import com.gym.member.shared.outbox.scheduler.OutboxPublisherScheduler;
import com.gym.member.shared.outbox.service.OutboxPayloadParser;
import com.gym.member.shared.outbox.service.OutboxRelayService;
import com.gym.member.config.MemberProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherSchedulerUnitTest {

    @Mock
    private OutboxRelayService outboxRelayService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private OutboxPayloadParser payloadParser;

    @Mock
    private MemberProperties properties;

    @InjectMocks
    private OutboxPublisherScheduler scheduler;

    private OutboxEventEntity outboxEvent;
    private Message mockMessage;

    @BeforeEach
    void setUp() {
        outboxEvent = new OutboxEventEntity();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setAggregateId("agg-123");
        outboxEvent.setEventType("MembershipActivatedEvent");
        outboxEvent.setTopic("membership.activated");
        outboxEvent.setPayload("payload-json");
        outboxEvent.setStatus(OutboxEventEntity.OutboxStatus.PENDING);
        outboxEvent.setCreatedAt(Instant.now());
        outboxEvent.setNextAttemptAt(Instant.now());

        mockMessage = mock(Message.class);

        MemberProperties.OutboxProperties outboxProps = new MemberProperties.OutboxProperties(50, Duration.ofSeconds(2), Duration.ofSeconds(10), 10, Duration.ofMinutes(1));
        when(properties.outbox()).thenReturn(outboxProps);
    }

    @Test
    void givenPendingOutboxEvents_whenProcessOutboxEvents_thenPublishesAndMarksPublished() {
        // Given
        when(outboxRelayService.claimBatch(anyInt(), any(Duration.class))).thenReturn(List.of(outboxEvent));
        when(payloadParser.parse("MembershipActivatedEvent", "payload-json")).thenReturn(mockMessage);

        // When
        scheduler.processOutboxEvents();

        // Then
        verify(eventPublisher, times(1)).publish(eq("membership.activated"), eq("agg-123"), eq(mockMessage), eq(outboxEvent.getId().toString()), anyMap());
        verify(outboxRelayService, times(1)).markPublished(outboxEvent.getId());
    }

    @Test
    void givenPublishFailure_whenProcessOutboxEvents_thenMarksFailedOrRetry() {
        // Given
        when(outboxRelayService.claimBatch(anyInt(), any(Duration.class))).thenReturn(List.of(outboxEvent));
        when(payloadParser.parse(anyString(), anyString())).thenThrow(new IllegalArgumentException("Invalid payload"));

        // When
        scheduler.processOutboxEvents();

        // Then
        verify(outboxRelayService, times(1)).markFailedOrRetry(eq(outboxEvent.getId()), any(IllegalArgumentException.class));
    }
}
