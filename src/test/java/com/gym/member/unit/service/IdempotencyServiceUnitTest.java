package com.gym.member.unit.service;

import com.gym.member.adapter.out.persistence.entity.ProcessedEventEntity;
import com.gym.member.adapter.out.persistence.repository.ProcessedEventJpaRepository;
import com.gym.member.application.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceUnitTest {

    @Mock
    private ProcessedEventJpaRepository repository;

    @Spy
    private Clock clock = Clock.systemUTC();

    @InjectMocks
    private IdempotencyService idempotencyService;

    private String eventId;

    @BeforeEach
    void setUp() {
        eventId = "evt-12345";
    }

    @Test
    void givenProcessedEvent_whenIsEventProcessed_thenReturnsTrue() {
        // Given
        when(repository.existsById(eventId)).thenReturn(true);

        // When
        boolean result = idempotencyService.isEventProcessed(eventId);

        // Then
        assertTrue(result);
    }

    @Test
    void givenUnprocessedEvent_whenIsEventProcessed_thenReturnsFalse() {
        // Given
        when(repository.existsById(eventId)).thenReturn(false);

        // When
        boolean result = idempotencyService.isEventProcessed(eventId);

        // Then
        assertFalse(result);
    }

    @Test
    void givenNewEvent_whenClaimEvent_thenReturnsTrue() {
        // Given
        when(repository.insertIfNotExists(eq(eventId), eq("user.registered"), any())).thenReturn(1);

        // When
        boolean claimed = idempotencyService.claimEvent(eventId, "user.registered");

        // Then
        assertTrue(claimed);
    }

    @Test
    void givenDuplicateEvent_whenClaimEvent_thenReturnsFalse() {
        // Given
        when(repository.insertIfNotExists(eq(eventId), eq("user.registered"), any())).thenReturn(0);

        // When
        boolean claimed = idempotencyService.claimEvent(eventId, "user.registered");

        // Then
        assertFalse(claimed);
    }

    @Test
    void givenNewEvent_whenMarkEventProcessed_thenSavesProcessedEventEntity() {
        // Given - New event

        // When
        idempotencyService.markEventProcessed(eventId, "user.registered");

        // Then
        verify(repository, times(1)).save(any(ProcessedEventEntity.class));
    }
}
