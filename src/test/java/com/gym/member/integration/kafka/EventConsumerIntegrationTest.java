package com.gym.member.integration.kafka;

import com.gym.member.location.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.location.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.location.domain.model.GymLocationStatus;
import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.payment.adapter.in.kafka.EventConsumerAdapter;
import com.gym.member.payment.adapter.in.kafka.KafkaEventMetadata;
import com.gym.member.shared.idempotency.repository.ProcessedEventJpaRepository;
import com.gym.proto.events.v1.UserRegisteredEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventConsumerIntegrationTest {

    @Autowired
    private EventConsumerAdapter consumerAdapter;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private GymLocationJpaRepository gymLocationRepository;

    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;

    private String gymId;
    private String userId;
    private String eventId;

    @BeforeEach
    void setUp() {
        gymId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        eventId = UUID.randomUUID().toString();

        GymLocationEntity location = new GymLocationEntity();
        location.setId(gymId);
        location.setChainId(UUID.randomUUID().toString());
        location.setName("Integration Gym");
        location.setAddress("Street 1");
        location.setCity("Hanoi");
        location.setStatus(GymLocationStatus.ACTIVE);
        gymLocationRepository.save(location);
    }

    @Test
    void givenUserRegisteredEvent_whenHandleUserRegistered_thenPersistsMemberAndIdempotencyRecord() {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder()
                .setUserId(userId)
                .setFullName("Integration User")
                .build();

        ConsumerRecord<String, UserRegisteredEvent> record = new ConsumerRecord<>(
                "identity.user.registered", 0, 0L, userId, payload
        );
        record.headers().add(new RecordHeader(KafkaEventMetadata.HEADER_EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(KafkaEventMetadata.HEADER_EVENT_TYPE, payload.getDescriptorForType().getFullName().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(KafkaEventMetadata.HEADER_SOURCE, "user-service".getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(KafkaEventMetadata.HEADER_TIMESTAMP, String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8)));

        Acknowledgment ack = mock(Acknowledgment.class);

        // When
        consumerAdapter.handleUserRegistered(record, ack);

        // Then
        MemberEntity member = memberRepository.findByUserId(userId).orElse(null);
        assertThat(member).isNotNull();
        assertThat(member.getFullName()).isEqualTo("Integration User");

        boolean processed = processedEventRepository.existsById(eventId);
        assertThat(processed).isTrue();
    }
}
