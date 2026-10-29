package org.itzmr.moderatio.staff;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.utils.MessageUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active cheat check sessions between moderators and suspects.
 * Tracks session states, executes start/finish commands, auto-detects chat commands,
 * and handles cleanup on disconnect or exit from Staff Mode.
 */
public class CheckManager implements Listener {

    private final Moderatio plugin;
    // Moderator UUID -> Target UUID
    private final Map<UUID, UUID> activeChecks = new ConcurrentHashMap<>();
    // Target UUID -> Moderator UUID
    private final Map<UUID, UUID> reverseChecks = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public CheckManager(Moderatio plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if a player is currently suspected and undergoing a cheat check.
     */
    public boolean isUnderCheck(Player target) {
        return target != null && isUnderCheck(target.getUniqueId());
    }

    public boolean isUnderCheck(UUID targetId) {
        return targetId != null && reverseChecks.containsKey(targetId);
    }

    /**
     * Checks if a moderator is currently conducting a cheat check.
     */
    public boolean isChecking(Player moderator) {
        return moderator != null && isChecking(moderator.getUniqueId());
    }

    public boolean isChecking(UUID moderatorId) {
        return moderatorId != null && activeChecks.containsKey(moderatorId);
    }

    /**
     * Checks if the specified moderator is currently checking the given target.
     */
    public boolean isCheckingTarget(Player moderator, Player target) {
        if (moderator == null || target == null) return false;
        UUID targetId = activeChecks.get(moderator.getUniqueId());
        return targetId != null && targetId.equals(target.getUniqueId());
    }

    public UUID getTargetIdUnderCheck(UUID moderatorId) {
        return moderatorId != null ? activeChecks.get(moderatorId) : null;
    }

    public UUID getModeratorIdChecking(UUID targetId) {
        return targetId != null ? reverseChecks.get(targetId) : null;
    }

    public Map<UUID, UUID> getActiveChecks() {
        return Collections.unmodifiableMap(activeChecks);
    }

    /**
     * Starts a cheat check session, executes the configured start-command,
     * and logs the event to disk and memory.
     */
    public void startCheck(Player moderator, Player target) {
        if (moderator == null || target == null || !moderator.isOnline() || !target.isOnline()) {
            return;
        }

        activeChecks.put(moderator.getUniqueId(), target.getUniqueId());
        reverseChecks.put(target.getUniqueId(), moderator.getUniqueId());

        String template = plugin.getConfigManager().getCheatCheckStartCommand();
        if (template == null || template.isBlank()) {
            template = "check {target}";
        }

        String formattedCmd = template
                .replace("{target}", target.getName())
                .replace("{moderator}", moderator.getName());

        final String finalCmd = formattedCmd.startsWith("/") ? formattedCmd.substring(1).trim() : formattedCmd.trim();
        String executorMode = plugin.getConfigManager().getCheatCheckExecutor();

        Runnable task = () -> {
            if ("CONSOLE".equalsIgnoreCase(executorMode)) {
                plugin.getLogger().info("[Moderatio] [CHEAT_CHECK] Executing console start command: " + finalCmd);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
            } else {
                plugin.getLogger().info("[Moderatio] [CHEAT_CHECK] Moderator " + moderator.getName() + " executing start command: " + finalCmd);
                moderator.performCommand(finalCmd);
            }
        };

        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }

        plugin.getAuditService().logAction(moderator, "CHEAT_CHECK_START", target.getName(), finalCmd);
        String timeStr = LocalDateTime.now().format(DTF);
        String logLine = String.format("[%s] [CHEAT_CHECK] Модератор %s начал проверку на читы для игрока %s (Команда: %s)",
                timeStr, moderator.getName(), target.getName(), finalCmd);
        plugin.getAuditService().writeRawLog(logLine);
    }

