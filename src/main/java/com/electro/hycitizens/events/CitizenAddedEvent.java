package com.electro.hycitizens.events;

import com.electro.hycitizens.models.CitizenData;

import javax.annotation.Nonnull;

public class CitizenAddedEvent {
    private final CitizenData citizen;

    public CitizenAddedEvent(@Nonnull CitizenData citizen) {
        this.citizen = citizen;
    }

    @Nonnull
    public CitizenData getCitizen() {
        return citizen;
    }
}
