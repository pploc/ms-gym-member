package com.gym.member.adapter.in.kafka;

import com.gym.common.kafka.message.EventEnvelope;
import com.gym.member.application.service.IdempotencyService;
import com.gym.member.application.service.MemberService;
import com.gym.member.application.service.SubscriptionService;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.events.v1.UserRegisteredEvent;
import com.gym.member.domain.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.RetryableTopic;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumerAdapter {

    private final MemberService memberService;
    private final SubscriptionService subscriptionService;
    private final IdempotencyService idempotencyService;

    @KafkaListener(topics = "${gym.kafka.topics.user-registered:identity.user.registered}")
    public void handleUserRegistered(EventEnvelope<UserRegisteredEvent> envelope, Acknowledgment ack) {
        String eventId = resolveEventId(envelope);
        log.info("Received user registered event, key: {}, eventId: {}", envelope.key(), eventId);

        if (idempotencyService.isEventProcessed(eventId)) {
            log.info("Event already processed, skipping duplicate eventId: {}", eventId);
            ack.acknowledge();
            return;
        }

        try {
            UserRegisteredEvent event = envelope.payload();
            if (event == null || event.getUserId() == null || event.getUserId().isBlank()) {
                log.error("Failed to process user registered event: Payload is null or userId is missing. Key: {}", envelope.key());
                throw new IllegalArgumentException("UserRegisteredEvent payload or userId cannot be blank. Key: " + envelope.key());
            }

            memberService.createMemberShell(event.getUserId(), event.getFullName(), event.getGymId());
            idempotencyService.markEventProcessed(eventId, envelope.eventType());
            ack.acknowledge();
            log.info("Successfully created member shell for user: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Exception while handling user registered event key={}: {}", envelope.key(), e.getMessage(), e);
            throw e;
        }
    }

    @RetryableTopic(
            attempts = "${gym.kafka.retry.max-attempts:3}",
            backOff = @BackOff(
                    delayString = "${gym.kafka.retry.initial-interval-ms:1000}",
                    multiplierString = "${gym.kafka.retry.multiplier:2.0}"
            )
    )
    @KafkaListener(topics = "${gym.kafka.topics.payment-completed:payment.completed}")
    public void handlePaymentCompleted(EventEnvelope<PaymentCompletedEvent> envelope, Acknowledgment ack) {
        String eventId = resolveEventId(envelope);
        log.info("Received payment.completed event, key: {}, eventId: {}", envelope.key(), eventId);

        if (idempotencyService.isEventProcessed(eventId)) {
            log.info("Event already processed, skipping duplicate eventId: {}", eventId);
            ack.acknowledge();
            return;
        }

        try {
            PaymentCompletedEvent event = envelope.payload();
            if (event == null) {
                log.error("Failed to process payment.completed: Payload is null. Key: {}", envelope.key());
                throw new IllegalArgumentException("PaymentCompletedEvent payload cannot be null. Key: " + envelope.key());
            }

            if ("MEMBERSHIP".equalsIgnoreCase(event.getType())) {
                String userId = event.getUserId().isBlank() ? envelope.key() : event.getUserId();
                String planId = event.getReferenceId();
                if (userId != null && !userId.isBlank() && planId != null && !planId.isBlank()) {
                    MemberDto member = memberService.getMemberByUserId(userId);
                    subscriptionService.activateOrRenewSubscription(member.id().toString(), planId);
                    log.info("Successfully activated/renewed membership for member: {}", member.id());
                } else {
                    log.warn("Skipping payment.completed: Missing userId or planId (userId={}, planId={})", userId, planId);
                }
            }

            idempotencyService.markEventProcessed(eventId, envelope.eventType());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Exception while handling payment.completed event key={}: {}", envelope.key(), e.getMessage(), e);
            throw e;
        }
    }

    private String resolveEventId(EventEnvelope<?> envelope) {
        if (envelope.traceId() != null && !envelope.traceId().isBlank()) {
            return envelope.traceId();
        }
        return envelope.eventType() + ":" + envelope.key();
    }
}
