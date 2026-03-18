package com.eventpulse.service;

import java.util.Map;

public class TemplateRendererService {

    public static String render(String template, Map<String, String> payload) {

        if (template == null) {
            return "";
        }

        if (payload == null || payload.isEmpty()) {
            return template;
        }

        String result = template;

        for (Map.Entry<String, String> entry : payload.entrySet()) {

            if (entry.getValue() == null) {
                continue;
            }

            String key = "{{" + entry.getKey() + "}}";

            result = result.replace(key, entry.getValue());
        }

        return result;
    }
}
