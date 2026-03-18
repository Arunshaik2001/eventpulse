package com.eventpulse.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {
    @Value("${app.kafka.topics.raw-events}")
    private String rawEventsTopic;

    @Bean
    public NewTopic rawEventsTopic() {
        return new NewTopic(rawEventsTopic, 3, (short) 1);
    }
}