    /**
     * Finishes a cheat check session, executes the configured finish-command,
     * and logs the event to disk and memory.
     */
    public void finishCheck(Player moderator, Player target) {
        if (moderator == null) {
            return;
        }

        UUID targetId = activeChecks.remove(moderator.getUniqueId());
        if (targetId != null) {
            reverseChecks.remove(targetId);
        }

        if (target == null && targetId != null) {
            target = Bukkit.getPlayer(targetId);
        }

        String targetName = target != null ? target.getName() : (targetId != null ? targetId.toString() : "-");

        String template = plugin.getConfigManager().getCheatCheckFinishCommand();
        if (template == null || template.isBlank()) {
            template = "uncheck {target}";
        }

        String formattedCmd = template
                .replace("{target}", targetName)
                .replace("{moderator}", moderator.getName());

        final String finalCmd = formattedCmd.startsWith("/") ? formattedCmd.substring(1).trim() : formattedCmd.trim();
        String executorMode = plugin.getConfigManager().getCheatCheckExecutor();

        Runnable task = () -> {
            if ("CONSOLE".equalsIgnoreCase(executorMode)) {
                plugin.getLogger().info("[Moderatio] [CHEAT_CHECK] Executing console finish command: " + finalCmd);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
            } else {
                plugin.getLogger().info("[Moderatio] [CHEAT_CHECK] Moderator " + moderator.getName() + " executing finish command: " + finalCmd);
                moderator.performCommand(finalCmd);
            }
        };

        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }

