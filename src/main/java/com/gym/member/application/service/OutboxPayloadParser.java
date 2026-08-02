package com.gym.member.application.service;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OutboxPayloadParser {

    private static final String EVENT_PACKAGE = "com.gym.proto.events.v1.";
    private static final JsonFormat.Parser PARSER = JsonFormat.parser().ignoringUnknownFields();
    private final Map<String, Method> builders = new ConcurrentHashMap<>();

    public Message parse(String eventType, String payload) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Payload event type cannot be null or blank");
        }
        try {
            Method builderMethod = builders.computeIfAbsent(eventType, this::findBuilder);
            Message.Builder builder = (Message.Builder) builderMethod.invoke(null);
            PARSER.merge(payload, builder);
            return builder.build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse outbox payload for eventType: " + eventType, e);
        }
    }

    private Method findBuilder(String eventType) {
        try {
            Class<?> clazz;
            if (eventType.contains(".")) {
                clazz = Class.forName(eventType);
            } else {
                clazz = Class.forName(EVENT_PACKAGE + eventType);
            }
            return clazz.getMethod("newBuilder");
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Unsupported outbox event type: " + eventType, e);
        }
    }
}
