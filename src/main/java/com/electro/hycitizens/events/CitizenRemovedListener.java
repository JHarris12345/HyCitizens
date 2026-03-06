package com.electro.hycitizens.events;

@FunctionalInterface
public interface CitizenRemovedListener {
    void onCitizenRemoved(CitizenRemovedEvent event);
}
