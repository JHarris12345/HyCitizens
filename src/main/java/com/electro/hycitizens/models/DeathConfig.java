package com.electro.hycitizens.models;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class DeathConfig {
    private List<DeathDropItem> dropItems;
    private List<CommandAction> deathCommands;
    private List<CitizenMessage> deathMessages;
    private String commandSelectionMode;
    private String messageSelectionMode;
    private int dropCountMin;
    private int dropCountMax;
    private int commandCountMin;
    private int commandCountMax;
    private int messageCountMin;
    private int messageCountMax;

    public DeathConfig() {
        this.dropItems = new ArrayList<>();
        this.deathCommands = new ArrayList<>();
        this.deathMessages = new ArrayList<>();
        this.commandSelectionMode = "ALL";
        this.messageSelectionMode = "ALL";
        this.dropCountMin = 0;
        this.dropCountMax = 0;
        this.commandCountMin = 0;
        this.commandCountMax = 0;
        this.messageCountMin = 0;
        this.messageCountMax = 0;
    }

    @Nonnull
    public List<DeathDropItem> getDropItems() { return new ArrayList<>(dropItems); }
    public void setDropItems(@Nonnull List<DeathDropItem> dropItems) { this.dropItems = new ArrayList<>(dropItems); }

    @Nonnull
    public List<CommandAction> getDeathCommands() { return new ArrayList<>(deathCommands); }
    public void setDeathCommands(@Nonnull List<CommandAction> deathCommands) { this.deathCommands = new ArrayList<>(deathCommands); }

    @Nonnull
    public List<CitizenMessage> getDeathMessages() { return new ArrayList<>(deathMessages); }
    public void setDeathMessages(@Nonnull List<CitizenMessage> deathMessages) { this.deathMessages = new ArrayList<>(deathMessages); }

    @Nonnull
    public String getCommandSelectionMode() { return commandSelectionMode; }
    public void setCommandSelectionMode(@Nonnull String commandSelectionMode) { this.commandSelectionMode = commandSelectionMode; }

    @Nonnull
    public String getMessageSelectionMode() { return messageSelectionMode; }
    public void setMessageSelectionMode(@Nonnull String messageSelectionMode) { this.messageSelectionMode = messageSelectionMode; }

    public int getDropCountMin() { return dropCountMin; }
    public void setDropCountMin(int dropCountMin) { this.dropCountMin = dropCountMin; }

    public int getDropCountMax() { return dropCountMax; }
    public void setDropCountMax(int dropCountMax) { this.dropCountMax = dropCountMax; }

    public int getCommandCountMin() { return commandCountMin; }
    public void setCommandCountMin(int commandCountMin) { this.commandCountMin = commandCountMin; }

    public int getCommandCountMax() { return commandCountMax; }
    public void setCommandCountMax(int commandCountMax) { this.commandCountMax = commandCountMax; }

    public int getMessageCountMin() { return messageCountMin; }
    public void setMessageCountMin(int messageCountMin) { this.messageCountMin = messageCountMin; }

    public int getMessageCountMax() { return messageCountMax; }
    public void setMessageCountMax(int messageCountMax) { this.messageCountMax = messageCountMax; }
}
