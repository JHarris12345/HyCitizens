package com.electro.hycitizens.events;

import com.electro.hycitizens.models.CitizenData;

import javax.annotation.Nonnull;

public class CitizenRemovedEvent {
    private final CitizenData citizen;

    public CitizenRemovedEvent(@Nonnull CitizenData citizen) {
        this.citizen = citizen;
    }

    @Nonnull
    public CitizenData getCitizen() {
        return citizen;
    }
}
