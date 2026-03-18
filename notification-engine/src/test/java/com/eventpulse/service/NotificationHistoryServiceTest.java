package com.eventpulse.service;

import com.eventpulse.entity.NotificationHistory;
import com.eventpulse.notification.NotificationStatusEvent;
import com.eventpulse.repository.NotificationHistoryRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class NotificationHistoryServiceTest {

    private final NotificationHistoryRepository repository = mock(NotificationHistoryRepository.class);
    private final NotificationHistoryService service = new NotificationHistoryService(repository);

    @Test
    void shouldMarkPartialFailureWhenBothSuccessAndFailureAreReported() {
        NotificationHistory history = new NotificationHistory();
        history.setNotificationId("notif-1");
        history.setSentCount(1);
        history.setFailedCount(0);

        when(repository.findByNotificationId("notif-1")).thenReturn(history);

        NotificationStatusEvent event = NotificationStatusEvent.newBuilder()
                .setNotificationId("notif-1")
                .setStatus("PARTIAL_FAILURE")
                .setChannel("PUSH")
                .setSuccessCount(2)
                .setFailureCount(1)
                .setEventId("evt-1")
                .build();

        service.updateDeliveryStatus(event);

        assertEquals(3, history.getSentCount());
        assertEquals(1, history.getFailedCount());
        assertEquals("PARTIAL_FAILURE", history.getStatus());
        verify(repository).save(history);
    }
}