        plugin.getAuditService().logAction(moderator, "CHEAT_CHECK_FINISH", targetName, finalCmd);
        String timeStr = LocalDateTime.now().format(DTF);
        String logLine = String.format("[%s] [CHEAT_CHECK] Модератор %s завершил проверку на читы для игрока %s (Команда: %s)",
                timeStr, moderator.getName(), targetName, finalCmd);
        plugin.getAuditService().writeRawLog(logLine);
    }

    /**
     * Directly registers an active check when a command was typed manually into chat.
     */
    public void registerDirectCheck(Player moderator, Player target) {
        if (moderator == null || target == null) return;
        activeChecks.put(moderator.getUniqueId(), target.getUniqueId());
        reverseChecks.put(target.getUniqueId(), moderator.getUniqueId());

        plugin.getAuditService().logAction(moderator, "CHEAT_CHECK_START", target.getName(), "Auto-detected via chat");
        String timeStr = LocalDateTime.now().format(DTF);
        String logLine = String.format("[%s] [CHEAT_CHECK] Модератор %s начал проверку на читы для игрока %s (перехвачено из чата)",
                timeStr, moderator.getName(), target.getName());
        plugin.getAuditService().writeRawLog(logLine);
    }

    /**
     * Directly unregisters an active check when an uncheck command was typed manually into chat.
     */
    public void unregisterDirectCheck(Player moderator, Player target) {
        if (moderator == null) return;
        UUID targetId = activeChecks.remove(moderator.getUniqueId());
        if (targetId != null) {
            reverseChecks.remove(targetId);
        }
        String targetName = target != null ? target.getName() : (targetId != null ? targetId.toString() : "-");

        plugin.getAuditService().logAction(moderator, "CHEAT_CHECK_FINISH", targetName, "Auto-detected via chat");
        String timeStr = LocalDateTime.now().format(DTF);
        String logLine = String.format("[%s] [CHEAT_CHECK] Модератор %s завершил проверку на читы для игрока %s (перехвачено из чата)",
                timeStr, moderator.getName(), targetName);
        plugin.getAuditService().writeRawLog(logLine);
    }

    /**
     * Cleans up check session when a moderator exits Staff Mode.
     */
    public void onModeratorExitStaff(Player moderator) {
        if (moderator == null) return;
        UUID targetId = activeChecks.remove(moderator.getUniqueId());
        if (targetId != null) {
            reverseChecks.remove(targetId);
            Player target = Bukkit.getPlayer(targetId);
            String targetName = target != null ? target.getName() : targetId.toString();

            String timeStr = LocalDateTime.now().format(DTF);
            String logLine = String.format("[%s] [CHEAT_CHECK] Модератор %s вышел из Staff Mode, проверка игрока %s завершена.",
                    timeStr, moderator.getName(), targetName);
            plugin.getAuditService().writeRawLog(logLine);
            plugin.getAuditService().logAction(moderator, "CHEAT_CHECK_EXIT_STAFF", targetName, null);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 1. If player was moderator checking someone
        UUID targetId = activeChecks.remove(playerId);
        if (targetId != null) {
            reverseChecks.remove(targetId);
            Player target = Bukkit.getPlayer(targetId);
            String targetName = target != null ? target.getName() : targetId.toString();

            String timeStr = LocalDateTime.now().format(DTF);
            String logLine = String.format("[%s] [CHEAT_CHECK] Модератор %s покинул сервер во время проверки игрока %s!",
                    timeStr, player.getName(), targetName);
            plugin.getAuditService().writeRawLog(logLine);
            plugin.getAuditService().logAction(player, "CHEAT_CHECK_QUIT_MOD", targetName, null);
        }

        // 2. If player was suspect under check
        UUID modId = reverseChecks.remove(playerId);
        if (modId != null) {
            activeChecks.remove(modId);
            Player mod = Bukkit.getPlayer(modId);

            String timeStr = LocalDateTime.now().format(DTF);
            String logLine = String.format("[%s] [CHEAT_CHECK] Подозреваемый %s (UUID: %s) покинул сервер во время проверки модератором %s!",
                    timeStr, player.getName(), playerId, mod != null ? mod.getName() : modId.toString());
            plugin.getAuditService().writeRawLog(logLine);

            if (mod != null && mod.isOnline()) {
                MessageUtils.send(mod, "<red>Подозреваемый <white>" + player.getName() + "</white> покинул сервер во время проверки на читы!</red>");
                plugin.getAuditService().logAction(mod, "CHEAT_CHECK_TARGET_QUIT", player.getName(), null);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfigManager().isCheatCheckEnabled()
                || !plugin.getConfigManager().isCheatCheckAutoDetectChatCommand()) {
            return;
        }

        Player moderator = event.getPlayer();
        if (!plugin.getStaffManager().isInStaffMode(moderator)) {
            return;
        }

        String raw = event.getMessage().trim();
        if (!raw.startsWith("/")) {
            return;
        }

        String message = raw.substring(1).trim();
        String[] split = message.split("\\s+");
        if (split.length == 0) {
            return;
        }

        String cmd = split[0].toLowerCase();
        if (cmd.contains(":")) {
            cmd = cmd.substring(cmd.indexOf(':') + 1);
        }

        String startTrigger = plugin.getConfigManager().getCheatCheckTriggerCommand().toLowerCase();
        String finishTrigger = plugin.getConfigManager().getCheatCheckFinishTriggerCommand().toLowerCase();

        if (cmd.equals(startTrigger)) {
            if (split.length >= 2) {
                String targetName = split[1];
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    target = Bukkit.getPlayer(targetName);
                }
                if (target != null && target.isOnline()) {
                    registerDirectCheck(moderator, target);
                }
            }
        } else if (cmd.equals(finishTrigger)) {
            Player target = null;
            if (split.length >= 2) {
                String targetName = split[1];
                target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    target = Bukkit.getPlayer(targetName);
                }
            }
            if (target == null) {
                UUID targetId = activeChecks.get(moderator.getUniqueId());
                if (targetId != null) {
                    target = Bukkit.getPlayer(targetId);
                }
            }
            if (target != null) {
                unregisterDirectCheck(moderator, target);
            } else {
                UUID targetId = activeChecks.remove(moderator.getUniqueId());
                if (targetId != null) {
                    reverseChecks.remove(targetId);
                }
            }
        }
    }

    /**
     * Cleans up all check sessions when plugin is disabled.
     */
    public void cleanupAll() {
        activeChecks.clear();
        reverseChecks.clear();
    }
}
