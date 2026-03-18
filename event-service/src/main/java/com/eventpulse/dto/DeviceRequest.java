package com.eventpulse.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceRequest {
    @NotBlank
    private String userId;
    @NotBlank
    private String deviceToken;
}
