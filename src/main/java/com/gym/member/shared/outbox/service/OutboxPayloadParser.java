package com.gym.member.shared.outbox.service;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.gym.proto.events.v1.MembershipActivatedEvent;
import com.gym.proto.events.v1.MembershipExpiredEvent;
import com.gym.proto.events.v1.MembershipExpiringSoonEvent;
import com.gym.proto.events.v1.MembershipPausedEvent;
import com.gym.proto.events.v1.MembershipResumedEvent;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

@Component
public class OutboxPayloadParser {

    private static final List<String> DEFAULT_EVENT_PACKAGES = List.of(
            "com.gym.proto.events.v1."
    );
    private static final JsonFormat.Parser PARSER = JsonFormat.parser().ignoringUnknownFields();

    private final Map<String, Supplier<Message.Builder>> builderSuppliers = new ConcurrentHashMap<>();
    private final List<String> packageSearchPaths = new CopyOnWriteArrayList<>(DEFAULT_EVENT_PACKAGES);

    public OutboxPayloadParser() {
        registerSupplier("MembershipActivatedEvent", MembershipActivatedEvent::newBuilder);
        registerSupplier("MembershipPausedEvent", MembershipPausedEvent::newBuilder);
        registerSupplier("MembershipResumedEvent", MembershipResumedEvent::newBuilder);
        registerSupplier("MembershipExpiringSoonEvent", MembershipExpiringSoonEvent::newBuilder);
        registerSupplier("MembershipExpiredEvent", MembershipExpiredEvent::newBuilder);
    }

    public void registerSupplier(String eventType, Supplier<Message.Builder> builderSupplier) {
        if (eventType != null && builderSupplier != null) {
            builderSuppliers.put(eventType, builderSupplier);
        }
    }

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
            builderSuppliers.put(eventType, () -> invokeNewBuilder(builderMethod));
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + messageClass.getName() + " does not have a static newBuilder method", e);
        }
    }

    public Message parse(String eventType, String payload) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Payload event type cannot be null or blank");
        }
        try {
            Supplier<Message.Builder> supplier = builderSuppliers.computeIfAbsent(eventType, this::findBuilderSupplier);
            Message.Builder builder = supplier.get();
            PARSER.merge(payload, builder);
            return builder.build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse outbox payload for eventType: " + eventType, e);
        }
    }

    private Supplier<Message.Builder> findBuilderSupplier(String eventType) {
        if (eventType.contains(".")) {
            try {
                Class<?> clazz = Class.forName(eventType);
                Method method = clazz.getMethod("newBuilder");
                return () -> invokeNewBuilder(method);
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("Unsupported outbox event type: " + eventType, e);
            }
        }

        for (String packagePath : packageSearchPaths) {
            try {
                Class<?> clazz = Class.forName(packagePath + eventType);
                Method method = clazz.getMethod("newBuilder");
                return () -> invokeNewBuilder(method);
            } catch (ClassNotFoundException ignored) {
                // Try next package
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Class in package " + packagePath + " missing newBuilder method: " + eventType, e);
            }
        }
        throw new IllegalArgumentException("Unsupported outbox event type: " + eventType + " across packages " + packageSearchPaths);
    }

    private static Message.Builder invokeNewBuilder(Method method) {
        try {
            return (Message.Builder) method.invoke(null);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to invoke newBuilder method", e);
        }
    }
}
