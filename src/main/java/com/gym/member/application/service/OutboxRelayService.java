package com.gym.member.application.service;

import com.gym.member.adapter.out.persistence.entity.OutboxEventEntity;
import com.gym.member.adapter.out.persistence.repository.OutboxEventJpaRepository;
import com.gym.member.config.MemberProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private final OutboxEventJpaRepository repository;
    private final MemberProperties properties;
    private final Clock clock;

    @Transactional
    public List<OutboxEventEntity> claimBatch(int batchSize, Duration leaseDuration) {
        Instant now = Instant.now(clock);
        List<OutboxEventEntity> events = repository.findReady(
                now,
                OutboxEventEntity.OutboxStatus.PENDING,
                OutboxEventEntity.OutboxStatus.IN_FLIGHT,
                batchSize
        );
        for (OutboxEventEntity event : events) {
            event.setStatus(OutboxEventEntity.OutboxStatus.IN_FLIGHT);
            event.setNextAttemptAt(now.plus(leaseDuration));
        }
        return repository.saveAll(events);
    }

    @Transactional
    public void markPublished(UUID eventId) {
        repository.findById(eventId).ifPresent(event -> {
            event.setStatus(OutboxEventEntity.OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now(clock));
            event.setLastError(null);
            repository.save(event);
        });
    }

    @Transactional
    public void markFailedOrRetry(UUID eventId, Exception failure) {
        repository.findById(eventId).ifPresent(event -> {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            String errorMsg = failure.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            event.setLastError(errorMsg);
            if (attempts >= properties.outbox().maxAttempts()) {
                event.setStatus(OutboxEventEntity.OutboxStatus.FAILED);
            } else {
                event.setStatus(OutboxEventEntity.OutboxStatus.PENDING);
                event.setNextAttemptAt(Instant.now(clock).plus(properties.outbox().retryDelay()));
            }
            repository.save(event);
            log.error("Outbox publish failed for event {} (attempt {})", eventId, attempts, failure);
        });
    }
}
