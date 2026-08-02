package com.gym.member.unit.service;

import com.gym.member.config.MemberProperties;
import com.gym.member.shared.outbox.entity.OutboxEventEntity;
import com.gym.member.shared.outbox.repository.OutboxEventJpaRepository;
import com.gym.member.shared.outbox.service.OutboxRelayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceUnitTest {

    @Mock
    private OutboxEventJpaRepository repository;

    @Mock
    private MemberProperties properties;

    @Spy
    private Clock clock = Clock.systemUTC();

    @InjectMocks
    private OutboxRelayService outboxRelayService;

    private UUID eventId;
    private OutboxEventEntity eventEntity;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        eventEntity = new OutboxEventEntity();
        eventEntity.setId(eventId);
        eventEntity.setStatus(OutboxEventEntity.OutboxStatus.PENDING);
        eventEntity.setAttempts(0);
    }

    @Test
    void givenPendingEvents_whenClaimBatch_thenUpdatesStatusToInFlightAndSaves() {
        when(repository.findReady(any(), eq(OutboxEventEntity.OutboxStatus.PENDING), eq(OutboxEventEntity.OutboxStatus.IN_FLIGHT), eq(10)))
                .thenReturn(List.of(eventEntity));
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<OutboxEventEntity> claimed = outboxRelayService.claimBatch(10, Duration.ofMinutes(5));

        assertEquals(1, claimed.size());
        assertEquals(OutboxEventEntity.OutboxStatus.IN_FLIGHT, claimed.get(0).getStatus());
        assertNotNull(claimed.get(0).getNextAttemptAt());
    }

    @Test
    void givenEvent_whenMarkPublished_thenUpdatesStatusToPublishedAndClearsError() {
        when(repository.findById(eventId)).thenReturn(Optional.of(eventEntity));

        outboxRelayService.markPublished(eventId);

        assertEquals(OutboxEventEntity.OutboxStatus.PUBLISHED, eventEntity.getStatus());
        assertNotNull(eventEntity.getPublishedAt());
        assertNull(eventEntity.getLastError());
        verify(repository, times(1)).save(eventEntity);
    }

    @Test
    void givenFailure_whenMarkFailedOrRetryUnderMaxAttempts_thenIncrementsAttemptAndSetsPending() {
        MemberProperties.OutboxProperties outboxProperties = new MemberProperties.OutboxProperties(10, Duration.ofSeconds(2), Duration.ofSeconds(30), 3, Duration.ofMinutes(5));
        when(properties.outbox()).thenReturn(outboxProperties);
        when(repository.findById(eventId)).thenReturn(Optional.of(eventEntity));

        Exception ex = new RuntimeException("Kafka connection timeout");
        outboxRelayService.markFailedOrRetry(eventId, ex);

        assertEquals(1, eventEntity.getAttempts());
        assertEquals(OutboxEventEntity.OutboxStatus.PENDING, eventEntity.getStatus());
        assertEquals("Kafka connection timeout", eventEntity.getLastError());
        verify(repository, times(1)).save(eventEntity);
    }

    @Test
    void givenLongErrorMessage_whenMarkFailedOrRetry_thenTruncatesErrorTo500Chars() {
        MemberProperties.OutboxProperties outboxProperties = new MemberProperties.OutboxProperties(10, Duration.ofSeconds(2), Duration.ofSeconds(30), 3, Duration.ofMinutes(5));
        when(properties.outbox()).thenReturn(outboxProperties);
        when(repository.findById(eventId)).thenReturn(Optional.of(eventEntity));

        String longMsg = "E".repeat(1000);
        Exception ex = new RuntimeException(longMsg);
        outboxRelayService.markFailedOrRetry(eventId, ex);

        assertEquals(500, eventEntity.getLastError().length());
        verify(repository, times(1)).save(eventEntity);
    }

    @Test
    void givenMaxAttemptsReached_whenMarkFailedOrRetry_thenSetsStatusToFailed() {
        MemberProperties.OutboxProperties outboxProperties = new MemberProperties.OutboxProperties(10, Duration.ofSeconds(2), Duration.ofSeconds(30), 3, Duration.ofMinutes(5));
        when(properties.outbox()).thenReturn(outboxProperties);
        eventEntity.setAttempts(2); // Attempt 3 will hit maxAttempts = 3
        when(repository.findById(eventId)).thenReturn(Optional.of(eventEntity));

        outboxRelayService.markFailedOrRetry(eventId, new RuntimeException("Fatal error"));

        assertEquals(3, eventEntity.getAttempts());
        assertEquals(OutboxEventEntity.OutboxStatus.FAILED, eventEntity.getStatus());
        verify(repository, times(1)).save(eventEntity);
    }
}
