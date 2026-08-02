package com.gym.member.application.service;

import com.gym.member.application.port.in.SubscriptionActivationUseCase;
import com.gym.member.application.port.in.SubscriptionExpiryUseCase;
import com.gym.member.domain.dto.MemberDto;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.events.v1.UserRegisteredEvent;
import com.gym.proto.events.v1.UserSuspendedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberEventProcessingService {

    private final IdempotencyService idempotencyService;
    private final MemberService memberService;
    private final SubscriptionActivationUseCase subscriptionActivationUseCase;
    private final SubscriptionExpiryUseCase subscriptionExpiryUseCase;

    public enum EventProcessingResult {
        PROCESSED,
        DUPLICATE,
        SKIPPED
    }

    private EventProcessingResult executeIdempotent(String eventId, String eventType, Supplier<EventProcessingResult> action) {
        if (!idempotencyService.claimEvent(eventId, eventType)) {
            log.info("Duplicate event claimed, skipping {} eventId: {}", eventType, eventId);
            return EventProcessingResult.DUPLICATE;
        }
        return action.get();
    }

    @Transactional
    public EventProcessingResult processUserRegistered(String eventId, String eventType, UserRegisteredEvent event) {
        return executeIdempotent(eventId, eventType, () -> {
            if (event == null || event.getUserId() == null || event.getUserId().isBlank() || event.getFullName() == null || event.getFullName().isBlank()) {
                throw new IllegalArgumentException("UserRegisteredEvent payload, userId, or fullName cannot be blank");
            }

            memberService.createMemberShell(event.getUserId(), event.getFullName(), event.getGymId());
            log.info("Successfully processed user registered event for user: {}", event.getUserId());
            return EventProcessingResult.PROCESSED;
        });
    }

    @Transactional
    public EventProcessingResult processPaymentCompleted(String eventId, String eventType, PaymentCompletedEvent event, String fallbackKey) {
        return executeIdempotent(eventId, eventType, () -> {
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
                subscriptionActivationUseCase.activateOrRenewSubscription(member.id().toString(), planId);
                log.info("Successfully processed payment completed event for member: {}", member.id());
                return EventProcessingResult.PROCESSED;
            }

            return EventProcessingResult.SKIPPED;
        });
    }

    @Transactional
    public EventProcessingResult processUserSuspended(String eventId, String eventType, UserSuspendedEvent event) {
        return executeIdempotent(eventId, eventType, () -> {
            if (event == null || event.getUserId() == null || event.getUserId().isBlank()) {
                throw new IllegalArgumentException("UserSuspendedEvent payload or userId cannot be blank");
            }

            subscriptionExpiryUseCase.suspendMemberAndSubscription(event.getUserId());
            log.info("Successfully processed user suspended event for user: {}", event.getUserId());
            return EventProcessingResult.PROCESSED;
        });
    }
}
