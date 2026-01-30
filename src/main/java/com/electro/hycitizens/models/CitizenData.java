package com.electro.hycitizens.models;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.PlayerSkin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CitizenData {
    private final String id;
    private String name;
    private String modelId;
    private Vector3d position;
    private Vector3f rotation;
    private float scale;
    private String requiredPermission;
    private String noPermissionMessage;
    private UUID worldUUID;
    private List<CommandAction> commandActions;
    private UUID spawnedUUID;
    private UUID hologramUUID;

    // Skin-related fields
    private boolean isPlayerModel;
    private boolean useLiveSkin;
    private String skinUsername;
    private PlayerSkin cachedSkin;
    private long lastSkinUpdate;
    private transient long createdAt; // Timestamp when citizen was created (not saved to config)

    public CitizenData(@Nonnull String id, @Nonnull String name, @Nonnull String modelId, @Nonnull UUID worldUUID,
                       @Nonnull Vector3d position, @Nonnull Vector3f rotation, float scale, @Nullable UUID npcUUID,
                       @Nullable UUID hologramUUID, @Nonnull String requiredPermission, @Nonnull String noPermissionMessage,
                       @Nonnull List<CommandAction> commandActions, boolean isPlayerModel, boolean useLiveSkin,
                       @Nullable String skinUsername, @Nullable PlayerSkin cachedSkin, long lastSkinUpdate) {
        this.id = id;
        this.name = name;
        this.modelId = modelId;
        this.worldUUID = worldUUID;
        this.position = position;
        this.rotation = rotation;
        this.scale = scale;
        this.requiredPermission = requiredPermission;
        this.noPermissionMessage = noPermissionMessage;
        this.commandActions = new ArrayList<>(commandActions);
        this.spawnedUUID = npcUUID;
        this.hologramUUID = hologramUUID;
        this.isPlayerModel = isPlayerModel;
        this.useLiveSkin = useLiveSkin;
        this.skinUsername = skinUsername != null ? skinUsername : "";
        this.cachedSkin = cachedSkin;
        this.lastSkinUpdate = lastSkinUpdate;
        this.createdAt = 0;
    }

    @Nonnull
    public String getId() {
        return id;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public void setName(@Nonnull String name) {
        this.name = name;
    }

    @Nonnull
    public String getModelId() {
        return modelId;
    }

    public void setModelId(@Nonnull String modelId) {
        this.modelId = modelId;
    }

    @Nonnull
    public UUID getWorldUUID() {
        return worldUUID;
    }

    public void setWorldUUID(@Nonnull UUID worldUUID) {
        this.worldUUID = worldUUID;
    }

    @Nonnull
    public Vector3d getPosition() {
        return position;
    }

    public void setPosition(@Nonnull Vector3d position) {
        this.position = position;
    }

    @Nonnull
    public Vector3f getRotation() {
        return rotation;
    }

    public void setRotation(@Nonnull Vector3f rotation) {
        this.rotation = rotation;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    @Nonnull
    public String getRequiredPermission() {
        return requiredPermission;
    }

    public void setRequiredPermission(@Nonnull String requiredPermission) {
        this.requiredPermission = requiredPermission;
    }

    @Nonnull
    public String getNoPermissionMessage() {
        return noPermissionMessage;
    }

    public void setNoPermissionMessage(@Nonnull String noPermissionMessage) {
        this.noPermissionMessage = noPermissionMessage;
    }

    @Nonnull
    public List<CommandAction> getCommandActions() {
        return new ArrayList<>(commandActions);
    }

    public void setCommandActions(@Nonnull List<CommandAction> commandActions) {
        this.commandActions = new ArrayList<>(commandActions);
    }

    public void setSpawnedUUID(UUID spawnedUUID) {
        this.spawnedUUID = spawnedUUID;
    }

    public UUID getSpawnedUUID() {
        return spawnedUUID;
    }

    public void setHologramUUID(UUID  hologramUUID) {
        this.hologramUUID = hologramUUID;
    }

    public UUID getHologramUUID() {
        return hologramUUID;
    }

    public boolean requiresPermission() {
        return !requiredPermission.isEmpty();
    }

    public boolean hasCommands() {
        return !commandActions.isEmpty();
    }

    public boolean isPlayerModel() {
        return isPlayerModel;
    }

    public void setPlayerModel(boolean playerModel) {
        this.isPlayerModel = playerModel;
    }

    public boolean isUseLiveSkin() {
        return useLiveSkin;
    }

    public void setUseLiveSkin(boolean useLiveSkin) {
        this.useLiveSkin = useLiveSkin;
    }

    @Nonnull
    public String getSkinUsername() {
        return skinUsername;
    }

    public void setSkinUsername(@Nullable String skinUsername) {
        this.skinUsername = skinUsername != null ? skinUsername : "";
    }

    @Nullable
    public PlayerSkin getCachedSkin() {
        return cachedSkin;
    }

    public void setCachedSkin(@Nullable PlayerSkin cachedSkin) {
        this.cachedSkin = cachedSkin;
    }

    public long getLastSkinUpdate() {
        return lastSkinUpdate;
    }

    public void setLastSkinUpdate(long lastSkinUpdate) {
        this.lastSkinUpdate = lastSkinUpdate;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
