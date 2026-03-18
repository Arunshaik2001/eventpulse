package com.eventpulse.consumer;

import com.eventpulse.Event;
import com.eventpulse.metrics.EventProcessorMetrics;
import com.eventpulse.notification.engine.NotificationEngine;
import com.eventpulse.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventConsumer {

    private final IdempotencyService idempotencyService;
    private final NotificationEngine notificationEngine;
    private final EventProcessorMetrics metrics;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(
                    delay = 1000,
                    multiplier = 6
            ),
            dltTopicSuffix = "-dlq",
            concurrency = "6"
    )
    @KafkaListener(
            topics = "${app.kafka.topics.raw-events}"
    )
    public void consume(
            Event event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Processing event {} from topic {} offset {}", event.getEventId(), topic, offset);
        if (idempotencyService.isProcessed(event.getEventId())) {
            log.info("Duplicate event ignored {}", event.getEventId());
            metrics.duplicate();
            return;
        }
        notificationEngine.process(event);
        idempotencyService.markProcessed(event.getEventId());
        metrics.processed();
    }

    @DltHandler
    public void dlt(Event event) {
        log.error("Event sent to DLQ: {}", event.getEventId());
        metrics.dlq();
    }

}
