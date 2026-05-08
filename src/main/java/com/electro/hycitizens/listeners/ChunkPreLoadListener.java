package com.electro.hycitizens.listeners;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.components.CitizenNpcIdentityComponent;
import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.EntityChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class ChunkPreLoadListener {
    private static final long NPC_RESOLVE_TIMEOUT_MS = 15_000L;
    private static final long NPC_RESOLVE_RETRY_MS = 500L;
    private static final long CHUNK_PROCESS_TIMEOUT_MS = 15_000L;
    private static final long CHUNK_PROCESS_RETRY_MS = 250L;
    private static final float MIN_MODEL_SCALE = 0.01f;

    private final HyCitizensPlugin plugin;
    private final Set<String> citizensPendingNpcResolution = ConcurrentHashMap.newKeySet();
    private final Map<String, PendingChunkProcess> pendingChunkProcesses = new ConcurrentHashMap<>();

    public ChunkPreLoadListener(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    public void onChunkPreload(ChunkPreLoadProcessEvent event) {
        World world = event.getChunk().getWorld();
        long eventChunkIndex = event.getChunk().getIndex();
        UUID worldUUID = world.getWorldConfig().getUuid();

        sanitizeChunkPersistentModels(event);
        plugin.getCitizensManager().processPendingNpcRemovals(world, eventChunkIndex);
        plugin.getCitizensManager().processPendingHologramRemovals(world, eventChunkIndex);

        enqueueChunkCitizens(world, worldUUID, eventChunkIndex, collectChunkCitizens(event, worldUUID, eventChunkIndex));
    }

    @Nonnull
    private Set<CitizenData> collectChunkCitizens(@Nonnull ChunkPreLoadProcessEvent event, @Nonnull UUID worldUUID, long eventChunkIndex) {
        Set<CitizenData> candidates = new LinkedHashSet<>();
        List<CitizenData> worldCitizens = new ArrayList<>();
        Map<UUID, CitizenData> citizensByNpcUuid = new HashMap<>();

        for (CitizenData citizen : plugin.getCitizensManager().getAllCitizens()) {
            if (!shouldProcessCitizen(worldUUID, citizen)) {
                continue;
            }

            worldCitizens.add(citizen);

            UUID spawnedUuid = citizen.getSpawnedUUID();
            if (spawnedUuid != null) {
                citizensByNpcUuid.put(spawnedUuid, citizen);
            }

            if (isCitizenTrackedInChunk(citizen, eventChunkIndex)) {
                candidates.add(citizen);
            }
        }

        Holder<ChunkStore> chunkHolder = event.getHolder();
        if (chunkHolder == null) {
            return candidates;
        }

        EntityChunk entityChunk = chunkHolder.getComponent(EntityChunk.getComponentType());
        if (entityChunk == null) {
            return candidates;
        }

        for (Holder<EntityStore> entityHolder : entityChunk.getEntityHolders()) {
            CitizenData matchedCitizen = resolveChunkEntityCitizen(entityHolder, worldCitizens, citizensByNpcUuid);
            if (matchedCitizen != null) {
                candidates.add(matchedCitizen);
            }
        }

        return candidates;
    }

    private boolean shouldProcessCitizen(@Nonnull UUID worldUUID, @Nonnull CitizenData citizen) {
        if (!worldUUID.equals(citizen.getWorldUUID())) {
            return false;
        }

        if (citizen.isAwaitingRespawn()) {
            return false;
        }

        long timeSinceCreation = System.currentTimeMillis() - citizen.getCreatedAt();
        if (timeSinceCreation < 10000) {
            return false;
        }

        return !plugin.getCitizensManager().isCitizenSpawning(citizen.getId());
    }

    private boolean isCitizenTrackedInChunk(@Nonnull CitizenData citizen, long chunkIndex) {
        Vector3d currentPosition = citizen.getCurrentPosition() != null ? citizen.getCurrentPosition() : citizen.getPosition();
        long currentChunkIndex = ChunkUtil.indexChunkFromBlock(currentPosition.x, currentPosition.z);
        if (chunkIndex == currentChunkIndex) {
            return true;
        }

        Vector3d basePosition = citizen.getPosition();
        long baseChunkIndex = ChunkUtil.indexChunkFromBlock(basePosition.x, basePosition.z);
        return chunkIndex == baseChunkIndex;
    }

    @Nullable
    private CitizenData resolveChunkEntityCitizen(@Nullable Holder<EntityStore> entityHolder,
                                                  @Nonnull List<CitizenData> worldCitizens,
                                                  @Nonnull Map<UUID, CitizenData> citizensByNpcUuid) {
        if (entityHolder == null) {
            return null;
        }

        NPCEntity npc = entityHolder.getComponent(NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) {
            return null;
        }

        CitizenNpcIdentityComponent identityComponent =
                entityHolder.getComponent(CitizenNpcIdentityComponent.getComponentType());
        if (identityComponent != null) {
            for (CitizenData citizen : worldCitizens) {
                if (citizen.getId().equals(identityComponent.getCitizenId())) {
                    return citizen;
                }
            }
        }

        UUIDComponent uuidComponent = entityHolder.getComponent(UUIDComponent.getComponentType());
        if (uuidComponent != null) {
            CitizenData matchedByUuid = citizensByNpcUuid.get(uuidComponent.getUuid());
            if (matchedByUuid != null) {
                return matchedByUuid;
            }
        }

        String roleName = npc.getRole().getRoleName();
        if (roleName == null || !roleName.startsWith("HyCitizens_")) {
            return null;
        }

        for (CitizenData citizen : worldCitizens) {
            if (roleName.startsWith("HyCitizens_" + citizen.getId() + "_")) {
                return citizen;
            }
        }

        return null;
    }

    private void enqueueChunkCitizens(@Nonnull World world, @Nonnull UUID worldUUID, long chunkIndex, @Nonnull Set<CitizenData> citizens) {
        if (citizens.isEmpty()) {
            return;
        }

        String processKey = getChunkProcessKey(worldUUID, chunkIndex);
        boolean[] created = { false };

        PendingChunkProcess process = pendingChunkProcesses.compute(processKey, (key, existingProcess) -> {
            PendingChunkProcess processToUse = existingProcess;
            if (processToUse == null || processToUse.isCompleted()) {
                processToUse = new PendingChunkProcess(world, chunkIndex);
                created[0] = true;
            }

            for (CitizenData citizen : citizens) {
                if (!citizen.isAwaitingRespawn()) {
                    processToUse.addCitizen(citizen);
                }
            }

            return processToUse;
        });

        if (created[0]) {
            process.setFuture(HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                    () -> tickPendingChunkProcess(processKey, process),
                    0,
                    CHUNK_PROCESS_RETRY_MS,
                    TimeUnit.MILLISECONDS
            ));
        }
    }

    private void tickPendingChunkProcess(@Nonnull String processKey, @Nonnull PendingChunkProcess process) {
        if (process.isCompleted() || !process.markQueued()) {
            return;
        }

        World world = process.getWorld();
        long chunkIndex = process.getChunkIndex();
        boolean loaded = world.getChunkIfLoaded(chunkIndex) != null;
        long elapsedMs = System.currentTimeMillis() - process.getStartedAt();

        if (!loaded && elapsedMs < CHUNK_PROCESS_TIMEOUT_MS) {
            process.clearQueued();
            return;
        }

        if (!loaded) {
            WorldChunk chunkInMemory = world.getChunkIfInMemory(chunkIndex);
            if (chunkInMemory == null) {
                List<CitizenData> skippedCitizens = finishPendingChunkProcess(processKey, process);
                if (!skippedCitizens.isEmpty()) {
                    getLogger().atWarning().log("Skipped chunk preload recovery for " + skippedCitizens.size()
                            + " HyCitizens NPC(s) in chunk " + chunkIndex + " of world '" + world.getName()
                            + "' because the chunk was not loaded or in memory after " + CHUNK_PROCESS_TIMEOUT_MS + "ms.");
                }
                return;
            }

            world.loadChunkIfInMemory(chunkIndex);
        }

        boolean chunkWasLoaded = loaded;
        world.execute(() -> {
            if (chunkWasLoaded && world.getChunkIfLoaded(chunkIndex) == null) {
                process.clearQueued();
                return;
            }

            processChunkCitizens(world, finishPendingChunkProcess(processKey, process));
        });
    }

    private void processChunkCitizens(@Nonnull World world, @Nonnull List<CitizenData> citizens) {
        for (CitizenData citizen : citizens) {
            if (citizen.isAwaitingRespawn() || plugin.getCitizensManager().isCitizenSpawning(citizen.getId())) {
                continue;
            }

            Ref<EntityStore> entityRef = checkIfNpcExists(world.getEntityStore().getStore(), citizen);
            if (entityRef == null || !entityRef.isValid()) {
                resolveOrSpawnCitizenNPC(world, citizen, true);
            } else {
                onCitizenEntityResolved(citizen, entityRef);
            }
        }
    }

    @Nonnull
    private List<CitizenData> finishPendingChunkProcess(@Nonnull String processKey, @Nonnull PendingChunkProcess process) {
        AtomicReference<List<CitizenData>> snapshotRef = new AtomicReference<>(new ArrayList<>());
        pendingChunkProcesses.computeIfPresent(processKey, (key, currentProcess) -> {
            if (currentProcess != process) {
                return currentProcess;
            }

            process.complete();
            snapshotRef.set(process.snapshotCitizens());
            return null;
        });

        return snapshotRef.get();
    }

    @Nonnull
    private String getChunkProcessKey(@Nonnull UUID worldUUID, long chunkIndex) {
        return worldUUID + ":" + chunkIndex;
    }

    private static final class PendingChunkProcess {
        private final World world;
        private final long chunkIndex;
        private final long startedAt = System.currentTimeMillis();
        private final Map<String, CitizenData> citizensById = new ConcurrentHashMap<>();
        private final AtomicBoolean queued = new AtomicBoolean(false);
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private volatile ScheduledFuture<?> future;

        private PendingChunkProcess(@Nonnull World world, long chunkIndex) {
            this.world = world;
            this.chunkIndex = chunkIndex;
        }

        private void addCitizen(@Nonnull CitizenData citizen) {
            citizensById.put(citizen.getId(), citizen);
        }

        @Nonnull
        private List<CitizenData> snapshotCitizens() {
            return new ArrayList<>(citizensById.values());
        }

        @Nonnull
        private World getWorld() {
            return world;
        }

        private long getChunkIndex() {
            return chunkIndex;
        }

        private long getStartedAt() {
            return startedAt;
        }

        private boolean markQueued() {
            return queued.compareAndSet(false, true);
        }

        private void clearQueued() {
            queued.set(false);
        }

        private boolean isCompleted() {
            return completed.get();
        }

        private void setFuture(@Nonnull ScheduledFuture<?> future) {
            this.future = future;
            if (isCompleted()) {
                future.cancel(false);
            }
        }

        private void complete() {
            completed.set(true);
            ScheduledFuture<?> scheduledFuture = future;
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
        }
    }

    private void resolveOrSpawnCitizenNPC(@Nonnull World world, @Nonnull CitizenData citizen, boolean save) {
        if (citizen.isAwaitingRespawn()) {
            return;
        }

        Ref<EntityStore> currentRef = checkIfNpcExists(world.getEntityStore().getStore(), citizen);
        if (currentRef != null && currentRef.isValid()) {
            onCitizenEntityResolved(citizen, currentRef);
            return;
        }

        UUID storedUuid = citizen.getSpawnedUUID();
        if (storedUuid == null) {
            plugin.getCitizensManager().spawnCitizenNPC(citizen, save);
            return;
        }

        // The chunk pre-load event can run before UUID-backed entities are fully reattached.
        // Retry UUID resolution before deciding it is stale.
        if (!citizensPendingNpcResolution.add(citizen.getId())) {
            return;
        }

        long resolutionStart = System.currentTimeMillis();
        final ScheduledFuture<?>[] futureRef = new ScheduledFuture<?>[1];
        futureRef[0] = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> world.execute(() -> {
            if (citizen.isAwaitingRespawn()) {
                if (futureRef[0] != null) {
                    futureRef[0].cancel(false);
                }
                citizensPendingNpcResolution.remove(citizen.getId());
                return;
            }

            Ref<EntityStore> currentCitizenRef = citizen.getNpcRef();
            if (currentCitizenRef != null && currentCitizenRef.isValid()) {
                if (futureRef[0] != null) {
                    futureRef[0].cancel(false);
                }
                citizensPendingNpcResolution.remove(citizen.getId());
                return;
            }

            Ref<EntityStore> resolvedRef = checkIfNpcExists(world.getEntityStore().getStore(), citizen);
            if (resolvedRef != null && resolvedRef.isValid()) {
                if (futureRef[0] != null) {
                    futureRef[0].cancel(false);
                }
                citizensPendingNpcResolution.remove(citizen.getId());
                onCitizenEntityResolved(citizen, resolvedRef);
                return;
            }

            if (System.currentTimeMillis() - resolutionStart < NPC_RESOLVE_TIMEOUT_MS) {
                return;
            }

            if (futureRef[0] != null) {
                futureRef[0].cancel(false);
            }
            citizensPendingNpcResolution.remove(citizen.getId());

            // UUID is stale after retry timeout
            plugin.getCitizensManager().clearCitizenEntityRef(citizen);
            plugin.getCitizensManager().spawnCitizenNPC(citizen, save);
        }), NPC_RESOLVE_RETRY_MS, NPC_RESOLVE_RETRY_MS, TimeUnit.MILLISECONDS);
    }

    private void onCitizenEntityResolved(@Nonnull CitizenData citizen, @Nonnull Ref<EntityStore> entityRef) {
        if (citizen.isAwaitingRespawn()) {
            return;
        }

        plugin.getCitizensManager().restoreResolvedCitizenState(citizen, entityRef, true);

        String pluginPatrolPath = citizen.getPathConfig().getPluginPatrolPath();
        if (!pluginPatrolPath.isEmpty()
                && plugin.getCitizensManager().getPatrolManager() != null
                && !plugin.getCitizensManager().getPatrolManager().isPatrolling(citizen.getId())) {
            plugin.getCitizensManager().startCitizenPatrol(citizen.getId(), pluginPatrolPath);
        }
    }

    private void sanitizeChunkPersistentModels(@Nonnull ChunkPreLoadProcessEvent event) {
        Holder<ChunkStore> chunkHolder = event.getHolder();
        if (chunkHolder == null) {
            return;
        }

        EntityChunk entityChunk = chunkHolder.getComponent(EntityChunk.getComponentType());
        if (entityChunk == null) {
            return;
        }

        int repairedCount = 0;
        for (Holder<EntityStore> entityHolder : entityChunk.getEntityHolders()) {
            if (entityHolder == null) {
                continue;
            }

            PersistentModel persistentModel = entityHolder.getComponent(PersistentModel.getComponentType());
            if (persistentModel == null) {
                continue;
            }

            Model.ModelReference modelReference = persistentModel.getModelReference();
            if (modelReference == null) {
                entityHolder.tryRemoveComponent(PersistentModel.getComponentType());
                repairedCount++;
                continue;
            }

            float scale = modelReference.getScale();
            if (Float.isFinite(scale) && scale > 0.0f && scale <= 100.0f) {
                continue;
            }

            String modelAssetId = modelReference.getModelAssetId();
            if (modelAssetId == null || modelAssetId.isEmpty()) {
                entityHolder.tryRemoveComponent(PersistentModel.getComponentType());
            } else {
                entityHolder.putComponent(
                        PersistentModel.getComponentType(),
                        new PersistentModel(new Model.ModelReference(
                                modelAssetId,
                                Float.isFinite(scale) && scale > 0.0f ? 100.0f : MIN_MODEL_SCALE,
                                modelReference.getRandomAttachmentIds(),
                                modelReference.isStaticModel()
                        ))
                );
            }
            repairedCount++;
        }

        if (repairedCount > 0) {
            entityChunk.markNeedsSaving();
            getLogger().atWarning().log("Repaired " + repairedCount + " invalid PersistentModel scale values while loading chunk "
                    + event.getChunk().getX() + ", " + event.getChunk().getZ() + " in world '" + event.getChunk().getWorld().getName() + "'.");
        }
    }

    Ref<EntityStore> checkIfNpcExists(Store<EntityStore> store, CitizenData citizen) {
        String rolePrefix = "HyCitizens_" + citizen.getId() + "_";
        Query<EntityStore> query = NPCEntity.getComponentType();
        CompletableFuture<Ref<EntityStore>> found = new CompletableFuture<>();

        store.forEachEntityParallel(query, (index, archetypeChunk, cb) -> {
            if (found.isDone()) {
                return;
            }

            Ref<EntityStore> otherRef = archetypeChunk.getReferenceTo(index);
            if (otherRef == null || !otherRef.isValid()) {
                return;
            }

            NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
            if (npc == null || npc.getRole() == null) {
                return;
            }

            CitizenNpcIdentityComponent identityComponent =
                    archetypeChunk.getComponent(index, CitizenNpcIdentityComponent.getComponentType());
            if (identityComponent != null && citizen.getId().equals(identityComponent.getCitizenId())) {
                found.complete(otherRef);
                return;
            }

            UUIDComponent uuidComponent = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
            if (citizen.getSpawnedUUID() != null && uuidComponent != null
                    && citizen.getSpawnedUUID().equals(uuidComponent.getUuid())) {
                found.complete(otherRef);
                return;
            }

            String roleName = npc.getRole().getRoleName();
            if (roleName != null && roleName.startsWith(rolePrefix)) {
                found.complete(otherRef);
            }
        });

        return found.isDone() ? found.join() : null;
    }
}
