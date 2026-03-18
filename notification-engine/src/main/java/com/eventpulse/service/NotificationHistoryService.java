package com.eventpulse.service;

import com.eventpulse.entity.NotificationHistory;
import com.eventpulse.notification.NotificationStatusEvent;
import com.eventpulse.notification.PushNotification;
import com.eventpulse.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationHistoryService {

    private final NotificationHistoryRepository repository;

    public void create(PushNotification notification) {

        NotificationHistory history = new NotificationHistory();

        history.setNotificationId(notification.getNotificationId());
        history.setEventId(notification.getEventId());
        history.setUserId(notification.getUserId());

        history.setChannel("PUSH");

        history.setTitle(notification.getTitle());
        history.setBody(notification.getBody());

        history.setStatus("CREATED");

        history.setCreatedAt(System.currentTimeMillis());

        repository.save(history);
    }

    public void updateStatus(String notificationId, String status) {
        NotificationHistory history = repository.findByNotificationId(notificationId);
        if (history == null) {
            return;
        }
        history.setStatus(status);
        history.setUpdatedAt(System.currentTimeMillis());
        repository.save(history);
    }

    public void updateDeliveryStatus(NotificationStatusEvent event) {

        NotificationHistory history =
                repository.findByNotificationId(event.getNotificationId());

        if (history == null) {
            return;
        }

        history.setSentCount(
                history.getSentCount() + event.getSuccessCount()
        );

        history.setFailedCount(
                history.getFailedCount() + event.getFailureCount()
        );

        history.setStatus(resolveStatus(event));
        history.setUpdatedAt(System.currentTimeMillis());

        repository.save(history);
    }

    private String resolveStatus(NotificationStatusEvent event) {
        if (event.getStatus() != null && !event.getStatus().isBlank()) {
            return event.getStatus().toString();
        }

        if (event.getSuccessCount() > 0 && event.getFailureCount() > 0) {
            return "PARTIAL_FAILURE";
        }

        if (event.getFailureCount() > 0) {
            return "FAILED";
        }

        return "SENT";
    }
}
