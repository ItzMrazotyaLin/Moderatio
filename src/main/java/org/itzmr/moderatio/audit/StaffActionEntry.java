package org.itzmr.moderatio.audit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Record representing a logged action performed by a staff member.
 */
public record StaffActionEntry(
        LocalDateTime timestamp,
        String moderator,
        String actionType,
        String target,
        String parameters,
        String world,
        int x,
        int y,
        int z
) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String toLogFormat() {
        return String.format("[%s] [%s] Действие: %s | Цель: %s | Параметры: %s | Мир: %s (%d, %d, %d)",
                timestamp.format(FORMATTER),
                moderator,
                actionType,
                target != null && !target.isEmpty() ? target : "-",
                parameters != null && !parameters.isEmpty() ? parameters : "-",
                world != null ? world : "-",
                x, y, z);
    }

    public String toDisplayFormat() {
        return String.format("<gray>[%s]</gray> <yellow>%s</yellow> <gray>»</gray> <white>%s</white> <dark_gray>(%s)</dark_gray>",
                timestamp.format(FORMATTER),
                actionType,
                target != null && !target.isEmpty() ? target : "-",
                parameters != null && !parameters.isEmpty() ? parameters : "-");
    }
}
