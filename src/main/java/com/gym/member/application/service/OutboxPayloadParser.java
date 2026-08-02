package com.gym.member.application.service;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class OutboxPayloadParser {

    private static final List<String> DEFAULT_EVENT_PACKAGES = List.of(
            "com.gym.proto.events.v1."
    );
    private static final JsonFormat.Parser PARSER = JsonFormat.parser().ignoringUnknownFields();

    private final Map<String, Method> builders = new ConcurrentHashMap<>();
    private final List<String> packageSearchPaths = new CopyOnWriteArrayList<>(DEFAULT_EVENT_PACKAGES);

    public void registerPackage(String packagePath) {
        if (packagePath != null && !packagePath.isBlank()) {
            String formatted = packagePath.endsWith(".") ? packagePath : packagePath + ".";
            if (!packageSearchPaths.contains(formatted)) {
                packageSearchPaths.add(formatted);
            }
        }
    }

    public void registerEventType(String eventType, Class<? extends Message> messageClass) {
        try {
            Method builderMethod = messageClass.getMethod("newBuilder");
            builders.put(eventType, builderMethod);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + messageClass.getName() + " does not have a static newBuilder method", e);
        }
    }

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
        if (eventType.contains(".")) {
            try {
                Class<?> clazz = Class.forName(eventType);
                return clazz.getMethod("newBuilder");
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("Unsupported outbox event type: " + eventType, e);
            }
        }

        for (String packagePath : packageSearchPaths) {
            try {
                Class<?> clazz = Class.forName(packagePath + eventType);
                return clazz.getMethod("newBuilder");
            } catch (ClassNotFoundException ignored) {
                // Try next package in search path
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Class in package " + packagePath + " missing newBuilder method: " + eventType, e);
            }
        }
        throw new IllegalArgumentException("Unsupported outbox event type: " + eventType + " across packages " + packageSearchPaths);
    }
}
