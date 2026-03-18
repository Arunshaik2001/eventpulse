package com.eventpulse.service;

import com.eventpulse.metrics.NotificationMetrics;
import com.eventpulse.notification.NotificationStatusEvent;
import com.eventpulse.notification.PushNotification;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {

    private final StringRedisTemplate redis;
    private final NotificationStatusService statusService;
    private final NotificationMetrics notificationMetrics;
    private final KafkaTemplate<String, NotificationStatusEvent> statusKafkaTemplate;
    @Value("${app.kafka.topics.notification-status}")
    private String notificationStatusTopic;

    private static final int MAX_BATCH = 500;

    public void send(PushNotification notification) {
        String userId = notification.getUserId();

        try {
            Set<String> tokenSet = redis.opsForSet().members("devices:" + userId);

            if (tokenSet == null || tokenSet.isEmpty()) {
                log.info("No device tokens found for user {}", userId);
                publishStatus(notification, 0, 0, "NO_TARGETS");
                return;
            }

            log.info("Preparing push notification notificationId={} eventId={} userId={} tokenCount={}",
                    notification.getNotificationId(),
                    notification.getEventId(),
                    notification.getUserId(),
                    tokenSet.size());

            statusService.markStatus(
                    notification.getEventId(),
                    notification.getUserId(),
                    notification.getNotificationId(),
                    "PUSH",
                    "PENDING"
            );

            notificationMetrics.pending();

            List<TokenEntry> batch = tokenSet.stream()
                    .map(token -> new TokenEntry(
                            token,
                            userId,
                            notification.getNotificationId(),
                            notification.getEventId()))
                    .toList();

            for (int start = 0; start < batch.size(); start += MAX_BATCH) {
                int end = Math.min(start + MAX_BATCH, batch.size());
                sendBatch(notification, batch.subList(start, end));
            }
        } catch (Exception e) {
            log.error("Push failed for event {}", notification.getEventId(), e);
            publishStatus(notification, 0, 1, "FAILED");
        }
    }

    private void sendBatch(PushNotification notification, List<TokenEntry> batch) throws FirebaseMessagingException {
        long startNanos = System.nanoTime();

        try {
            log.info("Sending FCM batch notificationId={} eventId={} batchSize={}",
                    notification.getNotificationId(),
                    notification.getEventId(),
                    batch.size());

            List<String> tokens = batch.stream().map(t -> t.token).toList();
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .putAllData(notification.getData())
                    .setNotification(
                            Notification.builder()
                                    .setTitle(notification.getTitle())
                                    .setBody(notification.getBody())
                                    .build()
                    )
                    .build();

            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            List<SendResponse> responses = response.getResponses();

            int success = 0;
            int failure = 0;
            for (int i = 0; i < responses.size(); i++) {
                SendResponse sendResponse = responses.get(i);

                if (sendResponse.isSuccessful()) {
                    success++;
                    continue;
                }

                failure++;

                FirebaseMessagingException exception = sendResponse.getException();
                String errorCode = exception != null && exception.getErrorCode() != null
                        ? exception.getErrorCode().name()
                        : "UNKNOWN";
                String token = batch.get(i).token;
                String batchUserId = batch.get(i).userId;

                log.warn("FCM failure token={} error={}", token, errorCode);

                if ("UNREGISTERED".equals(errorCode)
                        || "INVALID_ARGUMENT".equals(errorCode)
                        || "NOT_FOUND".equals(errorCode)) {
                    redis.opsForSet().remove("devices:" + batchUserId, token);
                    log.info("Removed invalid token {}", token);
                }
            }

            long createdAt = notification.getCreatedAt();
            if (createdAt > 0) {
                long e2eLatencyMs = System.currentTimeMillis() - createdAt;
                if (e2eLatencyMs >= 0) {
                    notificationMetrics.recordEndToEndLatency(e2eLatencyMs);
                }
            }

            publishStatus(notification, success, failure, resolveStatus(success, failure));
        } finally {
            long elapsedNanos = System.nanoTime() - startNanos;
            if (elapsedNanos >= 0) {
                notificationMetrics.recordLatency(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
            }
        }
    }

    private void publishStatus(PushNotification notification, int successCount, int failureCount, String status) {
        log.info("Publishing notification status notificationId={} eventId={} status={} successCount={} failureCount={}",
                notification.getNotificationId(),
                notification.getEventId(),
                status,
                successCount,
                failureCount);

        statusService.markStatus(
                notification.getEventId(),
                notification.getUserId(),
                notification.getNotificationId(),
                "PUSH",
                status
        );

        if ("FAILED".equals(status)) {
            notificationMetrics.failed();
        } else if ("NO_TARGETS".equals(status)) {
            notificationMetrics.failed();
        } else {
            notificationMetrics.sent();
        }

        NotificationStatusEvent event = NotificationStatusEvent.newBuilder()
                .setNotificationId(notification.getNotificationId())
                .setStatus(status)
                .setChannel("PUSH")
                .setSuccessCount(successCount)
                .setFailureCount(failureCount)
                .setEventId(notification.getEventId())
                .build();

        statusKafkaTemplate.send(notificationStatusTopic, event.getNotificationId(), event);
    }

    static String resolveStatus(int successCount, int failureCount) {
        if (successCount > 0 && failureCount > 0) {
            return "PARTIAL_FAILURE";
        }
        if (failureCount > 0) {
            return "FAILED";
        }
        return "SENT";
    }

    private static class TokenEntry {

        String token;
        String userId;
        String notificationId;
        String eventId;

        TokenEntry(String token, String userId, String notificationId, String eventId) {
            this.token = token;
            this.userId = userId;
            this.notificationId = notificationId;
            this.eventId = eventId;
        }
    }
}
