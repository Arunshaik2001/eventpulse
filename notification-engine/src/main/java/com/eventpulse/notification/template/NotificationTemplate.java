package com.eventpulse.notification.template;

import lombok.Data;
import java.util.List;

@Data
public class NotificationTemplate {

    private List<String> channels;
    private PushTemplate push;

    public List<String> getChannels() {
        return channels;
    }

    public void setChannels(List<String> channels) {
        this.channels = channels;
    }

    public PushTemplate getPush() {
        return push;
    }

    public void setPush(PushTemplate push) {
        this.push = push;
    }
}