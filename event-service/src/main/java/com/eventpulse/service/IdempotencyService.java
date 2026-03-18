package com.eventpulse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    @Value("${app.idempotency.ttl-seconds}")
    private long ttlSeconds;

    public boolean isDuplicate(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }

        String key = "event:" + eventId;

        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds));

        return Boolean.FALSE.equals(success);
    }
}
