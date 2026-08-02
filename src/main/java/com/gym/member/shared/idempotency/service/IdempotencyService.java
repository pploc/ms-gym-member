package com.gym.member.shared.idempotency.service;

import com.gym.member.shared.idempotency.entity.ProcessedEventEntity;
import com.gym.member.shared.idempotency.repository.ProcessedEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ProcessedEventJpaRepository processedEventRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean isEventProcessed(String eventId) {
        return processedEventRepository.existsById(eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimEvent(String eventId, String eventType) {
        int inserted = processedEventRepository.insertIfNotExists(eventId, eventType, Instant.now(clock));
        return inserted > 0;
    }

    @Transactional
    public void markEventProcessed(String eventId, String eventType) {
        processedEventRepository.save(new ProcessedEventEntity(eventId, eventType, Instant.now(clock)));
    }
}
