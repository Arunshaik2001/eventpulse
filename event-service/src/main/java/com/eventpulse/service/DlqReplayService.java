package com.eventpulse.service;

import com.eventpulse.Event;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DlqReplayService {

    private final KafkaTemplate<String, Event> kafkaTemplate;
    @Value("${app.kafka.topics.raw-events}")
    private String rawEventsTopic;

    public void replay(Event event) {

        ProducerRecord<String, Event> record =
                new ProducerRecord<>(rawEventsTopic, event.getEventId(), event);

        record.headers().add("x-replay", "true".getBytes());

        kafkaTemplate.send(record);

    }

}
