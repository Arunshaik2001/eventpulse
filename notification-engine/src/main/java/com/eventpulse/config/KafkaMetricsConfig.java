package com.eventpulse.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;

import jakarta.annotation.PostConstruct;

@Configuration
public class KafkaMetricsConfig {

    private final ConsumerFactory<?, ?> consumerFactory;
    private final MeterRegistry meterRegistry;

    public KafkaMetricsConfig(ConsumerFactory<?, ?> consumerFactory, MeterRegistry meterRegistry) {
        this.consumerFactory = consumerFactory;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void bindMetrics() {
        KafkaClientMetrics metrics = new KafkaClientMetrics(consumerFactory.createConsumer());
        metrics.bindTo(meterRegistry);
    }
}