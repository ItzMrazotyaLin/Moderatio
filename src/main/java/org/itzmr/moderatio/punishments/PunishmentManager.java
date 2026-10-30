package org.itzmr.moderatio.punishments;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.database.Punishment;
import org.itzmr.moderatio.database.PunishmentType;
import org.itzmr.moderatio.database.StaffLog;
import org.itzmr.moderatio.utils.MessageUtils;
import org.itzmr.moderatio.utils.TimeUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages active punishments, local in-memory caches, and enforcement workflows.
 */
public class PunishmentManager {

    private final Moderatio plugin;

    // Active in-memory caches
    private final Map<UUID, Punishment> activeBans = new ConcurrentHashMap<>();
    private final Map<UUID, Punishment> activeMutes = new ConcurrentHashMap<>();
    private final Map<UUID, List<Punishment>> activeWarns = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final UUID SYSTEM_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public PunishmentManager(Moderatio plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all active bans from database into memory on startup.
     */
    public CompletableFuture<Void> init() {
        return plugin.getDatabaseManager().loadActiveBans().thenAccept(bans -> {
            activeBans.clear();
            long now = System.currentTimeMillis();
            for (Punishment ban : bans) {
                if (ban.isPermanent() || ban.getExpiresAt() > now) {
                    activeBans.put(ban.getTargetUuid(), ban);
                }
            }
            plugin.getLogger().info("[Moderatio] Загружено " + activeBans.size() + " активных банов в локальный кэш.");
        });
    }

    /**
     * Preloads or refreshes player's active mute and warns into memory cache.
     */
    public CompletableFuture<Void> loadPlayerCache(UUID targetUuid) {
        if (targetUuid == null) {
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getDatabaseManager().getActivePunishments(targetUuid).thenAccept(punishments -> {
            List<Punishment> warns = new CopyOnWriteArrayList<>();
            Punishment mute = null;
            long now = System.currentTimeMillis();

            for (Punishment p : punishments) {
                if (p.getType() == PunishmentType.MUTE && p.isActive()) {
                    if (p.isPermanent() || p.getExpiresAt() > now) {
                        mute = p;
                    }
                } else if (p.getType() == PunishmentType.WARN && p.isActive()) {
                    if (p.isPermanent() || p.getExpiresAt() > now) {
                        warns.add(p);
                    }
                }
            }

            if (mute != null) {
                activeMutes.put(targetUuid, mute);
            } else {
                activeMutes.remove(targetUuid);
            }

            if (!warns.isEmpty()) {
                activeWarns.put(targetUuid, warns);
            } else {
                activeWarns.remove(targetUuid);
            }
        });
    }

    /**
     * Unloads player's mute and warns from cache to save RAM upon quitting.
     */
    public void unloadPlayerCache(UUID targetUuid) {
        if (targetUuid != null) {
            activeMutes.remove(targetUuid);
            activeWarns.remove(targetUuid);
        }
    }

    /**
     * Checks if a player has an active ban, automatically updating database if expired.
     */
    public Punishment getActiveBan(UUID targetUuid) {
        if (targetUuid == null) return null;
        Punishment ban = activeBans.get(targetUuid);
        if (ban == null) return null;

        if (ban.isExpired()) {
            plugin.getDatabaseManager().deactivatePunishment(ban.getId());
            activeBans.remove(targetUuid);
            return null;
        }
        return ban;
    }

    /**
     * Checks if a player has an active mute, automatically updating database if expired.
     */
    public Punishment getActiveMute(UUID targetUuid) {
        if (targetUuid == null) return null;
        Punishment mute = activeMutes.get(targetUuid);
        if (mute == null) return null;

        if (mute.isExpired()) {
            plugin.getDatabaseManager().deactivatePunishment(mute.getId());
            activeMutes.remove(targetUuid);
            return null;
        }
        return mute;
    }

    /**
     * Cleans up expired warns from active warns cache and database.
     */
    public void cleanExpiredWarns(UUID targetUuid) {
        if (targetUuid == null) return;
        List<Punishment> list = activeWarns.get(targetUuid);
        if (list == null || list.isEmpty()) return;
        list.removeIf(w -> {
            if (w.isExpired()) {
                plugin.getDatabaseManager().deactivatePunishment(w.getId());
                return true;
            }
            return false;
        });
    }

    /**
     * Retrieves active non-expired warns list for target.
     */
    public List<Punishment> getActiveWarns(UUID targetUuid) {
        if (targetUuid == null) return Collections.emptyList();
        cleanExpiredWarns(targetUuid);
        List<Punishment> list = activeWarns.get(targetUuid);
        return list != null ? list : Collections.emptyList();
    }

    /**
     * Retrieves active non-expired warns list for target filtered by category.
     */
    public List<Punishment> getActiveWarns(UUID targetUuid, String category) {
        if (targetUuid == null) return Collections.emptyList();
        cleanExpiredWarns(targetUuid);
        List<Punishment> list = activeWarns.get(targetUuid);
        if (list == null || list.isEmpty()) return Collections.emptyList();
        String cat = (category != null && category.equalsIgnoreCase("GAME")) ? "GAME" : "CHAT";
        List<Punishment> filtered = new ArrayList<>();
        for (Punishment w : list) {
            if (!w.isExpired() && cat.equalsIgnoreCase(w.getCategory())) {
                filtered.add(w);
            }
        }
        return filtered;
    }

    /**
     * Gets the latest active non-expired warn for target.
     */
    public Punishment getLatestActiveWarn(UUID targetUuid) {
        if (targetUuid == null) return null;
        cleanExpiredWarns(targetUuid);
        List<Punishment> list = activeWarns.get(targetUuid);
        if (list == null || list.isEmpty()) return null;
        for (int i = list.size() - 1; i >= 0; i--) {
            Punishment w = list.get(i);
            if (!w.isExpired() && w.isActive()) {
                return w;
            }
        }
        return null;
    }

    /**
     * Issues a ban to a target player.
     *
     * @param targetUuid     target player UUID
     * @param targetName     target player username
     * @param staffUuid      moderator UUID
     * @param staffName      moderator username
     * @param reason         ban reason
     * @param durationMillis duration in millis (-1 for permanent)
     */
    public CompletableFuture<Void> ban(UUID targetUuid, String targetName, UUID staffUuid, String staffName, String reason, long durationMillis) {
        long now = System.currentTimeMillis();
        long expiresAt = (durationMillis <= 0) ? -1L : now + durationMillis;

        Punishment punishment = new Punishment(
                PunishmentType.BAN,
                targetUuid,
                targetName,
                staffUuid,
                staffName,
                reason,
                now,
                expiresAt,
                true
        );

        // Deactivate previous bans in DB and insert new punishment
        return plugin.getDatabaseManager().deactivatePunishments(targetUuid, PunishmentType.BAN)
                .thenCompose(v -> plugin.getDatabaseManager().addPunishment(punishment))
                .thenRun(() -> {
                    // Update cache
                    activeBans.put(targetUuid, punishment);

                    // Log action
                    logAction(staffUuid, staffName, "BAN", targetName,
                            "Duration: " + TimeUtils.formatDuration(durationMillis) + ", Reason: " + reason);

                    // Kick online player on main thread
                    Player online = Bukkit.getPlayer(targetUuid);
                    if (online != null && online.isOnline()) {
                        Component kickMessage = formatBanScreen(punishment);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (online.isOnline()) {
                                online.kick(kickMessage);
                            }
                        });
                    }
                });
    }

