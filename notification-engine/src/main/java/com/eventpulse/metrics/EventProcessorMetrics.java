package com.eventpulse.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class EventProcessorMetrics {

    private final Counter eventsProcessed;
    private final Counter retries;
    private final Counter dlq;
    private final Counter rateLimitedNotifications;
    private final Counter duplicateEvents;

    public EventProcessorMetrics(MeterRegistry registry) {

        eventsProcessed = Counter.builder("events_processed_total")
                .description("Events processed")
                .tag("service", "notification-engine")
                .register(registry);

        retries = Counter.builder("events_retry_total")
                .description("Event retries")
                .tag("service", "notification-engine")
                .register(registry);

        dlq = Counter.builder("events_dlq_total")
                .description("Events sent to DLQ")
                .tag("service", "notification-engine")
                .register(registry);

        rateLimitedNotifications = Counter.builder("events_rate_limited")
                .description("Events rate limited")
                .tag("service", "notification-engine")
                .register(registry);

        duplicateEvents = Counter.builder("events_duplicates")
                .description("Duplicate events ignored")
                .tag("service", "notification-engine")
                .register(registry);
    }

    public void processed() {
        eventsProcessed.increment();
    }

    public void retry() {
        retries.increment();
    }

    public void dlq() {
        dlq.increment();
    }

    public void rateLimited() {
        rateLimitedNotifications.increment();
    }

    public void duplicate() {
        duplicateEvents.increment();
    }
}
