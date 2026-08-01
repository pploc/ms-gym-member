package com.gym.member.adapter.in.kafka;

import com.gym.common.kafka.message.EventEnvelope;
import com.gym.member.application.service.MemberService;
import com.gym.member.application.service.SubscriptionService;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.events.v1.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumerAdapter {

    private final MemberService memberService;
    private final SubscriptionService subscriptionService;

    @KafkaListener(topics = "identity.user.registered", groupId = "${spring.kafka.consumer.group-id:ms-gym-member-group}")
    public void handleUserRegistered(EventEnvelope<UserRegisteredEvent> envelope) {
        log.info("Received identity.user.registered event, key: {}", envelope.key());
        try {
            UserRegisteredEvent event = envelope.payload();
            if (event != null && !event.getUserId().isBlank()) {
                memberService.createMemberShell(event.getUserId(), event.getFullName(), event.getGymId());
                log.info("Created member shell for user: {}", event.getUserId());
            }
        } catch (Exception e) {
            log.error("Failed to process identity.user.registered event", e);
        }
    }

    @KafkaListener(topics = "payment.completed", groupId = "${spring.kafka.consumer.group-id:ms-gym-member-group}")
    public void handlePaymentCompleted(EventEnvelope<PaymentCompletedEvent> envelope) {
        log.info("Received payment.completed event, key: {}", envelope.key());
        try {
            PaymentCompletedEvent event = envelope.payload();
            if (event != null && "MEMBERSHIP".equalsIgnoreCase(event.getType())) {
                String memberId = envelope.key();
                String planId = event.getReferenceId();
                if (memberId != null && !memberId.isBlank() && planId != null && !planId.isBlank()) {
                    subscriptionService.activateOrRenewSubscription(memberId, planId);
                    log.info("Activated/Renewed membership for member: {}", memberId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process payment.completed event", e);
        }
    }
}
