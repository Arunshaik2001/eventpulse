package com.eventpulse.consumer;

import com.eventpulse.notification.NotificationStatusEvent;
import com.eventpulse.service.NotificationHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationStatusConsumer {

    private final NotificationHistoryService historyService;

    @KafkaListener(
            topics = "${app.kafka.topics.notification-status}",
            groupId = "notification-engine"
    )
    public void consume(NotificationStatusEvent event) {

        log.info("Received status event notificationId={} success={} failure={}",
                event.getNotificationId(),
                event.getSuccessCount(),
                event.getFailureCount());

        historyService.updateDeliveryStatus(event);
    }
}
