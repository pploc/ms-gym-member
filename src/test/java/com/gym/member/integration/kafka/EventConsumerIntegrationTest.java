package com.gym.member.integration.kafka;

import com.gym.common.kafka.message.EventEnvelope;
import com.gym.member.adapter.in.kafka.EventConsumerAdapter;
import com.gym.member.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.adapter.out.persistence.repository.ProcessedEventJpaRepository;
import com.gym.proto.events.v1.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
        location.setStatus("ACTIVE");
        gymLocationRepository.save(location);
    }

    @Test
    void givenUserRegisteredEvent_whenHandleUserRegistered_thenPersistsMemberAndIdempotencyRecord() {
        // Given
        UserRegisteredEvent payload = UserRegisteredEvent.newBuilder()
                .setUserId(userId)
                .setFullName("Integration User")
                .setGymId(gymId)
                .build();

        EventEnvelope<UserRegisteredEvent> envelope = new EventEnvelope<>(
                "identity.user.registered", userId, payload, System.currentTimeMillis(), eventId, "user-service"
        );

        Acknowledgment ack = mock(Acknowledgment.class);

        // When
        consumerAdapter.handleUserRegistered(envelope, ack);

        // Then
        MemberEntity member = memberRepository.findByUserId(userId).orElse(null);
        assertThat(member).isNotNull();
        assertThat(member.getFullName()).isEqualTo("Integration User");
        assertThat(member.getGymId()).isEqualTo(gymId);

        boolean processed = processedEventRepository.existsById(eventId);
        assertThat(processed).isTrue();
    }
}
