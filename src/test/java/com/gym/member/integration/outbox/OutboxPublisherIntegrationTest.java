package com.gym.member.integration.outbox;

import com.google.protobuf.util.JsonFormat;
import com.gym.member.adapter.out.persistence.entity.OutboxEventEntity;
import com.gym.member.adapter.out.persistence.repository.OutboxEventJpaRepository;
import com.gym.member.application.scheduler.OutboxPublisherScheduler;
import com.gym.proto.events.v1.MembershipActivatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OutboxPublisherIntegrationTest {

    @Autowired
    private OutboxPublisherScheduler outboxScheduler;

    @Autowired
    private OutboxEventJpaRepository outboxRepository;

    private UUID eventId;

    @BeforeEach
    void setUp() throws Exception {
        MembershipActivatedEvent proto = MembershipActivatedEvent.newBuilder()
                .setMemberId(UUID.randomUUID().toString())
                .setUserId(UUID.randomUUID().toString())
                .setGymId(UUID.randomUUID().toString())
                .setPlanType("MONTHLY")
                .setStartDate("2026-01-01")
                .setEndDate("2026-02-01")
                .setTimestamp(Instant.now().toEpochMilli())
                .build();

        String payloadJson = JsonFormat.printer().print(proto);

        OutboxEventEntity event = new OutboxEventEntity();
        event.setAggregateType("member");
        event.setAggregateId(UUID.randomUUID().toString());
        event.setEventType("MembershipActivatedEvent");
        event.setTopic("membership.activated");
        event.setPayload(payloadJson);
        event.setStatus(OutboxEventEntity.OutboxStatus.PENDING);
        event.setCreatedAt(Instant.now());
        event.setNextAttemptAt(Instant.now());

        OutboxEventEntity saved = outboxRepository.save(event);
        eventId = saved.getId();
    }

    @Test
    void givenPendingOutboxEvent_whenProcessOutboxEvents_thenUpdatesStatusToPublished() {
        // Given - Pending outbox event in DB

        // When
        outboxScheduler.processOutboxEvents();

        // Then
        OutboxEventEntity updated = outboxRepository.findById(eventId).orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo(OutboxEventEntity.OutboxStatus.PUBLISHED);
    }
}
