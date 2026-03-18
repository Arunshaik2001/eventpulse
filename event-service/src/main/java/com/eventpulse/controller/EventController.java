package com.eventpulse.controller;

import com.eventpulse.Event;
import com.eventpulse.dto.EventRequest;
import com.eventpulse.service.EventProducer;
import com.eventpulse.service.IdempotencyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventProducer producer;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Void> publish(@Valid @RequestBody EventRequest request) {
        String eventId = request.getEventId() == null || request.getEventId().isBlank()
                ? UUID.randomUUID().toString()
                : request.getEventId();

        if (idempotencyService.isDuplicate(eventId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        Event event = Event.newBuilder()
                .setEventId(eventId)
                .setEventType(request.getEventType())
                .setUserId(request.getUserId())
                .setTimestamp(System.currentTimeMillis())
                .setPayload(serializePayload(request))
                .build();

        producer.send(event);

        return ResponseEntity.accepted().build();
    }

    private String serializePayload(EventRequest request) {
        try {
            return objectMapper.writeValueAsString(request.getPayload());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid event payload", exception);
        }
    }
}
