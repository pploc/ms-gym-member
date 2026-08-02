package com.gym.member.application.service;

import com.gym.member.domain.dto.MemberDto;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.events.v1.UserRegisteredEvent;
import com.gym.proto.events.v1.UserSuspendedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberEventProcessingService {

    private final IdempotencyService idempotencyService;
    private final MemberService memberService;
    private final SubscriptionActivationService subscriptionActivationService;
    private final SubscriptionExpiryService subscriptionExpiryService;

    public enum EventProcessingResult {
        PROCESSED,
        DUPLICATE,
        SKIPPED
    }

    @Transactional
    public EventProcessingResult processUserRegistered(String eventId, String eventType, UserRegisteredEvent event) {
        if (!idempotencyService.claimEvent(eventId, eventType)) {
            log.info("Duplicate event claimed, skipping user registered eventId: {}", eventId);
            return EventProcessingResult.DUPLICATE;
        }

        if (event == null || event.getUserId() == null || event.getUserId().isBlank()) {
            throw new IllegalArgumentException("UserRegisteredEvent payload or userId cannot be blank");
        }

        memberService.createMemberShell(event.getUserId(), event.getFullName(), event.getGymId());
        log.info("Successfully processed user registered event for user: {}", event.getUserId());
        return EventProcessingResult.PROCESSED;
    }

    @Transactional
    public EventProcessingResult processPaymentCompleted(String eventId, String eventType, PaymentCompletedEvent event, String fallbackKey) {
        if (!idempotencyService.claimEvent(eventId, eventType)) {
            log.info("Duplicate event claimed, skipping payment completed eventId: {}", eventId);
            return EventProcessingResult.DUPLICATE;
        }

        if (event == null) {
            throw new IllegalArgumentException("PaymentCompletedEvent payload cannot be null");
        }

        if ("MEMBERSHIP".equalsIgnoreCase(event.getType())) {
            String userId = event.getUserId().isBlank() ? fallbackKey : event.getUserId();
            String planId = event.getReferenceId();
            if (userId == null || userId.isBlank() || planId == null || planId.isBlank()) {
                throw new IllegalArgumentException("PaymentCompletedEvent missing userId or planId (userId=" + userId + ", planId=" + planId + ")");
            }

            MemberDto member = memberService.getMemberByUserId(userId);
            subscriptionActivationService.activateOrRenewSubscription(member.id().toString(), planId);
            log.info("Successfully processed payment completed event for member: {}", member.id());
            return EventProcessingResult.PROCESSED;
        }

        return EventProcessingResult.SKIPPED;
    }

    @Transactional
    public EventProcessingResult processUserSuspended(String eventId, String eventType, UserSuspendedEvent event) {
        if (!idempotencyService.claimEvent(eventId, eventType)) {
            log.info("Duplicate event claimed, skipping user suspended eventId: {}", eventId);
            return EventProcessingResult.DUPLICATE;
        }

        if (event == null || event.getUserId() == null || event.getUserId().isBlank()) {
            throw new IllegalArgumentException("UserSuspendedEvent payload or userId cannot be blank");
        }

        subscriptionExpiryService.suspendMemberAndSubscription(event.getUserId());
        log.info("Successfully processed user suspended event for user: {}", event.getUserId());
        return EventProcessingResult.PROCESSED;
    }
}
