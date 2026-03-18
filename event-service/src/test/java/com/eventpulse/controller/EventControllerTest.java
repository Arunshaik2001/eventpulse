package com.eventpulse.controller;

import com.eventpulse.Event;
import com.eventpulse.dto.EventRequest;
import com.eventpulse.service.EventProducer;
import com.eventpulse.service.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.CONFLICT;

class EventControllerTest {

    private final EventProducer producer = mock(EventProducer.class);
    private final IdempotencyService idempotencyService = mock(IdempotencyService.class);
    private final EventController controller =
            new EventController(producer, idempotencyService, new ObjectMapper());

    @Test
    void shouldReuseClientSuppliedEventId() {
        EventRequest request = new EventRequest();
        request.setEventId("evt-123");
        request.setEventType("ORDER_CONFIRMED");
        request.setUserId("user-1");
        request.setPayload(Map.of("orderId", "A-1"));

        when(idempotencyService.isDuplicate("evt-123")).thenReturn(false);

        var response = controller.publish(request);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(producer).send(eventCaptor.capture());
        assertEquals(ACCEPTED, response.getStatusCode());
        assertEquals("evt-123", eventCaptor.getValue().getEventId().toString());
        assertEquals("{\"orderId\":\"A-1\"}", eventCaptor.getValue().getPayload().toString());
    }

    @Test
    void shouldGenerateEventIdWhenClientOmitsIt() {
        EventRequest request = new EventRequest();
        request.setEventType("ORDER_CONFIRMED");
        request.setUserId("user-1");
        request.setPayload(Map.of("orderId", "A-1"));

        when(idempotencyService.isDuplicate(anyString())).thenReturn(false);

        var response = controller.publish(request);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(producer).send(eventCaptor.capture());
        assertEquals(ACCEPTED, response.getStatusCode());
        assertNotNull(eventCaptor.getValue().getEventId());
    }

    @Test
    void shouldRejectDuplicateRequests() {
        EventRequest request = new EventRequest();
        request.setEventId("evt-123");
        request.setEventType("ORDER_CONFIRMED");
        request.setUserId("user-1");
        request.setPayload(Map.of("orderId", "A-1"));

        when(idempotencyService.isDuplicate("evt-123")).thenReturn(true);

        var response = controller.publish(request);

        verify(producer, never()).send(any());
        assertEquals(CONFLICT, response.getStatusCode());
        assertNull(response.getBody());
    }
}
