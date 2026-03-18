package com.eventpulse.notification.engine;

import com.eventpulse.Event;
import com.eventpulse.metrics.EventProcessorMetrics;
import com.eventpulse.notification.PushNotification;
import com.eventpulse.notification.template.NotificationTemplate;
import com.eventpulse.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class NotificationEngine {

    private final TemplateService templateService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final UserPreferenceService userPreferenceService;
    private final NotificationHistoryService notificationHistoryService;
    private final RateLimiterService rateLimiterService;
    private final EventProcessorMetrics metrics;
    private final String pushNotificationsTopic;

    public NotificationEngine(TemplateService templateService,
                              KafkaTemplate<String, Object> kafkaTemplate ,
                              ObjectMapper objectMapper,
                              UserPreferenceService userPreferenceService,
                              NotificationHistoryService notificationHistoryService,
                              RateLimiterService rateLimiterService,
                              EventProcessorMetrics metrics,
                              @Value("${app.kafka.topics.push-notifications}") String pushNotificationsTopic) {
        this.templateService = templateService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.userPreferenceService = userPreferenceService;
        this.notificationHistoryService = notificationHistoryService;
        this.rateLimiterService = rateLimiterService;
        this.metrics = metrics;
        this.pushNotificationsTopic = pushNotificationsTopic;
    }

    public void process(Event event) {

        String notificationId = UUID.randomUUID().toString();

        boolean allowed =
                rateLimiterService.allow(
                        event.getUserId(),
                        notificationId
                );

        if (!allowed) {
            log.warn("Rate limit exceeded user={}", event.getUserId());
            metrics.rateLimited();
            return;
        }

        NotificationTemplate template =
                templateService.getTemplate(event.getEventType());

        if(template.getChannels().contains("PUSH")) {

            Map<String, String> payload;

            try {
                payload = objectMapper.readValue(event.getPayload(), Map.class);
            } catch (Exception e) {
                throw new RuntimeException("Invalid payload", e);
            }

            if (!userPreferenceService.isPushEnabled(event.getUserId())) {
                return;
            }

            String title = TemplateRendererService.render(
                    template.getPush().getTitle(),
                    payload
            );

            String body = TemplateRendererService.render(
                    template.getPush().getBody(),
                    payload
            );

            PushNotification push = PushNotification.newBuilder()
                    .setNotificationId(notificationId)
                    .setEventId(event.getEventId())
                    .setUserId(event.getUserId())
                    .setTitle(title)
                    .setBody(body)
                    .setData(payload)
                    .setCreatedAt(event.getTimestamp())
                    .build();

            notificationHistoryService.create(push);

            kafkaTemplate.send(pushNotificationsTopic, push.getUserId(), push);

        }

    }

}
