package org.itzmr.moderatio.audit;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.database.StaffLog;
import org.itzmr.moderatio.utils.MessageUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing staff action logging, developers access, and anti-abuse alerts.
 */
public class StaffAuditService {

    private static final int MAX_RECENT_ACTIONS = 10;

    private final Moderatio plugin;
    private final File logFile;
    private final Map<String, Deque<StaffActionEntry>> recentActions = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> punishmentHistory = new ConcurrentHashMap<>();
    private final Set<String> knownStaffMembers = ConcurrentHashMap.newKeySet();

    public StaffAuditService(Moderatio plugin) {
        this.plugin = plugin;
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        this.logFile = new File(logsDir, "staff_actions.log");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create staff_actions.log: " + e.getMessage());
            }
        }
    }

    /**
     * Checks if a player or sender is listed as a developer in config.yml.
     */
    public boolean isDeveloper(CommandSender sender) {
        if (sender == null) {
            return false;
        }

        List<String> devs = plugin.getConfig().getStringList("developers");
        if (devs.isEmpty()) {
            return false;
        }

        String name = sender.getName().toLowerCase();
        for (String dev : devs) {
            if (dev != null && dev.equalsIgnoreCase(name)) {
                return true;
            }
        }

        if (sender instanceof Player p) {
            String uuidStr = p.getUniqueId().toString().toLowerCase();
            for (String dev : devs) {
                if (dev != null && dev.equalsIgnoreCase(uuidStr)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Logs an action performed by a staff member to disk and memory.
     */
    public void logAction(Player moderator, String actionType, String target, String parameters) {
        if (moderator == null) {
            return;
        }

        String modName = moderator.getName();
        knownStaffMembers.add(modName);

        String worldName = moderator.getWorld().getName();
        int x = moderator.getLocation().getBlockX();
        int y = moderator.getLocation().getBlockY();
        int z = moderator.getLocation().getBlockZ();

        StaffActionEntry entry = new StaffActionEntry(
                LocalDateTime.now(),
                modName,
                actionType,
                target,
                parameters,
                worldName,
                x, y, z
        );

        // Store in memory (last 10 actions per moderator)
        recentActions.compute(modName.toLowerCase(), (k, deque) -> {
            if (deque == null) {
                deque = new ArrayDeque<>();
            }
            deque.addFirst(entry);
            while (deque.size() > MAX_RECENT_ACTIONS) {
                deque.removeLast();
            }
            return deque;
        });

        // Asynchronously write to log file
        String logLine = entry.toLogFormat();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (logFile) {
                try (FileWriter fw = new FileWriter(logFile, true);
                     PrintWriter pw = new PrintWriter(fw)) {
                    pw.println(logLine);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to append to staff_actions.log: " + e.getMessage());
                }
            }
        });

        // Asynchronously persist to database
        if (plugin.getDatabaseManager() != null) {
            String details = (target != null && !target.isEmpty() ? "Target: " + target : "")
                    + (parameters != null && !parameters.isEmpty() ? " | " + parameters : "");
            StaffLog staffLog = new StaffLog(
                    moderator.getUniqueId(),
                    modName,
                    actionType,
                    details,
                    worldName,
                    x, y, z,
                    System.currentTimeMillis()
            );
            plugin.getDatabaseManager().logStaffAction(staffLog);
        }
    }

    /**
     * Checks if a moderator has exhausted their allowed punishment limit within the time window.
     * Developers are always exempt.
     */
    public boolean isPunishmentBlocked(Player moderator) {
        if (moderator == null || isDeveloper(moderator)) {
            return false;
        }

        if (!plugin.getConfigManager().isAntiAbuseBlockPunishmentsOnLimit()) {
            return false;
        }

        int limit = plugin.getConfigManager().getAntiAbusePunishmentLimit();
        long windowMs = plugin.getConfigManager().getAntiAbuseCheckIntervalSeconds() * 1000L;
        long now = System.currentTimeMillis();

        List<Long> timestamps = punishmentHistory.get(moderator.getUniqueId());
        if (timestamps == null || timestamps.isEmpty()) {
            return false;
        }

        synchronized (timestamps) {
            timestamps.removeIf(ts -> now - ts > windowMs);
            return timestamps.size() >= limit;
        }
    }

    /**
     * Tracks punishments issued by a moderator and triggers an alert if suspicious activity is detected.
     */
    public void checkPunishmentAbuse(Player moderator) {
        if (moderator == null) {
            return;
        }

        // Developers are completely exempt from anti-abuse checks
        if (isDeveloper(moderator)) {
            return;
        }

        UUID uuid = moderator.getUniqueId();
        long now = System.currentTimeMillis();
        int limit = plugin.getConfigManager().getAntiAbusePunishmentLimit();
        long windowMs = plugin.getConfigManager().getAntiAbuseCheckIntervalSeconds() * 1000L;
        int intervalSeconds = plugin.getConfigManager().getAntiAbuseCheckIntervalSeconds();

        List<Long> timestamps = punishmentHistory.computeIfAbsent(uuid, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (timestamps) {
            timestamps.add(now);
            timestamps.removeIf(ts -> now - ts > windowMs);

            if (timestamps.size() >= limit) {
                sendAbuseAlert(moderator, timestamps.size(), limit, intervalSeconds);
            }
        }
    }

    private void sendAbuseAlert(Player moderator, int count, int limit, int intervalSeconds) {
        String alertMsg = plugin.getConfigManager().getMessage("audit.abuse-alert",
                Map.of("moderator", moderator.getName(),
                        "count", String.valueOf(count),
                        "limit", String.valueOf(limit),
                        "interval", String.valueOf(intervalSeconds)), false);
        if (alertMsg.contains("Missing message")) {
            alertMsg = "<red><bold>[ВНИМАНИЕ]</bold> Подозрительная активность: модератор <yellow>"
                    + moderator.getName() + "</yellow> достиг лимита наказаний (" + count + "/" + limit + " за " + intervalSeconds + "с)!";
        }

        // Alert is sent ONLY to online developers (excluding the perpetrator)
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (isDeveloper(online) && !online.getUniqueId().equals(moderator.getUniqueId())) {
                MessageUtils.send(online, alertMsg);
            }
        }

        plugin.getLogger().warning("Anti-Abuse Alert: Moderator " + moderator.getName() + " reached punishment limit (" + count + "/" + limit + " within " + intervalSeconds + "s)!");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timeStr = LocalDateTime.now().format(dtf);
        String logLine = String.format("[%s] [SUSPICIOUS_ACTIVITY] [LIMIT_REACHED] Модератор %s (UUID: %s) достиг лимита наказаний (%d/%d за %dс)!",
                timeStr, moderator.getName(), moderator.getUniqueId(), count, limit, intervalSeconds);
        writeRawLog(logLine);
    }

    /**
     * Appends a raw log line to staff_actions.log asynchronously.
     */
    public void writeRawLog(String line) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (logFile) {
                try (FileWriter fw = new FileWriter(logFile, true);
                     PrintWriter pw = new PrintWriter(fw)) {
                    pw.println(line);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to write to staff_actions.log: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Retrieves the last actions of a moderator.
     */
    public List<StaffActionEntry> getRecentActions(String moderatorName) {
        if (moderatorName == null) {
            return Collections.emptyList();
        }
        Deque<StaffActionEntry> deque = recentActions.get(moderatorName.toLowerCase());
        if (deque == null || deque.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(deque);
    }

    /**
     * Retrieves names of all known staff members.
     */
    public Set<String> getAllKnownStaff() {
        Set<String> set = new HashSet<>(knownStaffMembers);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("moderatio.staff") || plugin.getStaffManager().isInStaffMode(online)) {
                set.add(online.getName());
            }
        }
        return set;
    }
}
