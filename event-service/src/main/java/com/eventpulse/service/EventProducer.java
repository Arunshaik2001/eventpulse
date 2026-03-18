package com.eventpulse.service;

import com.eventpulse.Event;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class EventProducer {

    private final KafkaTemplate<String, Event> kafkaTemplate;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final String rawEventsTopic;

    public EventProducer(KafkaTemplate<String, Event> kafkaTemplate,
                         MeterRegistry meterRegistry,
                         @Value("${app.kafka.topics.raw-events}") String rawEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.successCounter = meterRegistry.counter("events.produced.success");
        this.failureCounter = meterRegistry.counter("events.produced.failure");
        this.rawEventsTopic = rawEventsTopic;
    }

    public void send(Event event) {

        CompletableFuture<SendResult<String, Event>> future =
                kafkaTemplate.send(rawEventsTopic, event.getEventId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                failureCounter.increment();
                log.error("Failed to send eventId={} due to {}",
                        event.getEventId(), ex.getMessage(), ex);
            } else {
                successCounter.increment();
                log.info("Event sent successfully. eventId={}, partition={}, offset={}",
                        event.getEventId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
