package com.gym.member.unit.service;

import com.gym.member.shared.idempotency.entity.ProcessedEventEntity;
import com.gym.member.shared.idempotency.repository.ProcessedEventJpaRepository;
import com.gym.member.shared.idempotency.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void given_processed_event_when_is_event_processed_then_returns_true() {
        // given
        when(repository.existsById(eventId)).thenReturn(true);

        // when
        boolean result = idempotencyService.isEventProcessed(eventId);

        // then
        assertTrue(result);
    }

    @Test
    void given_unprocessed_event_when_is_event_processed_then_returns_false() {
        // given
        when(repository.existsById(eventId)).thenReturn(false);

        // when
        boolean result = idempotencyService.isEventProcessed(eventId);

        // then
        assertFalse(result);
    }

    @Test
    void given_new_event_when_claim_event_then_returns_true() {
        // given
        when(repository.insertIfNotExists(eq(eventId), eq("user.registered"), any())).thenReturn(1);

        // when
        boolean claimed = idempotencyService.claimEvent(eventId, "user.registered");

        // then
        assertTrue(claimed);
    }

    @Test
    void given_duplicate_event_when_claim_event_then_returns_false() {
        // given
        when(repository.insertIfNotExists(eq(eventId), eq("user.registered"), any())).thenReturn(0);

        // when
        boolean claimed = idempotencyService.claimEvent(eventId, "user.registered");

        // then
        assertFalse(claimed);
    }

    @Test
    void given_new_event_when_mark_event_processed_then_saves_processed_event_entity() {
        // given / when
        idempotencyService.markEventProcessed(eventId, "user.registered");

        // then
        verify(repository, times(1)).save(any(ProcessedEventEntity.class));
    }
}
