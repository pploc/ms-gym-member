package com.gym.member.payment.adapter.in.kafka;

import com.google.protobuf.Message;
import com.gym.member.config.MemberProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;

@Slf4j
public record KafkaEventMetadata(
        String eventId,
        String eventType,
        String source,
        String timestamp
) {
    public static final String HEADER_EVENT_ID = "event-id";
    public static final String HEADER_EVENT_TYPE = "event-type";
    public static final String HEADER_SOURCE = "source";
    public static final String HEADER_TIMESTAMP = "timestamp";

    public static KafkaEventMetadata extractAndValidate(ConsumerRecord<String, ? extends Message> record, MemberProperties properties) {
        Headers headers = record.headers();
        Message payload = record.value();

        String rawEventType = getHeaderValue(headers, HEADER_EVENT_TYPE);
        String descriptorType = payload != null ? payload.getDescriptorForType().getFullName() : null;

        if (rawEventType != null && !rawEventType.isBlank() && descriptorType != null && !rawEventType.equals(descriptorType)) {
            throw new IllegalArgumentException("Header event-type '" + rawEventType + "' does not match payload descriptor '" + descriptorType + "'");
        }

        String eventType = (rawEventType != null && !rawEventType.isBlank()) ? rawEventType : descriptorType;
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Event type cannot be resolved from header or payload descriptor");
        }

        String rawEventId = getHeaderValue(headers, HEADER_EVENT_ID);
        String eventId;
        if (rawEventId != null && !rawEventId.isBlank()) {
            eventId = rawEventId;
        } else if (properties != null && properties.requireEventId()) {
            throw new IllegalArgumentException("Event record missing required event-id header");
        } else {
            eventId = "legacy:" + record.topic() + ":" + record.partition() + ":" + record.offset();
            log.warn("Missing event-id header for record at {}:{}:{}; fallback to: {}", record.topic(), record.partition(), record.offset(), eventId);
        }

        return new KafkaEventMetadata(
                eventId,
                eventType,
                getHeaderValue(headers, HEADER_SOURCE),
                getHeaderValue(headers, HEADER_TIMESTAMP)
        );
    }

    public static String getHeaderValue(Headers headers, String key) {
        if (headers == null) return null;
        Header header = headers.lastHeader(key);
        if (header == null || header.value() == null) return null;
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
