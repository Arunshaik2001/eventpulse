package com.eventpulse.controller;

import com.eventpulse.dto.TemplateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/templates")
public class TemplateController {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TemplateController(StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public String createTemplate(@Valid @RequestBody TemplateRequest request) throws Exception {

        String key = "template:" + request.getEventType();

        String json = objectMapper.writeValueAsString(request);

        redisTemplate.opsForValue().set(key, json);

        String value = redisTemplate.opsForValue().get(key);
        System.out.println("Redis readback -> " + value);
        return "Template saved for " + request.getEventType();
    }
}
