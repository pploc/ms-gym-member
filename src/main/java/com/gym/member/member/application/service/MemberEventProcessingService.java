package com.gym.member.member.application.service;

import com.gym.member.shared.idempotency.service.IdempotencyService;

import com.gym.member.member.application.port.in.MemberUseCase;
import com.gym.member.member.application.port.in.MemberSuspensionUseCase;
import com.gym.member.member.application.service.strategy.PaymentTypeHandler;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.events.v1.UserRegisteredEvent;
import com.gym.proto.events.v1.UserSuspendedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberEventProcessingService {

    private final IdempotencyService idempotencyService;
    private final MemberUseCase memberUseCase;
    private final MemberSuspensionUseCase memberSuspensionUseCase;
    private final List<PaymentTypeHandler> paymentTypeHandlers;

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
        try {
            return action.get();
        } catch (Throwable t) {
            try {
                idempotencyService.releaseClaim(eventId);
            } catch (Exception e) {
                log.error("Failed to release idempotency claim for eventId: {}", eventId, e);
            }
            throw t;
        }
    }

    @Transactional
    public EventProcessingResult processUserRegistered(String eventId, String eventType, UserRegisteredEvent event) {
        return executeIdempotent(eventId, eventType, () -> {
            if (event == null || event.getUserId() == null || event.getUserId().isBlank() || event.getFullName() == null || event.getFullName().isBlank()) {
                throw new IllegalArgumentException("UserRegisteredEvent payload, userId, or fullName cannot be blank");
            }

            memberUseCase.createMemberShell(event.getUserId(), event.getFullName());
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

            for (PaymentTypeHandler handler : paymentTypeHandlers) {
                if (handler.supports(event.getType())) {
                    handler.handle(event, fallbackKey);
                    return EventProcessingResult.PROCESSED;
                }
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

            memberSuspensionUseCase.suspendMemberAndSubscription(event.getUserId());
            log.info("Successfully processed user suspended event for user: {}", event.getUserId());
            return EventProcessingResult.PROCESSED;
        });
    }
}
