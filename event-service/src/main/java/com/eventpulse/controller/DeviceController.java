package com.eventpulse.controller;

import com.eventpulse.dto.DeviceRequest;
import com.eventpulse.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Void> saveDeviceToken(@Validated @RequestBody DeviceRequest request) {

        log.info("Registering device token for user {}", request.getUserId());

        boolean registered =
                deviceService.register(
                        request.getUserId(),
                        request.getDeviceToken()
                );

        if (registered) {
            return ResponseEntity.accepted().build();
        }

        return ResponseEntity.internalServerError().build();
    }
}