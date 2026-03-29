package com.eventpulse.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicsConfig {

    @Value("${app.kafka.topics.push-notifications}")
    private String pushNotificationsTopic;

    @Value("${app.kafka.topics.notification-status}")
    private String notificationStatusTopic;

    @Bean
    public NewTopic pushNotificationsTopic() {
        return new NewTopic(pushNotificationsTopic, 3, (short) 1);
    }

    @Bean
    public NewTopic notificationStatusTopic() {
        return new NewTopic(notificationStatusTopic, 3, (short) 1);
    }
}
