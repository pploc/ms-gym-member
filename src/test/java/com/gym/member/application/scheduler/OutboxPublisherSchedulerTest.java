package com.gym.member.application.scheduler;

import com.gym.member.adapter.out.persistence.entity.OutboxEventEntity;
import com.gym.member.adapter.out.persistence.repository.OutboxEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherSchedulerTest {

    @Mock
    private OutboxEventJpaRepository outboxRepository;

    @InjectMocks
    private OutboxPublisherScheduler scheduler;

    @Test
    void processOutboxEvents_empty_doesNothing() {
        when(outboxRepository.findPendingEvents()).thenReturn(List.of());

        scheduler.processOutboxEvents();

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void processOutboxEvents_success() {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setId(UUID.randomUUID());
        event.setTopic("membership.activated");
        event.setStatus("PENDING");

        when(outboxRepository.findPendingEvents()).thenReturn(List.of(event));

        scheduler.processOutboxEvents();

        assertEquals("PUBLISHED", event.getStatus());
        verify(outboxRepository, times(1)).save(event);
    }

    @Test
    void processOutboxEvents_failure_setsFailedStatus() {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setId(UUID.randomUUID());
        event.setTopic("membership.activated");
        event.setStatus("PENDING");

        when(outboxRepository.findPendingEvents()).thenReturn(List.of(event));
        doThrow(new RuntimeException("DB error")).doAnswer(inv -> inv.getArgument(0)).when(outboxRepository).save(event);

        scheduler.processOutboxEvents();

        assertEquals("FAILED", event.getStatus());
        verify(outboxRepository, times(2)).save(event);
    }
}
