package com.gym.member.integration.kafka;

import com.gym.common.kafka.message.EventEnvelope;
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
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void givenValidEventPayload_whenPublish_thenSendsKafkaProducerRecordWithHeadersAndEnvelope() {
        // Given
        String topic = "membership.activated";
        String key = UUID.randomUUID().toString();
        MembershipActivatedEvent event = MembershipActivatedEvent.newBuilder()
                .setMemberId(key)
                .setStartDate("2026-08-01")
                .setEndDate("2026-08-31")
                .build();

        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        eventPublisher.publish(topic, key, event);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());

        ProducerRecord<String, Object> capturedRecord = recordCaptor.getValue();
        assertThat(capturedRecord.topic()).isEqualTo(topic);
        assertThat(capturedRecord.key()).isEqualTo(key);

        EventEnvelope<?> envelope = (EventEnvelope<?>) capturedRecord.value();
        assertThat(envelope).isNotNull();
        assertThat(envelope.eventType()).isEqualTo("MembershipActivatedEvent");
        assertThat(envelope.key()).isEqualTo(key);
        assertThat(envelope.payload()).isEqualTo(event);

        assertThat(capturedRecord.headers().lastHeader("x-event-type")).isNotNull();
        assertThat(capturedRecord.headers().lastHeader("x-source")).isNotNull();
    }
}
