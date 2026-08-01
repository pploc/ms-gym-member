package com.gym.member.util;

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
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, EventEnvelopeSerializer.class.getName());

        try (KafkaProducer<String, Object> producer = new KafkaProducer<>(props)) {
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

            producer.send(new ProducerRecord<>("identity.user.registered", testUserId, regEnvelope)).get();
            System.out.println("✅ Published identity.user.registered event for user_id: " + testUserId);

            // 2. Seed PaymentCompletedEvent -> payment.completed
            PaymentCompletedEvent paymentEvent = PaymentCompletedEvent.newBuilder()
                    .setPaymentId(UUID.randomUUID().toString())
                    .setUserId(testUserId)
                    .setType("MEMBERSHIP")
                    .setReferenceId(UUID.randomUUID().toString()) // planId
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

            producer.send(new ProducerRecord<>("payment.completed", testUserId, payEnvelope)).get();
            System.out.println("✅ Published payment.completed event for user_id: " + testUserId);

            System.out.println("🎉 Test event seeding complete!");
        } catch (Exception e) {
            System.err.println("❌ Failed to seed test events: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
