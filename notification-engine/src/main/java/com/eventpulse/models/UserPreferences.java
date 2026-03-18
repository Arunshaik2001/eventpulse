package com.eventpulse.models;

import lombok.Data;

@Data
public class UserPreferences {

    private Boolean PUSH = true;

    public Boolean getPUSH() {
        return PUSH;
    }

    public void setPUSH(Boolean PUSH) {
        this.PUSH = PUSH;
    }
}
