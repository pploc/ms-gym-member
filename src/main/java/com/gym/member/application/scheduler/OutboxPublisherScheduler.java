package com.gym.member.application.scheduler;

import com.gym.member.adapter.out.persistence.entity.OutboxEventEntity;
import com.gym.member.adapter.out.persistence.repository.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OutboxPublisherScheduler {

    private final OutboxEventJpaRepository outboxRepository;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEventEntity> pending = outboxRepository.findPendingEvents();
        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEventEntity event : pending) {
            try {
                event.setStatus("PUBLISHED");
                outboxRepository.save(event);
                log.info("Processed outbox event id: {}, topic: {}", event.getId(), event.getTopic());
            } catch (Exception e) {
                log.error("Failed to process outbox event id: {}", event.getId(), e);
                event.setStatus("FAILED");
                outboxRepository.save(event);
            }
        }
    }
}