    /**
     * Unbans a player.
     */
    public CompletableFuture<Void> unban(UUID targetUuid, UUID staffUuid, String staffName) {
        activeBans.remove(targetUuid);
        return plugin.getDatabaseManager().deactivatePunishments(targetUuid, PunishmentType.BAN)
                .thenRun(() -> {
                    logAction(staffUuid, staffName, "UNBAN", targetUuid.toString(), "Unbanned");
                });
    }

    /**
     * Issues a mute to a target player.
     */
    public CompletableFuture<Void> mute(UUID targetUuid, String targetName, UUID staffUuid, String staffName, String reason, long durationMillis) {
        long now = System.currentTimeMillis();
        long expiresAt = (durationMillis <= 0) ? -1L : now + durationMillis;

        Punishment punishment = new Punishment(
                PunishmentType.MUTE,
                targetUuid,
                targetName,
                staffUuid,
                staffName,
                reason,
                now,
                expiresAt,
                true
        );

        return plugin.getDatabaseManager().deactivatePunishments(targetUuid, PunishmentType.MUTE)
                .thenCompose(v -> plugin.getDatabaseManager().addPunishment(punishment))
                .thenRun(() -> {
                    activeMutes.put(targetUuid, punishment);

                    logAction(staffUuid, staffName, "MUTE", targetName,
                            "Duration: " + TimeUtils.formatDuration(durationMillis) + ", Reason: " + reason);

                    // Notify online player
                    Player online = Bukkit.getPlayer(targetUuid);
                    if (online != null && online.isOnline()) {
                        String rawMsg = plugin.getConfigManager().getMessage("punishments.chat-muted", false);
                        if (rawMsg.contains("Missing message")) {
                            rawMsg = plugin.getConfigManager().getMessage("punishment.chat-muted", false);
                        }
                        if (rawMsg.contains("Missing message")) {
                            rawMsg = "<red>Вам ограничен доступ к чату!</red> <gray>Причина: <white>{reason}</white> (осталось: <aqua>{remaining}</aqua>)</gray>";
                        }
                        String remaining = TimeUtils.formatRemaining(expiresAt);
                        MessageUtils.send(online, rawMsg.replace("{reason}", reason).replace("{remaining}", remaining));
                    }
                });
    }

