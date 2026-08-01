package com.gym.member.unit.service;

import com.gym.member.adapter.out.persistence.entity.ProcessedEventEntity;
import com.gym.member.adapter.out.persistence.repository.ProcessedEventJpaRepository;
import com.gym.member.application.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceUnitTest {

    @Mock
    private ProcessedEventJpaRepository repository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    private String eventId;

    @BeforeEach
    void setUp() {
        eventId = "evt-12345";
    }

    @Test
    void isEventProcessed_true() {
        when(repository.existsById(eventId)).thenReturn(true);

        assertTrue(idempotencyService.isEventProcessed(eventId));
    }

    @Test
    void isEventProcessed_false() {
        when(repository.existsById(eventId)).thenReturn(false);

        assertFalse(idempotencyService.isEventProcessed(eventId));
    }

    @Test
    void markEventProcessed_success() {
        idempotencyService.markEventProcessed(eventId, "user.registered");

        verify(repository, times(1)).save(any(ProcessedEventEntity.class));
    }
}
