package com.gym.member.unit.outbox;

import com.gym.member.shared.outbox.entity.OutboxEventEntity;
import com.gym.member.shared.outbox.repository.OutboxEventJpaRepository;
import com.gym.member.shared.outbox.service.OutboxEventWriter;
import com.gym.proto.events.v1.MembershipActivatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventWriterUnitTest {

    @Mock
    private OutboxEventJpaRepository repository;

    private Clock clock;
    private OutboxEventWriter writer;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneId.of("UTC"));
        writer = new OutboxEventWriter(repository, clock);
    }

    @Test
    void givenValidPayload_whenWrite_thenSavesEntityAndReturnsUUID() {
        MembershipActivatedEvent event = MembershipActivatedEvent.newBuilder()
                .setMemberId("mem-1")
                .setUserId("user-1")
                .setGymId("gym-1")
                .setPlanType(com.gym.proto.common.v1.PlanType.PLAN_TYPE_MONTHLY)
                .setStartDate("2026-08-01")
                .setEndDate("2026-09-01")
                .build();

        UUID eventId = writer.write("member", "mem-1", "membership.activated.v1", event);

        assertNotNull(eventId);
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());

        OutboxEventEntity saved = captor.getValue();
        assertEquals(eventId, saved.getId());
        assertEquals("member", saved.getAggregateType());
        assertEquals("mem-1", saved.getAggregateId());
        assertEquals("MembershipActivatedEvent", saved.getEventType());
        assertEquals("events.v1.MembershipActivatedEvent", saved.getPayloadType());
        assertEquals("membership.activated.v1", saved.getTopic());
        assertEquals(OutboxEventEntity.OutboxStatus.PENDING, saved.getStatus());
        assertEquals(Instant.now(clock), saved.getCreatedAt());
    }

    @Test
    void givenRepositoryFailure_whenWrite_thenThrowsIllegalStateException() {
        doThrow(new RuntimeException("DB error")).when(repository).save(any());

        MembershipActivatedEvent event = MembershipActivatedEvent.newBuilder().setMemberId("mem-1").build();

        assertThrows(IllegalStateException.class, () -> writer.write("member", "mem-1", "topic", event));
    }
}
