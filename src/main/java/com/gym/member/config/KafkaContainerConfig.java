package com.gym.member.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

@Configuration
public class KafkaContainerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<byte[], byte[]> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactory<byte[], byte[]> rawKafkaListenerContainerFactory
    ) {
        return rawKafkaListenerContainerFactory;
    }
}
