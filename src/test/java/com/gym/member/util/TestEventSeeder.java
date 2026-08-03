package com.gym.member.util;

import com.google.protobuf.Message;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.events.v1.UserRegisteredEvent;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

public class TestEventSeeder {

    public static void main(String[] args) {
        String bootstrapServers = System.getProperty("kafka.bootstrap", "localhost:9092");
        String schemaRegistryUrl = System.getProperty("schema.registry.url", "http://localhost:8081");
        System.out.println("Connecting to Kafka at: " + bootstrapServers + ", Schema Registry: " + schemaRegistryUrl);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class.getName());
        props.put("schema.registry.url", schemaRegistryUrl);

        try (KafkaProducer<String, Message> producer = new KafkaProducer<>(props)) {
            String testUserId = "11111111-1111-1111-1111-111111111111";
            String testGymId = "22222222-2222-2222-2222-222222222222";

            // 1. Seed UserRegisteredEvent -> identity.user.registered
            UserRegisteredEvent registeredEvent = UserRegisteredEvent.newBuilder()
                    .setUserId(testUserId)
                    .setFullName("John Doe (Test Member)")
                    .setEmail("johndoe@example.com")
                    .setGymId(testGymId)
                    .setRole("MEMBER")
                    .setAuthProvider("LOCAL")
                    .setTimestamp(Instant.now().toEpochMilli())
                    .build();

            String regEventId = UUID.randomUUID().toString();
            ProducerRecord<String, Message> regRecord = new ProducerRecord<>("identity.user.registered", testUserId, registeredEvent);
            addCanonicalHeaders(regRecord, regEventId, registeredEvent.getDescriptorForType().getFullName(), "ms-gym-identifier");

            producer.send(regRecord).get();
            System.out.println("Published identity.user.registered event for user_id: " + testUserId);

            String testPlanId = "44444444-4444-4444-4444-444444444444";

            // 2. Seed PaymentCompletedEvent -> payment.completed
            PaymentCompletedEvent paymentEvent = PaymentCompletedEvent.newBuilder()
                    .setPaymentId(UUID.randomUUID().toString())
                    .setUserId(testUserId)
                    .setType("MEMBERSHIP")
                    .setReferenceId(testPlanId)
                    .setAmountVnd(500000)
                    .setProvider("MOMO")
                    .setGymId(testGymId)
                    .setTimestamp(Instant.now().toEpochMilli())
                    .build();

            String payEventId = UUID.randomUUID().toString();
            ProducerRecord<String, Message> payRecord = new ProducerRecord<>("payment.completed", testUserId, paymentEvent);
            addCanonicalHeaders(payRecord, payEventId, paymentEvent.getDescriptorForType().getFullName(), "ms-gym-payment");

            producer.send(payRecord).get();
            System.out.println("Published payment.completed event for user_id: " + testUserId);

            System.out.println("Test event seeding complete!");
        } catch (Exception e) {
            System.err.println("Failed to seed test events: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void addCanonicalHeaders(ProducerRecord<String, Message> record, String eventId, String eventType, String source) {
        long ts = Instant.now().toEpochMilli();
        record.headers().add(new RecordHeader("event-id", eventId.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("event-type", eventType.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("source", source.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("timestamp", String.valueOf(ts).getBytes(StandardCharsets.UTF_8)));
    }
}
