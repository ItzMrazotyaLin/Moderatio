package org.itzmr.moderatio.database;

/**
 * Supported punishment types in Moderatio.
 */
public enum PunishmentType {
    BAN,
    MUTE,
    WARN;

    public static PunishmentType fromString(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
