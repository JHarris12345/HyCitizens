package com.electro.hycitizens.events;

@FunctionalInterface
public interface CitizenAddedListener {
    void onCitizenAdded(CitizenAddedEvent event);
}
