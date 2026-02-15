package com.electro.hycitizens.models;

import javax.annotation.Nonnull;

public class CommandAction {
    private String command;
    private boolean runAsServer;
    private float delaySeconds;

    public CommandAction(@Nonnull String command, boolean runAsServer) {
        this(command, runAsServer, 0.0f);
    }

    public CommandAction(@Nonnull String command, boolean runAsServer, float delaySeconds) {
        this.command = command;
        this.runAsServer = runAsServer;
        this.delaySeconds = delaySeconds;
    }

    @Nonnull
    public String getCommand() {
        return command;
    }

    public void setCommand(@Nonnull String command) {
        this.command = command;
    }

    public boolean isRunAsServer() {
        return runAsServer;
    }

    public void setRunAsServer(boolean runAsServer) {
        this.runAsServer = runAsServer;
    }

    public float getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(float delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    @Nonnull
    public String getFormattedCommand() {
        return "/" + command;
    }
}