    /**
     * Unmutes a player.
     */
    public CompletableFuture<Void> unmute(UUID targetUuid, UUID staffUuid, String staffName) {
        activeMutes.remove(targetUuid);
        return plugin.getDatabaseManager().deactivatePunishments(targetUuid, PunishmentType.MUTE)
                .thenRun(() -> {
                    logAction(staffUuid, staffName, "UNMUTE", targetUuid.toString(), "Unmuted");

                    Player online = Bukkit.getPlayer(targetUuid);
                    if (online != null && online.isOnline()) {
                        String msg = plugin.getConfigManager().getMessage("punishments.unmute-target", false);
                        if (msg.contains("Missing message")) {
                            msg = "<green>Вы были размучены! Соблюдайте правила сервера.</green>";
                        }
                        MessageUtils.send(online, msg);
                    }
                });
    }

    /**
     * Issues a warning to a target player with default category CHAT.
     */
    public CompletableFuture<Void> warn(UUID targetUuid, String targetName, UUID staffUuid, String staffName, String reason) {
        return warn(targetUuid, targetName, staffUuid, staffName, "CHAT", reason);
    }

    /**
     * Issues a warning to a target player with specific category (CHAT or GAME) and checks limits.
     */
    public CompletableFuture<Void> warn(UUID targetUuid, String targetName, UUID staffUuid, String staffName, String category, String reason) {
        String cat = (category != null && category.equalsIgnoreCase("GAME")) ? "GAME" : "CHAT";
        String expireDurationStr = plugin.getConfig().getString("warns.expire-duration", "1d");
        long expireMillis = TimeUtils.parseDurationToMillis(expireDurationStr);
        long now = System.currentTimeMillis();
        long expiresAt = (expireMillis <= 0) ? -1L : now + expireMillis;

        Punishment punishment = new Punishment(
                PunishmentType.WARN,
                targetUuid,
                targetName,
                staffUuid,
                staffName,
                reason,
                now,
                expiresAt,
                true,
                cat
        );

        return plugin.getDatabaseManager().addPunishment(punishment).thenRun(() -> {
            cleanExpiredWarns(targetUuid);
            List<Punishment> warns = activeWarns.computeIfAbsent(targetUuid, k -> new CopyOnWriteArrayList<>());
            warns.add(punishment);

            logAction(staffUuid, staffName, "WARN", targetName, "Category: " + cat + ", Reason: " + reason);

            int maxWarns = plugin.getConfig().getInt("warns.limits." + cat.toLowerCase() + ".max", 3);
            long catCount = warns.stream().filter(w -> !w.isExpired() && cat.equalsIgnoreCase(w.getCategory())).count();
            int currentWarns = (int) catCount;

            // Notify online player
            Player online = Bukkit.getPlayer(targetUuid);
            if (online != null && online.isOnline()) {
                String rawMsg = plugin.getConfigManager().getMessage("punishments.warn-received", false);
                if (rawMsg.contains("Missing message")) {
                    rawMsg = plugin.getConfigManager().getMessage("punishment.warn-received", false);
                }
                if (rawMsg.contains("Missing message")) {
                    rawMsg = "<gold>Вам выдано предупреждение!</gold> <gray>[<yellow>{category}</yellow>] Причина: <white>{reason}</white> (Предупреждений: <yellow>{count}/{max}</yellow>)</gray>";
                }
                String msg = rawMsg.replace("{reason}", reason)
                        .replace("{category}", cat)
                        .replace("{count}", String.valueOf(currentWarns))
                        .replace("{max}", String.valueOf(maxWarns));
                MessageUtils.send(online, msg);
            }

            // Check if warn limit reached
            if (currentWarns >= maxWarns) {
                String mode = plugin.getConfig().getString("warns.mode", "NOTIFICATION").toUpperCase();
                if ("NOTIFICATION".equals(mode)) {
                    String alert = "<gold><bold>[ВАРН-ЛИМИТ]</bold></gold> <yellow>" + targetName + "</yellow> набрал критическое число варнов за "
                            + cat + " (" + currentWarns + "/" + maxWarns + ")! Причина последнего: <white>" + reason + "</white>. Требуется выдать наказание вручную!";
                    for (Player staff : Bukkit.getOnlinePlayers()) {
                        if (staff.hasPermission("moderatio.staff") || plugin.getAuditService().isDeveloper(staff)) {
                            MessageUtils.send(staff, alert);
                        }
                    }
                    plugin.getLogger().warning("[ВАРН-ЛИМИТ] " + targetName + " набрал " + currentWarns + "/" + maxWarns + " варнов за " + cat + "! Причина последнего: " + reason);
                } else {
                    // PUNISHMENT mode
                    String autoReason = reason + " (Автоматически: " + currentWarns + "/" + maxWarns + " варнов от " + staffName + ")";
                    boolean reset = plugin.getConfig().getBoolean("warns.reset-warns-on-action", true);

                    if ("GAME".equals(cat)) {
                        String durStr = plugin.getConfig().getString("warns.limits.game.punish-duration", "1d");
                        long durMillis = TimeUtils.parseDurationToMillis(durStr);
                        ban(targetUuid, targetName, staffUuid, staffName, autoReason, durMillis);
                    } else {
                        String durStr = plugin.getConfig().getString("warns.limits.chat.punish-duration", "12h");
                        long durMillis = TimeUtils.parseDurationToMillis(durStr);
                        mute(targetUuid, targetName, staffUuid, staffName, autoReason, durMillis);
                    }

                    if (reset) {
                        for (Punishment w : warns) {
                            if (cat.equalsIgnoreCase(w.getCategory())) {
                                plugin.getDatabaseManager().deactivatePunishment(w.getId());
                            }
                        }
                        warns.removeIf(w -> cat.equalsIgnoreCase(w.getCategory()));
                    }
                }
            }
        });
    }

