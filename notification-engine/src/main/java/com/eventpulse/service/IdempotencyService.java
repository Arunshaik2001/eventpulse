package com.eventpulse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    public boolean isProcessed(String eventId) {
        Boolean exists = redisTemplate.hasKey(eventId);
        return exists != null && exists;

    }

    public void markProcessed(String eventId) {
        redisTemplate.opsForValue().set(eventId, "1", Duration.ofDays(7));
    }

}
