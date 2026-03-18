package com.eventpulse.consumer;

import com.eventpulse.notification.PushNotification;
import com.eventpulse.service.FcmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PushNotificationConsumer {

    private final FcmService fcmService;

    public PushNotificationConsumer(FcmService fcmService) {
        this.fcmService = fcmService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.push-notifications}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(PushNotification notification) {
        log.info("Received push notification notificationId={} eventId={} userId={}",
                notification.getNotificationId(),
                notification.getEventId(),
                notification.getUserId());
        fcmService.send(notification);
    }
}
