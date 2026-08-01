package com.gym.member.application.service;

import com.gym.member.adapter.out.persistence.entity.ProcessedEventEntity;
import com.gym.member.adapter.out.persistence.repository.ProcessedEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ProcessedEventJpaRepository processedEventRepository;

    @Transactional(readOnly = true)
    public boolean isEventProcessed(String eventId) {
        return processedEventRepository.existsById(eventId);
    }

    @Transactional
    public void markEventProcessed(String eventId, String eventType) {
        processedEventRepository.save(new ProcessedEventEntity(eventId, eventType, Instant.now()));
    }
}
