package com.gym.member.unit.scheduler;

import com.gym.member.adapter.out.persistence.entity.OutboxEventEntity;
import com.gym.member.adapter.out.persistence.repository.OutboxEventJpaRepository;
import com.gym.member.application.scheduler.OutboxPublisherScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherSchedulerUnitTest {

    @Mock
    private OutboxEventJpaRepository outboxRepository;

    @InjectMocks
    private OutboxPublisherScheduler scheduler;

    private OutboxEventEntity outboxEvent;

    @BeforeEach
    void setUp() {
        outboxEvent = new OutboxEventEntity();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setTopic("membership.activated");
        outboxEvent.setPayload("payload-json");
        outboxEvent.setStatus("PENDING");
        outboxEvent.setCreatedAt(Instant.now());
    }

    @Test
    void processOutboxEvents_success() {
        when(outboxRepository.findPendingEvents()).thenReturn(List.of(outboxEvent));

        scheduler.processOutboxEvents();

        verify(outboxRepository, times(1)).save(outboxEvent);
    }
}
