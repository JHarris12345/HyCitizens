package com.electro.hycitizens.models;

import javax.annotation.Nonnull;

public class DeathDropItem {
    private String itemId;
    private int quantity;
    private float chancePercent;

    public DeathDropItem() {
        this.itemId = "";
        this.quantity = 1;
        this.chancePercent = 100.0f;
    }

    public DeathDropItem(@Nonnull String itemId, int quantity) {
        this(itemId, quantity, 100.0f);
    }

    public DeathDropItem(@Nonnull String itemId, int quantity, float chancePercent) {
        this.itemId = itemId;
        this.quantity = quantity;
        this.chancePercent = chancePercent;
    }

    @Nonnull
    public String getItemId() { return itemId; }
    public void setItemId(@Nonnull String itemId) { this.itemId = itemId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public float getChancePercent() { return chancePercent; }
    public void setChancePercent(float chancePercent) { this.chancePercent = chancePercent; }
}
