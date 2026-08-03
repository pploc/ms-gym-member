package com.gym.member.payment.adapter.in.kafka;

import com.gym.member.config.MemberProperties;
import com.gym.member.member.application.service.MemberEventProcessingService;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.events.v1.UserRegisteredEvent;
import com.gym.proto.events.v1.UserSuspendedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumerAdapter {

    private final MemberEventProcessingService eventProcessingService;
    private final MemberProperties properties;

    @KafkaListener(topics = "${gym.kafka.topics.user-registered:identity.user.registered}", groupId = "${spring.kafka.consumer.group-id:ms-gym-member-group}")
    public void handleUserRegistered(ConsumerRecord<String, UserRegisteredEvent> record, Acknowledgment ack) {
        KafkaEventMetadata metadata = KafkaEventMetadata.extractAndValidate(record, properties);
        log.info("Received user registered event, key: {}, eventId: {}", record.key(), metadata.getEventId());
        eventProcessingService.processUserRegistered(metadata.getEventId(), metadata.getEventType(), record.value());
        ack.acknowledge();
    }

    @KafkaListener(topics = "${gym.kafka.topics.payment-completed:payment.completed}", groupId = "${spring.kafka.consumer.group-id:ms-gym-member-group}")
    public void handlePaymentCompleted(ConsumerRecord<String, PaymentCompletedEvent> record, Acknowledgment ack) {
        KafkaEventMetadata metadata = KafkaEventMetadata.extractAndValidate(record, properties);
        log.info("Received payment.completed event, key: {}, eventId: {}", record.key(), metadata.getEventId());
        eventProcessingService.processPaymentCompleted(metadata.getEventId(), metadata.getEventType(), record.value(), record.key());
        ack.acknowledge();
    }

    @KafkaListener(topics = "${gym.kafka.topics.user-suspended:identity.user.suspended}", groupId = "${spring.kafka.consumer.group-id:ms-gym-member-group}")
    public void handleUserSuspended(ConsumerRecord<String, UserSuspendedEvent> record, Acknowledgment ack) {
        KafkaEventMetadata metadata = KafkaEventMetadata.extractAndValidate(record, properties);
        log.info("Received user suspended event, key: {}, eventId: {}", record.key(), metadata.getEventId());
        eventProcessingService.processUserSuspended(metadata.getEventId(), metadata.getEventType(), record.value());
        ack.acknowledge();
    }
}
