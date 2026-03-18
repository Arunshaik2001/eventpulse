package com.eventpulse.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {

    @Autowired
    private StringRedisTemplate redis;

    public boolean register(String userId, String token) {

        redis.opsForSet().add(
                "devices:" + userId,
                token
        );

        System.out.println("Set successfully");
        return true;
    }
}
