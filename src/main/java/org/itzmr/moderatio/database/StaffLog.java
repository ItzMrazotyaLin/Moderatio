package org.itzmr.moderatio.database;

import java.util.UUID;

/**
 * Model representing a logged staff action in the database.
 */
public class StaffLog {

    private int id;
    private final UUID staffUuid;
    private final String staffName;
    private final String actionType;
    private final String details;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final long timestamp;

    public StaffLog(int id, UUID staffUuid, String staffName, String actionType,
                    String details, String world, double x, double y, double z, long timestamp) {
        this.id = id;
        this.staffUuid = staffUuid;
        this.staffName = staffName;
        this.actionType = actionType;
        this.details = details;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.timestamp = timestamp;
    }

    public StaffLog(UUID staffUuid, String staffName, String actionType,
                    String details, String world, double x, double y, double z, long timestamp) {
        this(0, staffUuid, staffName, actionType, details, world, x, y, z, timestamp);
    }

    public StaffLog(UUID staffUuid, String staffName, String actionType,
                    String details, String world, double x, double y, double z) {
        this(0, staffUuid, staffName, actionType, details, world, x, y, z, System.currentTimeMillis());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getStaffUuid() {
        return staffUuid;
    }

    public String getStaffName() {
        return staffName;
    }

    public String getActionType() {
        return actionType;
    }

    public String getDetails() {
        return details;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "StaffLog{" +
                "id=" + id +
                ", staffName='" + staffName + '\'' +
                ", actionType='" + actionType + '\'' +
                ", details='" + details + '\'' +
                ", world='" + world + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", timestamp=" + timestamp +
                '}';
    }
}
