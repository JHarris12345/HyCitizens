package com.electro.hycitizens.models;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CitizenMessage {
    private String message;
    @Nullable
    private String interactionTrigger;
    private float delaySeconds;
    private float chancePercent;

    public CitizenMessage(@Nonnull String message) {
        this(message, "BOTH", 0.0f, 100.0f);
    }

    public CitizenMessage(@Nonnull String message, @Nullable String interactionTrigger, float delaySeconds) {
        this(message, interactionTrigger, delaySeconds, 100.0f);
    }

    public CitizenMessage(@Nonnull String message, @Nullable String interactionTrigger, float delaySeconds, float chancePercent) {
        this.message = message;
        this.interactionTrigger = interactionTrigger;
        this.delaySeconds = delaySeconds;
        this.chancePercent = chancePercent;
    }

    public CitizenMessage() {
        this.message = "";
        this.interactionTrigger = "BOTH";
        this.delaySeconds = 0.0f;
        this.chancePercent = 100.0f;
    }

    @Nonnull
    public String getMessage() {
        return message;
    }

    public void setMessage(@Nonnull String message) {
        this.message = message;
    }

    @Nullable
    public String getInteractionTrigger() {
        return interactionTrigger;
    }

    public void setInteractionTrigger(@Nullable String interactionTrigger) {
        this.interactionTrigger = interactionTrigger;
    }

    public float getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(float delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    public float getChancePercent() {
        return chancePercent;
    }

    public void setChancePercent(float chancePercent) {
        this.chancePercent = chancePercent;
    }

    public boolean isTriggeredBy(@Nonnull String interactionSource) {
        String trigger = interactionTrigger != null ? interactionTrigger : "BOTH";
        return "BOTH".equals(trigger) || trigger.equals(interactionSource);
    }
}
