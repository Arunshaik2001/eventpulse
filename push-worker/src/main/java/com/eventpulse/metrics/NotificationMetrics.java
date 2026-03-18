package com.eventpulse.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class NotificationMetrics {

    private final Counter sentCounter;
    private final Counter pendingCounter;
    private final Counter failedCounter;
    private final Timer processingTimer;
    private final Timer endToEndLatency;

    public NotificationMetrics(MeterRegistry registry) {

        sentCounter = Counter.builder("notifications_sent_total")
                .description("Total notifications sent")
                .tag("channel", "push")
                .register(registry);

        pendingCounter = Counter.builder("notifications_pending_total")
                .description("Total notifications pending")
                .tag("channel", "push")
                .register(registry);

        failedCounter = Counter.builder("notifications_failed_total")
                .description("Total notifications failed")
                .tag("channel", "push")
                .register(registry);

        processingTimer = Timer.builder("notification_processing_latency_ms")
                .description("Notification processing latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .tag("channel", "push")
                .register(registry);

        endToEndLatency = Timer.builder("notification_e2e_latency_ms")
                .description("End to end notification latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .tag("channel", "push")
                .register(registry);
    }

    public void sent() {
        sentCounter.increment();
    }

    public void pending() {
        pendingCounter.increment();
    }

    public void failed() {
        failedCounter.increment();
    }

    public <T> T recordLatency(Supplier<T> supplier) {
        return processingTimer.record(supplier);
    }

    public void recordLatency(Runnable runnable) {
        processingTimer.record(runnable);
    }

    public void recordLatency(long latency, TimeUnit unit) {
        processingTimer.record(latency, unit);
    }

    public void recordEndToEndLatency(long latency) {
        endToEndLatency.record(latency, TimeUnit.MILLISECONDS);
    }
}
