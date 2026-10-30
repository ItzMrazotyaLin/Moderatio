package org.itzmr.moderatio.staff;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.utils.MessageUtils;
import org.itzmr.moderatio.utils.TimeUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages native in-house cheat check sessions, suspect isolation,
 * screen titles, private check-chat routing, and quit-ban handling.
 */
public class CheckManager implements Listener {

    private final Moderatio plugin;

    // Suspect UUID -> CheckSession
    private final Map<UUID, CheckSession> sessionsBySuspect = new ConcurrentHashMap<>();
    // Moderator UUID -> CheckSession
    private final Map<UUID, CheckSession> sessionsByModerator = new ConcurrentHashMap<>();

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
        return targetId != null && sessionsBySuspect.containsKey(targetId);
    }

    /**
     * Checks if a moderator is currently conducting a cheat check.
     */
    public boolean isChecking(Player moderator) {
        return moderator != null && isChecking(moderator.getUniqueId());
    }

    public boolean isChecking(UUID moderatorId) {
        return moderatorId != null && sessionsByModerator.containsKey(moderatorId);
    }

    /**
     * Checks if the specified moderator is currently checking the given target.
     */
    public boolean isCheckingTarget(Player moderator, Player target) {
        if (moderator == null || target == null) return false;
        CheckSession session = sessionsByModerator.get(moderator.getUniqueId());
        return session != null && session.getSuspectUuid().equals(target.getUniqueId());
    }

    public CheckSession getSessionBySuspect(UUID suspectId) {
        return suspectId != null ? sessionsBySuspect.get(suspectId) : null;
    }

    public CheckSession getSessionByModerator(UUID moderatorId) {
        return moderatorId != null ? sessionsByModerator.get(moderatorId) : null;
    }

    public Map<UUID, CheckSession> getActiveSessions() {
        return Collections.unmodifiableMap(sessionsBySuspect);
    }

    /**
     * Starts a cheat check session between moderator and suspect.
     */
    public boolean startCheck(Player moderator, Player suspect) {
        if (moderator == null || suspect == null || !moderator.isOnline() || !suspect.isOnline()) {
            return false;
        }

        if (moderator.getUniqueId().equals(suspect.getUniqueId())) {
            MessageUtils.send(moderator, "<red>Вы не можете проверять самого себя!</red>");
            return false;
        }

        if (isUnderCheck(suspect.getUniqueId())) {
            CheckSession existing = sessionsBySuspect.get(suspect.getUniqueId());
            String modName = (existing != null) ? existing.getModeratorName() : "другим модератором";
            MessageUtils.send(moderator, "<red>Игрок <white>" + suspect.getName() + "</white> уже находится на проверке у модератора <yellow>" + modName + "</yellow>!</red>");
            return false;
        }

        if (isChecking(moderator.getUniqueId())) {
            CheckSession current = sessionsByModerator.get(moderator.getUniqueId());
            String targetName = (current != null) ? current.getSuspectName() : "другого игрока";
            MessageUtils.send(moderator, "<red>Вы уже проводите проверку игрока <white>" + targetName + "</white>! Сначала завершите её через /uncheck.</red>");
            return false;
        }

        int totalSeconds = plugin.getConfigManager().getCheatCheckTimerSeconds();
        if (totalSeconds <= 0) totalSeconds = 180;

        CheckSession session = new CheckSession(
                moderator.getUniqueId(),
                moderator.getName(),
                suspect.getUniqueId(),
                suspect.getName(),
                totalSeconds
        );

        sessionsBySuspect.put(suspect.getUniqueId(), session);
        sessionsByModerator.put(moderator.getUniqueId(), session);

        // Notify suspect in chat
        MessageUtils.send(suspect, "<red><bold>[ВНИМАНИЕ]</bold> Вы были вызваны на проверку на читы модератором <yellow>" + moderator.getName() + "</yellow>!</red>");
        MessageUtils.send(suspect, "<yellow>У вас есть <white>" + (totalSeconds / 60) + "</white> мин на предоставление контактов. Выход с сервера наказуем баном!</yellow>");
        String contactText = plugin.getConfigManager().getCheatCheckDiscordContactText();
        MessageUtils.send(suspect, contactText);

        // Notify moderator
        MessageUtils.send(moderator, "<green>Вы начали проверку на читы игрока <white>" + suspect.getName() + "</white> (Таймер: <yellow>" + (totalSeconds / 60) + " мин</yellow>).</green>");

        // Repeating 1-second timer for Title, BossBar & Contact repetition
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ZERO);
        Component mainTitle = MessageUtils.parse("<red><bold>ПРОВЕРКА НА ЧИТЫ</bold></red>");

        String initialMmSs = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
        BossBar bossBar = BossBar.bossBar(
                MessageUtils.parse("<red><bold>ПРОВЕРКА НА ЧИТЫ</bold></red> <gray>| Осталось: <yellow>" + initialMmSs + "</yellow></gray>"),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS
        );
        session.setBossBar(bossBar);

        moderator.showBossBar(bossBar);
        suspect.showBossBar(bossBar);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player currentSuspect = Bukkit.getPlayer(session.getSuspectUuid());
            if (currentSuspect == null || !currentSuspect.isOnline()) {
                session.stopTimer();
                return;
            }

            int remaining = session.decrementSeconds();
            float progress = Math.max(0.0f, Math.min(1.0f, (float) remaining / (float) session.getTotalSeconds()));
            bossBar.progress(progress);

            String formattedTime = String.format("%02d:%02d", Math.max(0, remaining) / 60, Math.max(0, remaining) % 60);

            if (session.hasProvidedContact()) {
                bossBar.name(MessageUtils.parse("<green><bold>ПРОВЕРКА НА ЧИТЫ</bold></green> <gray>| Данные получены (<gold>" + session.getProvidedService() + "</gold>)</gray>"));
            } else {
                bossBar.name(MessageUtils.parse("<red><bold>ПРОВЕРКА НА ЧИТЫ</bold></red> <gray>| Осталось: <yellow>" + formattedTime + "</yellow></gray>"));
            }

            Component subTitle = MessageUtils.parse("<yellow>Осталось: <white>" + formattedTime + "</white></yellow>");
            currentSuspect.showTitle(Title.title(mainTitle, subTitle, times));

            // Repeat contact instructions every 30 seconds only if contact has NOT yet been provided
            if (!session.hasProvidedContact() && remaining > 0 && remaining % 30 == 0) {
                MessageUtils.send(currentSuspect, contactText);
            }

            if (remaining <= 0) {
                if (!session.hasProvidedContact() && plugin.getConfigManager().isCheatCheckTimeoutBan()) {
                    session.stopTimer();

                    UUID modUuid = session.getModeratorUuid();
                    String modName = session.getModeratorName();
                    String durationStr = plugin.getConfigManager().getCheatCheckTimeoutBanDuration();
                    String reason = plugin.getConfigManager().getCheatCheckTimeoutBanReason();
                    long durationMillis = TimeUtils.parseDurationToMillis(durationStr);

                    plugin.getPunishmentManager().ban(currentSuspect.getUniqueId(), currentSuspect.getName(), modUuid, modName, reason, durationMillis);

                    String alert = "<red>[ВНИМАНИЕ] Игрок <yellow>" + currentSuspect.getName() + "</yellow> не предоставил контакты вовремя и был заблокирован!</red>";
                    for (Player staff : Bukkit.getOnlinePlayers()) {
                        if (staff.hasPermission("moderatio.staff") || plugin.getAuditService().isDeveloper(staff)) {
                            MessageUtils.send(staff, alert);
                        }
                    }

                    plugin.getAuditService().logAction(null, "CHECK_TIMEOUT_BAN", currentSuspect.getName(), "Moderator: " + modName + ", Reason: " + reason);
                    String timeStr = LocalDateTime.now().format(DTF);
                    plugin.getAuditService().writeRawLog(String.format("[%s] [CHECK_TIMEOUT_BAN] Игрок %s забанен за отказ от проверки (таймаут) модератором %s (Бан: %s)",
                            timeStr, currentSuspect.getName(), modName, durationStr));

                    finishCheck(null, currentSuspect, false);
                } else if (!session.hasProvidedContact()) {
                    MessageUtils.send(currentSuspect, "<red><bold>ВРЕМЯ ИСТЕКЛО!</bold> Если вы не предоставили контакты, вы будете заблокированы.</red>");
                    Player currentMod = Bukkit.getPlayer(session.getModeratorUuid());
                    if (currentMod != null && currentMod.isOnline()) {
                        MessageUtils.send(currentMod, "<red>[ВАРН] Время на проверку игрока <white>" + session.getSuspectName() + "</white> истекло!</red>");
                    }
                    session.stopTimer();
                } else {
                    session.stopTimer();
                }
            }
        }, 0L, 20L);

        session.setTimerTask(task);

        // Action logging
        plugin.getAuditService().logAction(moderator, "CHECK_STARTED", suspect.getName(), "Timer: " + totalSeconds + "s");
        String timeStr = LocalDateTime.now().format(DTF);
        String logLine = String.format("[%s] [CHECK_STARTED] Модератор %s начал проверку игрока %s",
                timeStr, moderator.getName(), suspect.getName());
        plugin.getAuditService().writeRawLog(logLine);

        return true;
    }

    /**
     * Finishes a cheat check session (defaulting to passed = true).
     */
    public boolean finishCheck(Player moderator, Player suspect) {
        return finishCheck(moderator, suspect, true);
    }

    /**
     * Finishes a cheat check session, unfreezes suspect, and notifies both parties.
     */
    public boolean finishCheck(Player moderator, Player suspect, boolean passed) {
        UUID suspectId = (suspect != null) ? suspect.getUniqueId() : null;
        if (suspectId == null && moderator != null) {
            CheckSession modSession = sessionsByModerator.get(moderator.getUniqueId());
            if (modSession != null) {
                suspectId = modSession.getSuspectUuid();
                suspect = Bukkit.getPlayer(suspectId);
            }
        }

        if (suspectId == null) {
            return false;
        }

        CheckSession session = sessionsBySuspect.remove(suspectId);
        if (session != null) {
            sessionsByModerator.remove(session.getModeratorUuid());
            session.stopTimer();
        }

        String suspectName = (suspect != null) ? suspect.getName() : (session != null ? session.getSuspectName() : suspectId.toString());
        String modName = (moderator != null) ? moderator.getName() : (session != null ? session.getModeratorName() : "Console");

        if (suspect != null && suspect.isOnline()) {
            suspect.clearTitle();
            if (passed) {
                MessageUtils.send(suspect, "<green>Проверка успешно завершена! Приятной игры.</green>");
            } else {
                MessageUtils.send(suspect, "<red>Проверка на читы завершена со статусом: <bold>НЕ ПРОЙДЕНА</bold>.</red>");
            }
        }

        if (moderator != null && moderator.isOnline()) {
            if (passed) {
                MessageUtils.send(moderator, "<green>Проверка игрока <white>" + suspectName + "</white> успешно завершена (Читы не обнаружены).</green>");
            } else {
                MessageUtils.send(moderator, "<yellow>Проверка игрока <white>" + suspectName + "</white> завершена со статусом: <red>НЕ ПРОЙДЕНА</red>.</yellow>");
            }
        }

        String actionType = passed ? "CHECK_PASSED" : "CHECK_CANCELLED";
        if (moderator != null) {
            plugin.getAuditService().logAction(moderator, actionType, suspectName, null);
        }
        String timeStr = LocalDateTime.now().format(DTF);
        String logLine = String.format("[%s] [%s] Модератор %s завершил проверку игрока %s",
                timeStr, actionType, modName, suspectName);
        plugin.getAuditService().writeRawLog(logLine);

        return true;
    }

    /**
     * Handles quit event during active check session (issues auto-ban if enabled).
     */
    public void handleQuit(Player suspect) {
        if (suspect == null) return;
        UUID suspectId = suspect.getUniqueId();
        CheckSession session = sessionsBySuspect.remove(suspectId);
        if (session == null) {
            // Check if player was a moderator checking someone
            CheckSession modSession = sessionsByModerator.remove(suspectId);
            if (modSession != null) {
                sessionsBySuspect.remove(modSession.getSuspectUuid());
                modSession.stopTimer();
                Player target = Bukkit.getPlayer(modSession.getSuspectUuid());
                if (target != null && target.isOnline()) {
                    target.clearTitle();
                    MessageUtils.send(target, "<yellow>Модератор, проводивший проверку, покинул сервер. Проверка временно приостановлена.</yellow>");
                }
            }
            return;
        }

        // Suspect left during check!
        sessionsByModerator.remove(session.getModeratorUuid());
        session.stopTimer();

        boolean autoBan = plugin.getConfigManager().isCheatCheckAutoBanOnQuit();
        String durationStr = plugin.getConfigManager().getCheatCheckAutoBanDuration();
        String reason = plugin.getConfigManager().getCheatCheckAutoBanReason();
        long durationMillis = TimeUtils.parseDurationToMillis(durationStr);

        UUID modUuid = session.getModeratorUuid();
        String modName = session.getModeratorName();

        if (autoBan) {
            plugin.getPunishmentManager().ban(suspect.getUniqueId(), suspect.getName(), modUuid, modName, reason, durationMillis);
        }

        // Send alert to all online staff and developers
        String alert = "<red>[ВНИМАНИЕ] Игрок <yellow>" + suspect.getName() + "</yellow> покинул сервер во время проверки и был заблокирован!</red>";
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("moderatio.staff") || plugin.getAuditService().isDeveloper(staff)) {
                MessageUtils.send(staff, alert);
            }
        }
        plugin.getLogger().warning("[CHEAT_CHECK] Игрок " + suspect.getName() + " покинул сервер во время проверки модератором " + modName + " и был заблокирован!");

        // Action log
        plugin.getAuditService().logAction(null, "CHECK_QUIT_BAN", suspect.getName(), "Moderator: " + modName + ", Reason: " + reason);
        String timeStr = LocalDateTime.now().format(DTF);
        plugin.getAuditService().writeRawLog(String.format("[%s] [CHECK_QUIT_BAN] Игрок %s вышел во время проверки модератором %s (Бан: %s)",
                timeStr, suspect.getName(), modName, durationStr));
    }

    /**
     * Restricts movement coordinates while allowing head rotation.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (isUnderCheck(player.getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                Location newTo = from.clone();
                newTo.setYaw(to.getYaw());
                newTo.setPitch(to.getPitch());
                event.setTo(newTo);
            }
        }
    }

    /**
     * Intercepts chat from suspect to isolate communication exclusively to staff.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();

        // 1. If sender is the suspect under check
        CheckSession suspectSession = getSessionBySuspect(sender.getUniqueId());
        if (suspectSession != null) {
            event.setCancelled(true);
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            String formatted = "<gold>[ПРОВЕРКА]</gold> <yellow>" + sender.getName() + "</yellow>: <white>" + message + "</white>";
            Component component = MessageUtils.parse(formatted);

            sender.sendMessage(component);

            Player moderator = Bukkit.getPlayer(suspectSession.getModeratorUuid());
            if (moderator != null && moderator.isOnline()) {
                moderator.sendMessage(component);
            }

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.equals(sender) || p.equals(moderator)) continue;
                if (plugin.getAuditService().isDeveloper(p)) {
                    p.sendMessage(component);
                }
            }
            return;
        }

        // 2. If sender is the checking moderator, route their message with [ПРОВЕРКА] prefix to suspect as well
        CheckSession modSession = getSessionByModerator(sender.getUniqueId());
        if (modSession != null) {
            Player suspect = Bukkit.getPlayer(modSession.getSuspectUuid());
            if (suspect != null && suspect.isOnline()) {
                String message = PlainTextComponentSerializer.plainText().serialize(event.message());
                Component formatted = MessageUtils.parse("<gold>[ПРОВЕРКА]</gold> <aqua>" + sender.getName() + "</aqua>: <white>" + message + "</white>");
                suspect.sendMessage(formatted);
            }
        }
    }

    /**
     * Submits contact information (Discord / AnyDesk) from suspect to moderator.
     */
    public boolean submitContact(Player suspect, String service, String contact) {
        if (suspect == null || !suspect.isOnline()) return false;
        CheckSession session = sessionsBySuspect.get(suspect.getUniqueId());
        if (session == null) {
            MessageUtils.send(suspect, "<red>Вы не находитесь на проверке на читы!</red>");
            return false;
        }

        session.setHasProvidedContact(true);
        session.setProvidedService(service);
        session.setProvidedContact(contact);

        // Update BossBar immediately to show contacts received
        BossBar bb = session.getBossBar();
        if (bb != null) {
            bb.name(MessageUtils.parse("<green><bold>ПРОВЕРКА НА ЧИТЫ</bold></green> <gray>| Данные получены (<gold>" + service + "</gold>)</gray>"));
        }

        Player moderator = Bukkit.getPlayer(session.getModeratorUuid());
        String modName = session.getModeratorName();

        // 1. Build clickable message for checking moderator
        Component prefix = MessageUtils.parse("<dark_aqua>[ПРОВЕРКА]</dark_aqua> <yellow>" + suspect.getName() + "</yellow> предоставил <gold>" + service + "</gold>: ");
        Component contactComp = Component.text(contact, NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.copyToClipboard(contact))
                .hoverEvent(HoverEvent.showText(MessageUtils.parse("<gray>Нажмите, чтобы скопировать: <white>" + contact + "</white></gray>")));
        Component fullModMessage = prefix.append(contactComp);

        if (moderator != null && moderator.isOnline()) {
            moderator.sendMessage(fullModMessage);
        }

        // Notify developers
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(moderator) && !p.equals(suspect) && plugin.getAuditService().isDeveloper(p)) {
                p.sendMessage(fullModMessage);
            }
        }

        // 2. Notify suspect confirmation
        MessageUtils.send(suspect, "<green>Ваши данные (" + service + ") переданы модератору! Ожидайте дальнейших инструкций.</green>");

        // Audit logging
        plugin.getAuditService().logAction(moderator, "CHECK_CONTACT_PROVIDED", suspect.getName(), service + ": " + contact);
        String timeStr = LocalDateTime.now().format(DTF);
        plugin.getAuditService().writeRawLog(String.format("[%s] [CHECK_CONTACT] Игрок %s предоставил %s: %s (Модератор: %s)",
                timeStr, suspect.getName(), service, contact, modName));

        return true;
    }

    /**
     * Blocks all commands for suspect during active check, allowing only contact commands (/discord, /anydesk).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isUnderCheck(player.getUniqueId())) {
            String msg = event.getMessage().toLowerCase().trim();
            if (msg.startsWith("/discord") || msg.startsWith("/anydesk") || msg.startsWith("/mdiscord") || msg.startsWith("/manydesk")) {
                return; // Whitelist contact commands
            }
            event.setCancelled(true);
            MessageUtils.send(player, "<red>Во время проверки команды заблокированы! Доступны только <white>/discord</white> и <white>/anydesk</white>.</red>");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        handleQuit(event.getPlayer());
    }

    /**
     * Cleans up check session when a moderator exits Staff Mode.
     */
    public void onModeratorExitStaff(Player moderator) {
        if (moderator == null) return;
        CheckSession session = sessionsByModerator.remove(moderator.getUniqueId());
        if (session != null) {
            sessionsBySuspect.remove(session.getSuspectUuid());
            session.stopTimer();

            Player target = Bukkit.getPlayer(session.getSuspectUuid());
            if (target != null && target.isOnline()) {
                target.clearTitle();
                MessageUtils.send(target, "<yellow>Модератор вышел из Staff Mode. Проверка приостановлена.</yellow>");
            }

            String targetName = target != null ? target.getName() : session.getSuspectName();
            String timeStr = LocalDateTime.now().format(DTF);
            String logLine = String.format("[%s] [CHECK_EXIT_STAFF] Модератор %s вышел из Staff Mode, проверка игрока %s приостановлена.",
                    timeStr, moderator.getName(), targetName);
            plugin.getAuditService().writeRawLog(logLine);
            plugin.getAuditService().logAction(moderator, "CHECK_EXIT_STAFF", targetName, null);
        }
    }

    /**
     * Cleans up all check sessions when plugin is disabled.
     */
    public void cleanupAll() {
        for (CheckSession session : sessionsBySuspect.values()) {
            session.stopTimer();
            Player target = Bukkit.getPlayer(session.getSuspectUuid());
            if (target != null && target.isOnline()) {
                target.clearTitle();
            }
        }
        sessionsBySuspect.clear();
        sessionsByModerator.clear();
    }
}
