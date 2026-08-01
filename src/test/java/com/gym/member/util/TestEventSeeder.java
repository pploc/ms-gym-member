package com.gym.member.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.gym.common.kafka.message.EventEnvelope;
import com.gym.common.kafka.message.EventEnvelopeSerializer;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.events.v1.UserRegisteredEvent;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

public class TestEventSeeder {

    public static void main(String[] args) {
        String bootstrapServers = System.getProperty("kafka.bootstrap", "localhost:9092");
        System.out.println("Connecting to Kafka at: " + bootstrapServers);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(EventEnvelope.class, (com.fasterxml.jackson.databind.JsonSerializer) new EventEnvelopeSerializer());
        mapper.registerModule(module);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
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

            EventEnvelope<UserRegisteredEvent> regEnvelope = new EventEnvelope<>(
                    "UserRegisteredEvent",
                    testUserId,
                    registeredEvent,
                    Instant.now().toEpochMilli(),
                    UUID.randomUUID().toString(),
                    "ms-gym-identifier"
            );

            org.apache.kafka.common.header.Header typeHeader = new org.apache.kafka.common.header.internals.RecordHeader(
                    "__TypeId__",
                    "com.gym.common.kafka.message.EventEnvelope".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            ProducerRecord<String, String> regRecord = new ProducerRecord<>("identity.user.registered", testUserId, mapper.writeValueAsString(regEnvelope));
            regRecord.headers().add(typeHeader);
            producer.send(regRecord).get();
            System.out.println("Published identity.user.registered event for user_id: " + testUserId);

            String testPlanId = "44444444-4444-4444-4444-444444444444";

            // 2. Seed PaymentCompletedEvent -> payment.completed
            PaymentCompletedEvent paymentEvent = PaymentCompletedEvent.newBuilder()
                    .setPaymentId(UUID.randomUUID().toString())
                    .setUserId(testUserId)
                    .setType("MEMBERSHIP")
                    .setReferenceId(testPlanId) // planId
                    .setAmountVnd(500000)
                    .setProvider("MOMO")
                    .setGymId(testGymId)
                    .setTimestamp(Instant.now().toEpochMilli())
                    .build();

            EventEnvelope<PaymentCompletedEvent> payEnvelope = new EventEnvelope<>(
                    "PaymentCompletedEvent",
                    testUserId,
                    paymentEvent,
                    Instant.now().toEpochMilli(),
                    UUID.randomUUID().toString(),
                    "ms-gym-payment"
            );

            ProducerRecord<String, String> payRecord = new ProducerRecord<>("payment.completed", testUserId, mapper.writeValueAsString(payEnvelope));
            payRecord.headers().add(typeHeader);
            producer.send(payRecord).get();
            System.out.println("Published payment.completed event for user_id: " + testUserId);

            System.out.println("Test event seeding complete!");
        } catch (Exception e) {
            System.err.println("Failed to seed test events: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
