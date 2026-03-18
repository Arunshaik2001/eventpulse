package com.eventpulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class EventRequest {
    private String eventId;
    @NotBlank
    private String eventType;
    @NotBlank
    private String userId;
    @NotNull
    private Map<String, String> payload;
}
