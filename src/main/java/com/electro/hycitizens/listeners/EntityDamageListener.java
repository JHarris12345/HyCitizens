package com.electro.hycitizens.listeners;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.events.CitizenInteractEvent;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.models.CommandAction;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EntityDamageListener extends DamageEventSystem {
    private final HyCitizensPlugin plugin;

    public EntityDamageListener(HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(int i, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Damage event) {
        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(i);
        UUIDComponent uuidComponent = (UUIDComponent)store.getComponent(targetRef, UUIDComponent.getComponentType());

        assert uuidComponent != null;
        NPCEntity npcEntity = store.getComponent(targetRef, NPCEntity.getComponentType());

        if (npcEntity == null)
            return;

        Damage.Source source = event.getSource();
        PlayerRef attackerPlayerRef = null;

        if (source instanceof Damage.ProjectileSource) { // This doesn't work for arrows. Using a workaround
            Damage.ProjectileSource projectileSource = (Damage.ProjectileSource) source;
            Ref<EntityStore> shooterRef = projectileSource.getRef();
            if (shooterRef != null) {
                attackerPlayerRef = store.getComponent(shooterRef, PlayerRef.getComponentType());
            }
        }
        else if (source instanceof Damage.EntitySource) {
            Damage.EntitySource entitySource = (Damage.EntitySource) source;
            Ref<EntityStore> attackerRef = entitySource.getRef();
            attackerPlayerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());
        }

        if (attackerPlayerRef == null)
            return;

        // Todo: It would be best to give the citizens a custom component. There may be compatibility issues if citizens already exist though
        for (CitizenData citizen : plugin.getCitizensManager().getAllCitizens()) {
            if (!citizen.getSpawnedUUID().equals(uuidComponent.getUuid()))
                continue;

            event.setCancelled(true);
            event.setAmount(0);

            Ref<EntityStore> attackerRef = attackerPlayerRef.getReference();
            if (attackerRef == null) {
                return;
            }

            Player player = attackerRef.getStore().getComponent(attackerRef, Player.getComponentType());
            if (player == null) {
                attackerPlayerRef.sendMessage(Message.raw("An error occurred").color(Color.RED));
                break;
            }

            if (!citizen.getRequiredPermission().isEmpty()) {
                if (!player.hasPermission(citizen.getRequiredPermission())) {
                    String permissionMessage = citizen.getNoPermissionMessage();

                    if (permissionMessage.isEmpty()) {
                        permissionMessage = "You do not have permissions";
                    }

                    attackerPlayerRef.sendMessage(Message.raw(permissionMessage).color(Color.RED));
                    break;
                }
            }

            CitizenInteractEvent interactEvent = new CitizenInteractEvent(citizen, attackerPlayerRef);
            plugin.getCitizensManager().fireCitizenInteractEvent(interactEvent);

            if (interactEvent.isCancelled())
                break;

            // Run commands
            for (CommandAction commandAction : citizen.getCommandActions()) {
                String command = commandAction.getCommand();

                // Replace {PlayerName} placeholders
                command = Pattern.compile("\\{PlayerName}", Pattern.CASE_INSENSITIVE)
                        .matcher(command)
                        .replaceAll(attackerPlayerRef.getUsername());

                // Check if this is a "send message" command
                if (command.startsWith("{SendMessage}")) {
                    String messageContent = command.substring("{SendMessage}".length()).trim();

                    Map<String, Color> namedColors = Map.ofEntries(
                            Map.entry("BLACK", Color.decode("#000000")),
                            Map.entry("WHITE", Color.decode("#FFFFFF")),
                            Map.entry("RED", Color.decode("#FF0000")),
                            Map.entry("GREEN", Color.decode("#00FF00")),
                            Map.entry("BLUE", Color.decode("#0000FF")),
                            Map.entry("YELLOW", Color.decode("#FFFF00")),
                            Map.entry("ORANGE", Color.decode("#FFA500")),
                            Map.entry("PINK", Color.decode("#FFC0CB")),
                            Map.entry("PURPLE", Color.decode("#800080")),
                            Map.entry("CYAN", Color.decode("#00FFFF")),
                            Map.entry("MAGENTA", Color.decode("#FF00FF")),
                            Map.entry("LIME", Color.decode("#00FF00")),
                            Map.entry("MAROON", Color.decode("#800000")),
                            Map.entry("NAVY", Color.decode("#000080")),
                            Map.entry("TEAL", Color.decode("#008080")),
                            Map.entry("OLIVE", Color.decode("#808000")),
                            Map.entry("SILVER", Color.decode("#C0C0C0")),
                            Map.entry("GRAY", Color.decode("#808080")),
                            Map.entry("GREY", Color.decode("#808080")),
                            Map.entry("BROWN", Color.decode("#A52A2A")),
                            Map.entry("GOLD", Color.decode("#FFD700")),
                            Map.entry("ORCHID", Color.decode("#DA70D6")),
                            Map.entry("SALMON", Color.decode("#FA8072")),
                            Map.entry("TURQUOISE", Color.decode("#40E0D0")),
                            Map.entry("VIOLET", Color.decode("#EE82EE")),
                            Map.entry("INDIGO", Color.decode("#4B0082")),
                            Map.entry("CORAL", Color.decode("#FF7F50")),
                            Map.entry("CRIMSON", Color.decode("#DC143C")),
                            Map.entry("KHAKI", Color.decode("#F0E68C")),
                            Map.entry("PLUM", Color.decode("#DDA0DD")),
                            Map.entry("CHOCOLATE", Color.decode("#D2691E")),
                            Map.entry("TAN", Color.decode("#D2B48C")),
                            Map.entry("LIGHTBLUE", Color.decode("#ADD8E6")),
                            Map.entry("LIGHTGREEN", Color.decode("#90EE90")),
                            Map.entry("LIGHTGRAY", Color.decode("#D3D3D3")),
                            Map.entry("LIGHTGREY", Color.decode("#D3D3D3")),
                            Map.entry("DARKRED", Color.decode("#8B0000")),
                            Map.entry("DARKGREEN", Color.decode("#006400")),
                            Map.entry("DARKBLUE", Color.decode("#00008B")),
                            Map.entry("DARKGRAY", Color.decode("#A9A9A9")),
                            Map.entry("DARKGREY", Color.decode("#A9A9A9")),
                            Map.entry("LIGHTPINK", Color.decode("#FFB6C1")),
                            Map.entry("LIGHTYELLOW", Color.decode("#FFFFE0")),
                            Map.entry("LIGHTCYAN", Color.decode("#E0FFFF")),
                            Map.entry("LIGHTMAGENTA", Color.decode("#FF77FF")),
                            Map.entry("ORANGERED", Color.decode("#FF4500")),
                            Map.entry("DEEPSKYBLUE", Color.decode("#00BFFF"))
                    );

                    // Check if it matches {COLOR}, {HEX}, or pain text
                    Pattern pattern = Pattern.compile("(\\{[A-Za-z]+})|(\\{#[0-9A-Fa-f]{6}})|([^\\{]+)");
                    Matcher matcher = pattern.matcher(messageContent);

                    Message msg = null;
                    Color currentColor = null;

                    while (matcher.find()) {
                        String namedColorToken = matcher.group(1);
                        String hexColorToken = matcher.group(2);
                        String textPart = matcher.group(3);

                        // {RED}, {GREEN}, etc
                        if (namedColorToken != null) {
                            String colorKey = namedColorToken.substring(1, namedColorToken.length() - 1).toUpperCase();
                            currentColor = namedColors.getOrDefault(colorKey, null);
                            continue;
                        }

                        // {#7CFC00}, etc
                        if (hexColorToken != null) {
                            String hex = hexColorToken.substring(1, hexColorToken.length() - 1); // remove { }
                            try {
                                currentColor = Color.decode(hex);
                            } catch (Exception ignored) {
                                currentColor = null;
                            }
                            continue;
                        }

                        // Text chunk
                        if (textPart != null && !textPart.isEmpty()) {
                            Message part = Message.raw(textPart);

                            if (currentColor != null) {
                                part = part.color(currentColor);
                            }

                            if (msg == null) {
                                msg = part;
                            } else {
                                msg = msg.insert(part);
                            }
                        }
                    }

                    if (msg != null) {
                        attackerPlayerRef.sendMessage(msg);
                    }
                } else {
                    // Regular command execution
                    if (commandAction.isRunAsServer()) {
                        CommandManager.get().handleCommand(ConsoleSender.INSTANCE, command);
                    } else {
                        CommandManager.get().handleCommand(player, command);
                    }
                }
            }

            break;
        }
    }

    @Nullable
    public Query<EntityStore> getQuery() {
        return Query.and(new Query[]{UUIDComponent.getComponentType()});
    }

    @Nullable
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }
}
