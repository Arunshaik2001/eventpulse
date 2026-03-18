package com.eventpulse.service;

import com.eventpulse.notification.template.NotificationTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TemplateService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    private final Map<String, CachedTemplate> cache = new ConcurrentHashMap<>();

    public TemplateService(StringRedisTemplate redisTemplate,
                           ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public NotificationTemplate getTemplate(String eventType) {
        CachedTemplate cached = cache.get(eventType);
        if (cached != null && System.currentTimeMillis() - cached.loadedAt < CACHE_TTL_MS) {
            return cached.template;
        }

        String key = "template:" + eventType;

        String json = redisTemplate.opsForValue().get(key);

        if (json == null || json.isBlank()) {
            throw new IllegalStateException("Template not found for eventType=" + eventType);
        }

        try {
            NotificationTemplate template =
                    objectMapper.readValue(json, NotificationTemplate.class);

            cache.put(eventType, new CachedTemplate(template));

            return template;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse template for eventType=" + eventType, e);
        }
    }

    private static class CachedTemplate {
        NotificationTemplate template;
        long loadedAt;

        CachedTemplate(NotificationTemplate template) {
            this.template = template;
            this.loadedAt = System.currentTimeMillis();
        }
    }
}
