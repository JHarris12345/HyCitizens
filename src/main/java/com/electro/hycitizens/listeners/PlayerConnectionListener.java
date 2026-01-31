package com.electro.hycitizens.listeners;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;

import java.util.List;
import java.util.UUID;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class PlayerConnectionListener {
    private final HyCitizensPlugin plugin;

    public PlayerConnectionListener(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    public void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        List<CitizenData> citizens = plugin.getCitizensManager().getAllCitizens();

        for (CitizenData citizen : citizens) {
            citizen.lastLookDirections.remove(event.getPlayerRef().getUuid());
        }
    }
}
