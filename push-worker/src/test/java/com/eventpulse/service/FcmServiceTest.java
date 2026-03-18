package com.eventpulse.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FcmServiceTest {

    @Test
    void shouldResolveSentStatus() {
        assertEquals("SENT", FcmService.resolveStatus(2, 0));
    }

    @Test
    void shouldResolveFailedStatus() {
        assertEquals("FAILED", FcmService.resolveStatus(0, 2));
    }

    @Test
    void shouldResolvePartialFailureStatus() {
        assertEquals("PARTIAL_FAILURE", FcmService.resolveStatus(2, 1));
    }
}
