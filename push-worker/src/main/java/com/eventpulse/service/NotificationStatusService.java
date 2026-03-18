package com.eventpulse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationStatusService {

    private final StringRedisTemplate redis;

    private static final String PREFIX = "notification:";

    public void markStatus(String eventId, String userId, String notificationId, String channel, String status) {
        String key = PREFIX + eventId;

        Map<String, String> data = new HashMap<>();
        data.put("eventId", eventId);
        data.put("userId", userId);
        data.put("notificationId", notificationId);
        data.put("channel", channel);
        data.put("status", status);
        data.put("timestamp", String.valueOf(System.currentTimeMillis()));

        redis.opsForHash().putAll(key, data);
        redis.expire(key, Duration.ofDays(7));
    }

    public void markFailed(String eventId) {
        String key = PREFIX + eventId;
        redis.opsForHash().put(key, "status", "FAILED");
    }
}
