package com.electro.hycitizens.models;

import javax.annotation.Nonnull;

public class PathConfig {
    private boolean followPath;
    private String pathName;
    private boolean patrol;
    private float patrolWanderDistance;

    public PathConfig() {
        this.followPath = false;
        this.pathName = "";
        this.patrol = false;
        this.patrolWanderDistance = 25;
    }

    public void copyFrom(@Nonnull PathConfig other) {
        this.followPath = other.followPath;
        this.pathName = other.pathName;
        this.patrol = other.patrol;
        this.patrolWanderDistance = other.patrolWanderDistance;
    }

    public boolean isFollowPath() { return followPath; }
    public void setFollowPath(boolean followPath) { this.followPath = followPath; }

    @Nonnull
    public String getPathName() { return pathName; }
    public void setPathName(@Nonnull String pathName) { this.pathName = pathName; }

    public boolean isPatrol() { return patrol; }
    public void setPatrol(boolean patrol) { this.patrol = patrol; }

    public float getPatrolWanderDistance() { return patrolWanderDistance; }
    public void setPatrolWanderDistance(float patrolWanderDistance) { this.patrolWanderDistance = patrolWanderDistance; }
}
