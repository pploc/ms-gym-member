package com.gym.member.payment.adapter.in.kafka;

import com.gym.common.kafka.message.EventEnvelope;
import com.gym.member.member.application.service.MemberEventProcessingService;
import com.gym.member.config.MemberProperties;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.events.v1.UserRegisteredEvent;
import com.gym.proto.events.v1.UserSuspendedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumerAdapter {

    private final MemberEventProcessingService eventProcessingService;
    private final MemberProperties properties;

    @KafkaListener(topics = "${gym.kafka.topics.user-registered:identity.user.registered}")
    public void handleUserRegistered(EventEnvelope<UserRegisteredEvent> envelope, Acknowledgment ack) {
        String eventId = resolveEventId(envelope);
        log.info("Received user registered event, key: {}, eventId: {}", envelope.key(), eventId);
        eventProcessingService.processUserRegistered(eventId, envelope.eventType(), envelope.payload());
        ack.acknowledge();
    }

    @KafkaListener(topics = "${gym.kafka.topics.payment-completed:payment.completed}")
    public void handlePaymentCompleted(EventEnvelope<PaymentCompletedEvent> envelope, Acknowledgment ack) {
        String eventId = resolveEventId(envelope);
        log.info("Received payment.completed event, key: {}, eventId: {}", envelope.key(), eventId);
        eventProcessingService.processPaymentCompleted(eventId, envelope.eventType(), envelope.payload(), envelope.key());
        ack.acknowledge();
    }

    @KafkaListener(topics = "${gym.kafka.topics.user-suspended:identity.user.suspended}")
    public void handleUserSuspended(EventEnvelope<UserSuspendedEvent> envelope, Acknowledgment ack) {
        String eventId = resolveEventId(envelope);
        log.info("Received user suspended event, key: {}, eventId: {}", envelope.key(), eventId);
        eventProcessingService.processUserSuspended(eventId, envelope.eventType(), envelope.payload());
        ack.acknowledge();
    }

    private static final String LEGACY_EVENT_ID_PREFIX = "legacy:";

    private String resolveEventId(EventEnvelope<?> envelope) {
        if (envelope.eventId() != null && !envelope.eventId().isBlank()) {
            return envelope.eventId();
        }
        if (properties.requireEventId()) {
            throw new IllegalArgumentException("Event envelope missing required event_id for strict DLT processing");
        }
        String fallbackId = LEGACY_EVENT_ID_PREFIX + envelope.source() + ":" + envelope.eventType() + ":" + envelope.key() + ":" + envelope.timestamp();
        log.warn("Missing event_id in event envelope; using metered compatibility fallback event ID: {}", fallbackId);
        return fallbackId;
    }
}
