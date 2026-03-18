package com.eventpulse.service;

import com.eventpulse.models.UserPreferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public boolean isPushEnabled(String userId) {

        String json = redis.opsForValue().get("prefs:" + userId);

        if (json == null) {
            return true;
        }

        try {

            UserPreferences prefs =
                    objectMapper.readValue(json, UserPreferences.class);

            return Boolean.TRUE.equals(prefs.getPUSH());

        } catch (Exception e) {

            return true; // fail open
        }
    }
}
