package com.eventpulse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redis;

    private static final int LIMIT = 5;
    private static final long WINDOW = 3600_000; // 1 hour

    public boolean allow(String userId, String notificationId) {

        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW;

        String key = "rate:user:" + userId;

        ZSetOperations<String, String> zset = redis.opsForZSet();

        zset.removeRangeByScore(key, 0, windowStart);

        Long count = zset.zCard(key);

        if (count != null && count >= LIMIT) {
            return false;
        }

        zset.add(key, notificationId, now);

        redis.expire(key, Duration.ofHours(1));

        return true;
    }
}