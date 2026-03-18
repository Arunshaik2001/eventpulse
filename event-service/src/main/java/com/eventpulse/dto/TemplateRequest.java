package com.eventpulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public class TemplateRequest {

    @NotBlank
    private String eventType;

    @NotEmpty
    private List<String> channels;

    @NotNull
    private Map<String, String> push;

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public List<String> getChannels() {
        return channels;
    }

    public void setChannels(List<String> channels) {
        this.channels = channels;
    }

    public Map<String, String> getPush() {
        return push;
    }

    public void setPush(Map<String, String> push) {
        this.push = push;
    }
}
