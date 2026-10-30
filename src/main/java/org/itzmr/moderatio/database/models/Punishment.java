package org.itzmr.moderatio.database.models;

import java.util.UUID;

/**
 * Model alias in models package.
 */
public class Punishment extends org.itzmr.moderatio.database.Punishment {
    public Punishment(int id, org.itzmr.moderatio.database.PunishmentType type, UUID targetUuid, String targetName, UUID staffUuid, String staffName, String reason, long createdAt, long expiresAt, boolean active) {
        super(id, type, targetUuid, targetName, staffUuid, staffName, reason, createdAt, expiresAt, active);
    }

    public Punishment(org.itzmr.moderatio.database.PunishmentType type, UUID targetUuid, String targetName, UUID staffUuid, String staffName, String reason, long createdAt, long expiresAt, boolean active) {
        super(type, targetUuid, targetName, staffUuid, staffName, reason, createdAt, expiresAt, active);
    }

    public Punishment(org.itzmr.moderatio.database.PunishmentType type, UUID targetUuid, String targetName, UUID staffUuid, String staffName, String reason, long expiresAt) {
        super(type, targetUuid, targetName, staffUuid, staffName, reason, expiresAt);
    }
}