    /**
     * Removes the latest active warn from target player.
     */
    public CompletableFuture<Punishment> unwarn(UUID targetUuid, UUID staffUuid, String staffName, String reason) {
        Punishment warnToRemove = getLatestActiveWarn(targetUuid);
        if (warnToRemove == null) {
            return CompletableFuture.completedFuture(null);
        }

        warnToRemove.setActive(false);
        List<Punishment> warns = activeWarns.get(targetUuid);
        if (warns != null) {
            warns.remove(warnToRemove);
        }

        return plugin.getDatabaseManager().deactivatePunishment(warnToRemove.getId()).thenApply(v -> {
            String details = "Warn ID: #" + warnToRemove.getId() + " (" + warnToRemove.getCategory() + "), Reason: " + reason;
            logAction(staffUuid, staffName, "UNWARN", warnToRemove.getTargetName(), details);

            Player online = Bukkit.getPlayer(targetUuid);
            if (online != null && online.isOnline()) {
                int remaining = (warns != null) ? warns.size() : 0;
                MessageUtils.send(online, "<green>С вас снято предупреждение!</green> <gray>Причина: <white>" + reason + "</white> (Осталось варнов: <yellow>" + remaining + "</yellow>)</gray>");
            }
            return warnToRemove;
        });
    }

