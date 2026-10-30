package org.itzmr.moderatio.database.models;

import java.util.UUID;

/**
 * Model alias in models package.
 */
public class StaffLog extends org.itzmr.moderatio.database.StaffLog {
    public StaffLog(int id, UUID staffUuid, String staffName, String actionType, String details, String world, double x, double y, double z, long timestamp) {
        super(id, staffUuid, staffName, actionType, details, world, x, y, z, timestamp);
    }

    public StaffLog(UUID staffUuid, String staffName, String actionType, String details, String world, double x, double y, double z, long timestamp) {
        super(staffUuid, staffName, actionType, details, world, x, y, z, timestamp);
    }

    public StaffLog(UUID staffUuid, String staffName, String actionType, String details, String world, double x, double y, double z) {
        super(staffUuid, staffName, actionType, details, world, x, y, z);
    }
}
