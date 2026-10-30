package org.itzmr.moderatio.database;

import java.util.UUID;

/**
 * Model representing a disciplinary punishment (BAN, MUTE, WARN).
 */
public class Punishment {

    private int id;
    private final PunishmentType type;
    private final UUID targetUuid;
    private final String targetName;
    private final UUID staffUuid;
    private final String staffName;
    private final String reason;
    private final long createdAt;
    private final long expiresAt;
    private boolean active;
    private final String category;

    public Punishment(int id, PunishmentType type, UUID targetUuid, String targetName,
                      UUID staffUuid, String staffName, String reason,
                      long createdAt, long expiresAt, boolean active, String category) {
        this.id = id;
        this.type = type;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.staffUuid = staffUuid;
        this.staffName = staffName;
        this.reason = reason;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.active = active;
        this.category = (category != null && !category.isBlank()) ? category.toUpperCase() : "CHAT";
    }

    public Punishment(int id, PunishmentType type, UUID targetUuid, String targetName,
                      UUID staffUuid, String staffName, String reason,
                      long createdAt, long expiresAt, boolean active) {
        this(id, type, targetUuid, targetName, staffUuid, staffName, reason, createdAt, expiresAt, active, "CHAT");
    }

    public Punishment(PunishmentType type, UUID targetUuid, String targetName,
                      UUID staffUuid, String staffName, String reason,
                      long createdAt, long expiresAt, boolean active, String category) {
        this(0, type, targetUuid, targetName, staffUuid, staffName, reason, createdAt, expiresAt, active, category);
    }

    public Punishment(PunishmentType type, UUID targetUuid, String targetName,
                      UUID staffUuid, String staffName, String reason,
                      long createdAt, long expiresAt, boolean active) {
        this(0, type, targetUuid, targetName, staffUuid, staffName, reason, createdAt, expiresAt, active, "CHAT");
    }

    public Punishment(PunishmentType type, UUID targetUuid, String targetName,
                      UUID staffUuid, String staffName, String reason, long expiresAt) {
        this(0, type, targetUuid, targetName, staffUuid, staffName, reason, System.currentTimeMillis(), expiresAt, true, "CHAT");
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public PunishmentType getType() {
        return type;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public UUID getStaffUuid() {
        return staffUuid;
    }

    public String getStaffName() {
        return staffName;
    }

    public String getReason() {
        return reason;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPermanent() {
        return expiresAt <= 0;
    }

    public boolean isExpired() {
        return !isPermanent() && System.currentTimeMillis() > expiresAt;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public String toString() {
        return "Punishment{" +
                "id=" + id +
                ", type=" + type +
                ", targetName='" + targetName + '\'' +
                ", staffName='" + staffName + '\'' +
                ", reason='" + reason + '\'' +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                ", active=" + active +
                '}';
    }
}
