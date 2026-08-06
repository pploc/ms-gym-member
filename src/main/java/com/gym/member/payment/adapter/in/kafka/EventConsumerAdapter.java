package com.gym.member.payment.adapter.in.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.gym.common.kafka.consumer.DecodedKafkaRecord;
import com.gym.common.kafka.consumer.KafkaRecordHandler;
import com.gym.common.kafka.consumer.PermanentKafkaException;
import com.gym.common.kafka.consumer.RawDeliveryCoordinatorFactory;
import com.gym.common.kafka.consumer.RawKafkaListenerAdapter;
import com.gym.member.config.MemberProperties;
import com.gym.member.member.application.service.MemberEventProcessingService;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.events.v1.UserRegisteredEvent;
import com.gym.proto.events.v1.UserSuspendedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class EventConsumerAdapter implements KafkaRecordHandler {

    private final MemberEventProcessingService eventProcessingService;
    private final MemberProperties properties;
    private final RawKafkaListenerAdapter rawListenerAdapter;

    public EventConsumerAdapter(
            MemberEventProcessingService eventProcessingService,
            MemberProperties properties,
            RawDeliveryCoordinatorFactory coordinatorFactory
    ) {
        this.eventProcessingService = eventProcessingService;
        this.properties = properties;
        this.rawListenerAdapter = new RawKafkaListenerAdapter(coordinatorFactory.forHandler(this));
    }

    @KafkaListener(
            topics = {
                    "${gym.kafka.topics.user-registered:identity.user.registered.v1}",
                    "${gym.kafka.topics.payment-completed:payment.completed.v1}",
                    "${gym.kafka.topics.user-suspended:identity.user.suspended.v1}"
            },
            groupId = "${spring.kafka.consumer.group-id:ms-gym-member-group}",
            containerFactory = "rawKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<byte[], byte[]> record, Acknowledgment acknowledgment) {
        rawListenerAdapter.deliver(record, acknowledgment);
    }

    @Override
    public void handle(DecodedKafkaRecord decoded) {
        Message payload = toConcrete(decoded.message());
        ConsumerRecord<String, Message> record = typedRecord(decoded, payload);
        KafkaEventMetadata metadata;
        try {
            metadata = KafkaEventMetadata.extractAndValidate(record, properties);
        } catch (IllegalArgumentException exception) {
            throw new PermanentKafkaException(exception.getMessage(), exception);
        }

        if (payload instanceof UserRegisteredEvent event) {
            log.info("Received user registered event, key: {}, eventId: {}", record.key(), metadata.eventId());
            eventProcessingService.processUserRegistered(metadata.eventId(), metadata.eventType(), event);
            return;
        }
        if (payload instanceof PaymentCompletedEvent event) {
            log.info("Received payment.completed event, key: {}, eventId: {}", record.key(), metadata.eventId());
            eventProcessingService.processPaymentCompleted(metadata.eventId(), metadata.eventType(), event, record.key());
            return;
        }
        if (payload instanceof UserSuspendedEvent event) {
            log.info("Received user suspended event, key: {}, eventId: {}", record.key(), metadata.eventId());
            eventProcessingService.processUserSuspended(metadata.eventId(), metadata.eventType(), event);
            return;
        }
        throw new PermanentKafkaException("Unsupported event type: " + payload.getDescriptorForType().getFullName());
    }

    /**
     * Confluent's generic KafkaProtobufDeserializer returns DynamicMessage unless a single
     * specific.protobuf.value.type is configured. Multi-topic listeners convert by descriptor.
     */
    static Message toConcrete(Message message) {
        if (message instanceof UserRegisteredEvent
                || message instanceof PaymentCompletedEvent
                || message instanceof UserSuspendedEvent) {
            return message;
        }
        String type = message.getDescriptorForType().getFullName();
        try {
            return switch (type) {
                case "events.v1.UserRegisteredEvent" -> UserRegisteredEvent.parseFrom(message.toByteString());
                case "events.v1.PaymentCompletedEvent" -> PaymentCompletedEvent.parseFrom(message.toByteString());
                case "events.v1.UserSuspendedEvent" -> UserSuspendedEvent.parseFrom(message.toByteString());
                default -> message;
            };
        } catch (InvalidProtocolBufferException exception) {
            throw new PermanentKafkaException(
                    "Failed to convert DynamicMessage to concrete type: " + type, exception);
        }
    }

    private static ConsumerRecord<String, Message> typedRecord(DecodedKafkaRecord decoded, Message payload) {
        var raw = decoded.raw();
        String key = raw.key() == null ? null : new String(raw.key(), StandardCharsets.UTF_8);
        ConsumerRecord<String, Message> record = new ConsumerRecord<>(
                raw.topic(), raw.partition(), raw.offset(), key, payload
        );
        raw.headers().forEach(header -> record.headers().add(new RecordHeader(header.key(), header.value())));
        return record;
    }
}
