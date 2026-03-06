package com.electro.hycitizens.listeners;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.events.CitizenDeathEvent;
import com.electro.hycitizens.interactions.CitizenInteraction;
import com.electro.hycitizens.models.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.awt.Color;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class EntityDamageListener extends DamageEventSystem {
    private final HyCitizensPlugin plugin;

    public EntityDamageListener(HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(int i, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Damage event) {
        try {
            Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(i);
            UUIDComponent uuidComponent = store.getComponent(targetRef, UUIDComponent.getComponentType());
            if (uuidComponent == null) {
                return;
            }

            List<CitizenData> citizens = HyCitizensPlugin.get().getCitizensManager().getAllCitizens();
            Damage.Source source = event.getSource();
            Ref<EntityStore> attackerEntityRef = getAttackerEntityRef(source);
            UUID attackerUuid = getEntityUuid(attackerEntityRef);

            PlayerRef attackerPlayerRef = null;
            if (attackerEntityRef != null && attackerEntityRef.isValid()) {
                attackerPlayerRef = attackerEntityRef.getStore().getComponent(attackerEntityRef, PlayerRef.getComponentType());
            }

            CitizenData attackerCitizen = findCitizenByEntity(citizens, attackerEntityRef, attackerUuid);
            if (attackerCitizen != null && attackerCitizen.isOverrideDamage() && attackerCitizen.getDamageAmount() >= 0) {
                event.setAmount(attackerCitizen.getDamageAmount());
            }

            CitizenData targetCitizen = findCitizenByEntity(citizens, targetRef, uuidComponent.getUuid());
            if (targetCitizen == null) {
                return;
            }

            // Passive citizens always cancel damage - they never enter combat
            boolean cancelDamage = !targetCitizen.isTakesDamage() || "PASSIVE".equals(targetCitizen.getAttitude());

            // Trigger ON_ATTACK animations regardless of damage setting
            HyCitizensPlugin.get().getCitizensManager().triggerAnimations(targetCitizen, "ON_ATTACK");

//            CitizenInteraction.handleInteraction(targetCitizen, attackerPlayerRef); // Handled by new interaction system

            if (cancelDamage) {
                // This is now handled by the Invulnerable component, but we are keeping it for backwards compatibility
                Invulnerable invulnerable = store.getComponent(targetRef, Invulnerable.getComponentType());

                if (invulnerable == null) {
                    event.setCancelled(true);
                    event.setAmount(0);
                    World world = Universe.get().getWorld(targetCitizen.getWorldUUID());
                    // Todo: This does not work
//                if (world != null) {
//                    // Prevent knockback
//                    world.execute(() -> {
//                        store.removeComponentIfExists(targetRef, KnockbackComponent.getComponentType());
//                    });
//                }
                    // Temporary solution to knockback
                    TransformComponent transformComponent = store.getComponent(targetRef, TransformComponent.getComponentType());
                    if (transformComponent != null && world != null) {
                        Vector3d lockedPosition = new Vector3d(transformComponent.getPosition());

                        ScheduledFuture<?> lockTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
                            if (!targetRef.isValid()) {
                                return;
                            }

                            Vector3d currentPosition = transformComponent.getPosition();
                            if (!currentPosition.equals(lockedPosition)) {
                                transformComponent.setPosition(lockedPosition);
                            }
                        }, 0, 20, TimeUnit.MILLISECONDS);

                        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                            lockTask.cancel(false);
                        }, 2000, TimeUnit.MILLISECONDS);
                    }
                }
                return;
            }

            targetCitizen.setLastDamageTakenAt(System.currentTimeMillis());

            // Check if the citizen will die from this damage
            EntityStatMap statMap = store.getComponent(targetRef, EntityStatsModule.get().getEntityStatMapComponentType());
            if (statMap == null) {
                return;
            }

            EntityStatValue healthValue = statMap.get(DefaultEntityStatTypes.getHealth());
            if (healthValue == null) {
                return;
            }

            float currentHealth = healthValue.get();
            float damageAmount = event.getAmount();

            if (currentHealth - damageAmount <= 0 && !targetCitizen.isAwaitingRespawn()) {
                long now = System.currentTimeMillis();

                // Fire death event
                CitizenDeathEvent deathEvent = new CitizenDeathEvent(targetCitizen, attackerPlayerRef);
                plugin.getCitizensManager().fireCitizenDeathEvent(deathEvent);

                if (deathEvent.isCancelled()) {
                    event.setCancelled(true);
                    event.setAmount(0);
                    return;
                }

                // Handle death config (drops, commands, messages)
                DeathConfig dc = targetCitizen.getDeathConfig();
                handleDeathCommands(targetCitizen, dc, attackerPlayerRef);
                handleDeathMessages(targetCitizen, dc, attackerPlayerRef);

                TransformComponent npcTransformComponent = store.getComponent(targetRef, TransformComponent.getComponentType());
                if (npcTransformComponent != null) {
                    handleDeathDrops(targetCitizen, dc, npcTransformComponent.getPosition());
                }

                targetCitizen.setLastDeathTime(now);

                if (plugin.getCitizensManager().getPatrolManager() != null) {
                    plugin.getCitizensManager().getPatrolManager().onCitizenDespawned(targetCitizen.getId());
                }
                plugin.getCitizensManager().despawnCitizenHologram(targetCitizen);
                plugin.getCitizensManager().clearCitizenEntityBinding(targetCitizen);

                // Mark for respawn
                if (targetCitizen.isRespawnOnDeath()) {
                    targetCitizen.setAwaitingRespawn(true);

                    HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                        World world = Universe.get().getWorld(targetCitizen.getWorldUUID());
                        if (world == null)
                            return;

                        targetCitizen.setAwaitingRespawn(false);
                        world.execute(() -> {
                            plugin.getCitizensManager().spawnCitizen(targetCitizen, true);
                        });
                    }, (long)(targetCitizen.getRespawnDelaySeconds() * 1000), TimeUnit.MILLISECONDS);
                }
            }

        } catch (Exception e) {
            getLogger().atWarning().log("[HyCitizens] Error in damage handler: " + e.getMessage());
        }
    }

    private static final Random RANDOM = new Random();
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("\\{PlayerName}", Pattern.CASE_INSENSITIVE);
    private static final Pattern CITIZEN_NAME_PATTERN = Pattern.compile("\\{CitizenName}", Pattern.CASE_INSENSITIVE);
    private static final Pattern NPC_X_PATTERN = Pattern.compile("\\{NpcX}", Pattern.CASE_INSENSITIVE);
    private static final Pattern NPC_Y_PATTERN = Pattern.compile("\\{NpcY}", Pattern.CASE_INSENSITIVE);
    private static final Pattern NPC_Z_PATTERN = Pattern.compile("\\{NpcZ}", Pattern.CASE_INSENSITIVE);
    private static final UUID NON_PLAYER_CONTEXT_UUID = new UUID(0L, 0L);

    @Nullable
    private Ref<EntityStore> getAttackerEntityRef(@Nonnull Damage.Source source) {
        if (source instanceof Damage.ProjectileSource) {
            return ((Damage.ProjectileSource) source).getRef();
        }

        if (source instanceof Damage.EntitySource) {
            return ((Damage.EntitySource) source).getRef();
        }

        return null;
    }

    @Nullable
    private UUID getEntityUuid(@Nullable Ref<EntityStore> entityRef) {
        if (entityRef == null || !entityRef.isValid()) {
            return null;
        }

        UUIDComponent uuidComponent = entityRef.getStore().getComponent(entityRef, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return null;
        }
        return uuidComponent.getUuid();
    }

    @Nullable
    private CitizenData findCitizenByEntity(@Nonnull List<CitizenData> citizens,
                                            @Nullable Ref<EntityStore> entityRef,
                                            @Nullable UUID entityUuid) {
        for (CitizenData citizen : citizens) {
            if (entityRef != null && entityRef.isValid()
                    && citizen.getNpcRef() != null
                    && citizen.getNpcRef().isValid()
                    && citizen.getNpcRef().equals(entityRef)) {
                return citizen;
            }

            if (entityUuid != null && entityUuid.equals(citizen.getSpawnedUUID())) {
                return citizen;
            }
        }

        return null;
    }

    private void handleDeathDrops(@Nonnull CitizenData citizen, @Nonnull DeathConfig dc, Vector3d position) {
        List<DeathDropItem> drops = dc.getDropItems();
        if (drops.isEmpty()) {
            return;
        }

        List<DeathDropItem> eligible = drops.stream()
                .filter(drop -> drop.getItemId() != null && !drop.getItemId().isEmpty())
                .filter(drop -> RANDOM.nextFloat() * 100.0f <= drop.getChancePercent())
                .collect(java.util.stream.Collectors.toList());

        if (eligible.isEmpty()) {
            return;
        }

        int desiredCount = resolveDesiredCount(
                dc.getDropCountMin(),
                dc.getDropCountMax(),
                eligible.size(),
                "ALL"
        );

        List<DeathDropItem> toDrop = new ArrayList<>(eligible);
        if (desiredCount < toDrop.size()) {
            Collections.shuffle(toDrop, RANDOM);
            toDrop = new ArrayList<>(toDrop.subList(0, desiredCount));
        }

        World world = Universe.get().getWorld(citizen.getWorldUUID());
        if (world == null) {
            return;
        }

        List<DeathDropItem> finalToDrop = toDrop;
        world.execute(() -> {
            ComponentAccessor<EntityStore> accessor = world.getEntityStore().getStore();
            if (accessor == null) {
                return;
            }

            for (DeathDropItem drop : finalToDrop) {
                if (drop.getItemId().isEmpty()) {
                    continue;
                }

                ItemStack itemStack = new ItemStack(drop.getItemId(), drop.getQuantity());

                Holder<EntityStore>[] entities = ItemComponent.generateItemDrops(
                        accessor, new ArrayList<>(List.of(itemStack)), new Vector3d(position), Vector3f.ZERO);

                accessor.addEntities(entities, AddReason.SPAWN);
            }
        });
    }

    private void handleDeathCommands(@Nonnull CitizenData citizen, @Nonnull DeathConfig dc,
                                     @Nullable PlayerRef attackerPlayerRef) {
        List<CommandAction> commands = dc.getDeathCommands();
        if (commands.isEmpty()) {
            return;
        }

        UUID selectionContextUuid = attackerPlayerRef != null ? attackerPlayerRef.getUuid() : NON_PLAYER_CONTEXT_UUID;
        Ref<EntityStore> attackerRef = attackerPlayerRef != null ? attackerPlayerRef.getReference() : null;
        Player player = null;
        if (attackerRef != null && attackerRef.isValid()) {
            player = attackerRef.getStore().getComponent(attackerRef, Player.getComponentType());
        }
        final Player commandPlayer = player;

        List<CommandAction> eligible = commands.stream()
                .filter(cmd -> RANDOM.nextFloat() * 100.0f <= cmd.getChancePercent())
                .filter(cmd -> attackerPlayerRef != null || !containsPlayerPlaceholder(cmd.getCommand()))
                .collect(java.util.stream.Collectors.toList());
        if (eligible.isEmpty()) {
            return;
        }

        List<CommandAction> toRun = selectCommandsByModeAndCount(
                eligible,
                dc.getCommandSelectionMode(),
                citizen.getSequentialDeathCommandIndex(),
                selectionContextUuid,
                dc.getCommandCountMin(),
                dc.getCommandCountMax()
        );

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (CommandAction cmd : toRun) {
            chain = chain.thenCompose(v -> {
                if (cmd.getDelaySeconds() > 0) {
                    CompletableFuture<Void> delayed = new CompletableFuture<>();
                    HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> delayed.complete(null),
                            (long) (cmd.getDelaySeconds() * 1000), TimeUnit.MILLISECONDS);
                    return delayed;
                }
                return CompletableFuture.completedFuture(null);
            }).thenCompose(v -> {
                String command = replacePlaceholders(cmd.getCommand(), attackerPlayerRef, citizen);

                if (command.startsWith("{SendMessage}")) {
                    if (attackerPlayerRef == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    String messageContent = command.substring("{SendMessage}".length()).trim();
                    Message msg = CitizenInteraction.parseColoredMessage(messageContent);
                    if (msg != null) {
                        attackerPlayerRef.sendMessage(msg);
                    }
                    return CompletableFuture.completedFuture(null);
                } else {
                    if (cmd.isRunAsServer()) {
                        return CommandManager.get().handleCommand(ConsoleSender.INSTANCE, command);
                    } else {
                        if (commandPlayer == null) {
                            getLogger().atWarning().log("[HyCitizens] Skipping death command as player: attacker entity is unavailable.");
                            return CompletableFuture.completedFuture(null);
                        }
                        return CommandManager.get().handleCommand(commandPlayer, command);
                    }
                }
            });
        }
    }

    private void handleDeathMessages(@Nonnull CitizenData citizen, @Nonnull DeathConfig dc,
                                     @Nullable PlayerRef attackerPlayerRef) {
        if (attackerPlayerRef == null) {
            return;
        }

        List<CitizenMessage> messages = dc.getDeathMessages();
        if (messages.isEmpty()) {
            return;
        }

        List<CitizenMessage> eligible = messages.stream()
                .filter(msg -> RANDOM.nextFloat() * 100.0f <= msg.getChancePercent())
                .collect(java.util.stream.Collectors.toList());
        if (eligible.isEmpty()) {
            return;
        }

        List<CitizenMessage> toSend = selectMessagesByModeAndCount(
                eligible,
                dc.getMessageSelectionMode(),
                citizen.getSequentialDeathMessageIndex(),
                attackerPlayerRef.getUuid(),
                dc.getMessageCountMin(),
                dc.getMessageCountMax()
        );

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (CitizenMessage msg : toSend) {
            if (msg.getDelaySeconds() > 0) {
                chain = chain.thenCompose(v -> {
                    CompletableFuture<Void> delayed = new CompletableFuture<>();
                    HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> delayed.complete(null),
                            (long) (msg.getDelaySeconds() * 1000), TimeUnit.MILLISECONDS);
                    return delayed;
                });
            }
            chain = chain.thenCompose(v -> {
                dispatchDeathMessage(citizen, attackerPlayerRef, msg);
                return CompletableFuture.completedFuture(null);
            });
        }
    }

    private void dispatchDeathMessage(@Nonnull CitizenData citizen, @Nonnull PlayerRef playerRef,
                                      @Nonnull CitizenMessage cm) {
        String text = replacePlaceholders(cm.getMessage(), playerRef, citizen);

        Message parsed = CitizenInteraction.parseColoredMessage(text);
        if (parsed == null) {
            return;
        }

        if (cm.getDelaySeconds() > 0) {
            final Message finalMsg = parsed;
            HytaleServer.SCHEDULED_EXECUTOR.schedule(
                    () -> playerRef.sendMessage(finalMsg),
                    (long) (cm.getDelaySeconds() * 1000), TimeUnit.MILLISECONDS);
        } else {
            playerRef.sendMessage(parsed);
        }
    }

    @Nonnull
    private List<CommandAction> selectCommandsByModeAndCount(@Nonnull List<CommandAction> source,
                                                             @Nonnull String mode,
                                                             @Nonnull Map<UUID, Integer> sequentialMap,
                                                             @Nonnull UUID playerUuid,
                                                             int minCount,
                                                             int maxCount) {
        int desiredCount = resolveDesiredCount(minCount, maxCount, source.size(), mode);
        if (desiredCount <= 0) {
            return List.of();
        }

        String normalized = mode.toUpperCase(Locale.ROOT);
        if ("RANDOM".equals(normalized)) {
            List<CommandAction> shuffled = new ArrayList<>(source);
            Collections.shuffle(shuffled, RANDOM);
            return new ArrayList<>(shuffled.subList(0, Math.min(desiredCount, shuffled.size())));
        }

        if ("SEQUENTIAL".equals(normalized)) {
            List<CommandAction> selected = new ArrayList<>();
            int start = sequentialMap.getOrDefault(playerUuid, 0);
            for (int i = 0; i < desiredCount; i++) {
                selected.add(source.get((start + i) % source.size()));
            }
            sequentialMap.put(playerUuid, start + desiredCount);
            return selected;
        }

        return new ArrayList<>(source.subList(0, Math.min(desiredCount, source.size())));
    }

    @Nonnull
    private List<CitizenMessage> selectMessagesByModeAndCount(@Nonnull List<CitizenMessage> source,
                                                              @Nonnull String mode,
                                                              @Nonnull Map<UUID, Integer> sequentialMap,
                                                              @Nonnull UUID playerUuid,
                                                              int minCount,
                                                              int maxCount) {
        int desiredCount = resolveDesiredCount(minCount, maxCount, source.size(), mode);
        if (desiredCount <= 0) {
            return List.of();
        }

        String normalized = mode.toUpperCase(Locale.ROOT);
        if ("RANDOM".equals(normalized)) {
            List<CitizenMessage> shuffled = new ArrayList<>(source);
            Collections.shuffle(shuffled, RANDOM);
            return new ArrayList<>(shuffled.subList(0, Math.min(desiredCount, shuffled.size())));
        }

        if ("SEQUENTIAL".equals(normalized)) {
            List<CitizenMessage> selected = new ArrayList<>();
            int start = sequentialMap.getOrDefault(playerUuid, 0);
            for (int i = 0; i < desiredCount; i++) {
                selected.add(source.get((start + i) % source.size()));
            }
            sequentialMap.put(playerUuid, start + desiredCount);
            return selected;
        }

        return new ArrayList<>(source.subList(0, Math.min(desiredCount, source.size())));
    }

    private int resolveDesiredCount(int minCount, int maxCount, int available, @Nonnull String mode) {
        if (available <= 0) {
            return 0;
        }

        int min = Math.max(0, minCount);
        int max = Math.max(0, maxCount);

        if (min == 0 && max == 0) {
            if ("ALL".equalsIgnoreCase(mode)) {
                return available;
            }
            return 1;
        }

        min = Math.max(1, min);
        max = Math.max(1, max);
        if (max < min) {
            int tmp = max;
            max = min;
            min = tmp;
        }

        int clampedMin = Math.min(min, available);
        int clampedMax = Math.min(max, available);
        if (clampedMax < clampedMin) {
            clampedMax = clampedMin;
        }

        if (clampedMin == clampedMax) {
            return clampedMin;
        }
        return clampedMin + RANDOM.nextInt(clampedMax - clampedMin + 1);
    }

    @Nonnull
    private String replacePlaceholders(@Nonnull String text, @Nullable PlayerRef playerRef, @Nonnull CitizenData citizen) {
        Vector3d npcPos = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
        String npcX = String.format(Locale.ROOT, "%.2f", npcPos.x);
        String npcY = String.format(Locale.ROOT, "%.2f", npcPos.y);
        String npcZ = String.format(Locale.ROOT, "%.2f", npcPos.z);

        if (playerRef != null) {
            text = PLAYER_NAME_PATTERN
                    .matcher(text)
                    .replaceAll(Matcher.quoteReplacement(playerRef.getUsername()));
        }
        text = CITIZEN_NAME_PATTERN
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(citizen.getName()));
        text = NPC_X_PATTERN
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(npcX));
        text = NPC_Y_PATTERN
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(npcY));
        text = NPC_Z_PATTERN
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement(npcZ));
        return text;
    }

    private boolean containsPlayerPlaceholder(@Nonnull String text) {
        return PLAYER_NAME_PATTERN.matcher(text).find();
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
