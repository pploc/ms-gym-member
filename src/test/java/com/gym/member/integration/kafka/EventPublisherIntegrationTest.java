package com.gym.member.integration.kafka;

import com.google.protobuf.Message;
import com.gym.common.kafka.producer.EventPublisher;
import com.gym.proto.events.v1.MembershipActivatedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class EventPublisherIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @MockitoBean
    private KafkaTemplate<String, Message> kafkaTemplate;

    @Test
    void givenValidEventPayload_whenPublish_thenSendsKafkaProducerRecordWithCanonicalHeadersAndProtobufPayload() {
        // Given
        String topic = "membership.activated.v1";
        String key = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        MembershipActivatedEvent event = MembershipActivatedEvent.newBuilder()
                .setMemberId(key)
                .setUserId(UUID.randomUUID().toString())
                .setGymId(UUID.randomUUID().toString())
                .setPlanType(com.gym.proto.common.v1.PlanType.PLAN_TYPE_MONTHLY)
                .setStartDate("2026-08-01")
                .setEndDate("2026-08-31")
                .setTimestamp(System.currentTimeMillis())
                .build();

        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        eventPublisher.publish(topic, key, event, eventId, java.util.Map.of());

        // Then
        ArgumentCaptor<ProducerRecord<String, Message>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());

        ProducerRecord<String, Message> capturedRecord = recordCaptor.getValue();
        assertThat(capturedRecord.topic()).isEqualTo(topic);
        assertThat(capturedRecord.key()).isEqualTo(key);

        assertThat(capturedRecord.value()).isEqualTo(event);

        assertThat(capturedRecord.headers().lastHeader("event-type")).isNotNull();
        assertThat(new String(capturedRecord.headers().lastHeader("event-type").value(), StandardCharsets.UTF_8))
                .isEqualTo(event.getDescriptorForType().getFullName());

        assertThat(capturedRecord.headers().lastHeader("event-id")).isNotNull();
        assertThat(new String(capturedRecord.headers().lastHeader("event-id").value(), StandardCharsets.UTF_8))
                .isEqualTo(eventId);

        assertThat(capturedRecord.headers().lastHeader("source")).isNotNull();
        assertThat(capturedRecord.headers().lastHeader("timestamp")).isNotNull();
    }
}
