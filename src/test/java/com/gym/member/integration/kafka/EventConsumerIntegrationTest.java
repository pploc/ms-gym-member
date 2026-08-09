package com.gym.member.integration.kafka;

import com.gym.common.kafka.consumer.DecodedKafkaRecord;
import com.gym.common.kafka.consumer.RawKafkaHeader;
import com.gym.common.kafka.consumer.RawKafkaRecord;
import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.payment.adapter.in.kafka.EventConsumerAdapter;
import com.gym.member.payment.adapter.in.kafka.KafkaEventMetadata;
import com.gym.member.shared.idempotency.repository.ProcessedEventJpaRepository;
import com.gym.proto.events.v1.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventConsumerIntegrationTest {

    @Autowired
    private EventConsumerAdapter consumerAdapter;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;

    private String userId;
    private String eventId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().toString();
        eventId = UUID.randomUUID().toString();
    }

    @Test
    void givenUserRegisteredEvent_whenHandleUserRegistered_thenPersistsMemberAndIdempotencyRecord() {
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder()
                .setUserId(userId)
                .setFullName("Integration User")
                .build();

        RawKafkaRecord raw = new RawKafkaRecord(
                "identity.user.registered.v1",
                0,
                0L,
                userId.getBytes(StandardCharsets.UTF_8),
                payload.toByteArray(),
                java.util.List.of(
                        new RawKafkaHeader(KafkaEventMetadata.HEADER_EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8)),
                        new RawKafkaHeader(
                                KafkaEventMetadata.HEADER_EVENT_TYPE,
                                payload.getDescriptorForType().getFullName().getBytes(StandardCharsets.UTF_8)),
                        new RawKafkaHeader(
                                KafkaEventMetadata.HEADER_SOURCE,
                                "user-service".getBytes(StandardCharsets.UTF_8)),
                        new RawKafkaHeader(
                                KafkaEventMetadata.HEADER_TIMESTAMP,
                                String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8))
                )
        );

        consumerAdapter.handle(new DecodedKafkaRecord(raw, payload));

        MemberEntity member = memberRepository.findByUserId(userId).orElse(null);
        assertThat(member).isNotNull();
        assertThat(member.getFullName()).isEqualTo("Integration User");
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }
}
