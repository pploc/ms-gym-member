package com.gym.member.application.service;

import com.gym.member.adapter.out.persistence.entity.ProcessedEventEntity;
import com.gym.member.adapter.out.persistence.repository.ProcessedEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private ProcessedEventJpaRepository processedEventRepository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    void isEventProcessed_true() {
        when(processedEventRepository.existsById("event-123")).thenReturn(true);

        assertTrue(idempotencyService.isEventProcessed("event-123"));
    }

    @Test
    void isEventProcessed_false() {
        when(processedEventRepository.existsById("event-123")).thenReturn(false);

        assertFalse(idempotencyService.isEventProcessed("event-123"));
    }

    @Test
    void markEventProcessed_success() {
        idempotencyService.markEventProcessed("event-123", "user.registered");

        verify(processedEventRepository, times(1)).save(any(ProcessedEventEntity.class));
    }
}