    /**
     * Formats MiniMessage ban screen component from ban punishment.
     */
    public Component formatBanScreen(Punishment ban) {
        String template = plugin.getConfigManager().getMessage("punishments.ban-screen", false);
        if (template.contains("Missing message")) {
            template = plugin.getConfigManager().getMessage("punishment.ban-screen", false);
        }
        if (template.contains("Missing message")) {
            template = """
                    <red><bold>ВЫ ЗАБЛОКИРОВАНЫ НА СЕРВЕРЕ</bold></red>
                    <gray>Причина: <white>{reason}</white></gray>
                    <gray>Заблокировал: <yellow>{moderator}</yellow></gray>
                    <gray>Срок: <aqua>{duration}</aqua></gray>
                    <gray>Истекает: <white>{expire_date}</white></gray>
                    <newline>
                    <dark_gray>ID наказания: #{id}</dark_gray>
                    """;
        }

        long diff = ban.isPermanent() ? -1L : (ban.getExpiresAt() - ban.getCreatedAt());
        String durationStr = ban.isPermanent() ? "Навсегда" : TimeUtils.formatDuration(diff);
        String expireDateStr = ban.isPermanent() ? "Никогда" : TimeUtils.formatDate(ban.getExpiresAt());

        String formatted = template
                .replace("{reason}", ban.getReason())
                .replace("{moderator}", ban.getStaffName())
                .replace("{duration}", durationStr)
                .replace("{expire_date}", expireDateStr)
                .replace("{id}", String.valueOf(ban.getId()));

        return MessageUtils.parse(formatted);
    }

    private void logAction(UUID staffUuid, String staffName, String actionType, String target, String details) {
        // Log to file
        String timeStr = LocalDateTime.now().format(DTF);
        String line = String.format("[%s] [%s] Действие: %s | Цель: %s | Параметры: %s",
                timeStr, staffName, actionType, target != null ? target : "-", details != null ? details : "-");
        plugin.getAuditService().writeRawLog(line);

        // Log to database
        StaffLog staffLog = new StaffLog(staffUuid, staffName, actionType, details, null, 0, 0, 0, System.currentTimeMillis());
        plugin.getDatabaseManager().logStaffAction(staffLog);
    }

    /**
     * Finds active ban UUID by target player name.
     */
    public UUID findBannedUuidByName(String name) {
        if (name == null) return null;
        for (Map.Entry<UUID, Punishment> entry : activeBans.entrySet()) {
            if (entry.getValue().getTargetName().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Finds active mute UUID by target player name.
     */
    public UUID findMutedUuidByName(String name) {
        if (name == null) return null;
        for (Map.Entry<UUID, Punishment> entry : activeMutes.entrySet()) {
            if (entry.getValue().getTargetName().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Map<UUID, Punishment> getActiveBans() {
        return Collections.unmodifiableMap(activeBans);
    }

    public Map<UUID, Punishment> getActiveMutes() {
        return Collections.unmodifiableMap(activeMutes);
    }

    public Map<UUID, List<Punishment>> getActiveWarns() {
        return Collections.unmodifiableMap(activeWarns);
    }
}
