package org.itzmr.moderatio.listeners;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.staff.StaffManager;
import org.itzmr.moderatio.utils.MessageUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Handles cross-world persistence (Multiverse-Core compatibility), teleports,
 * respawns, and player quit cleanup for Staff Mode.
 */
public class StaffWorldListener implements Listener {

    private final Moderatio plugin;
    private final StaffManager staffManager;

    public StaffWorldListener(Moderatio plugin) {
        this.plugin = plugin;
        this.staffManager = plugin.getStaffManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!staffManager.isInStaffMode(player)) {
            return;
        }

        // Run 1 tick later to override any world management plugin (e.g. Multiverse-Core) settings
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && staffManager.isInStaffMode(player)) {
                player.setAllowFlight(true);
                player.setFlying(true);
                staffManager.applyStaffFlags(player);
                String msg = plugin.getConfigManager().getMessage("staff-mode.world-restored");
                MessageUtils.send(player, msg);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!staffManager.isInStaffMode(player)) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && staffManager.isInStaffMode(player)) {
                player.setAllowFlight(true);
                player.setFlying(true);
                staffManager.applyStaffFlags(player);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!staffManager.isInStaffMode(player)) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && staffManager.isInStaffMode(player)) {
                player.setAllowFlight(true);
                player.setFlying(true);
                staffManager.applyStaffFlags(player);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (!staffManager.isInStaffMode(player)) {
            return;
        }

        GameMode newGm = event.getNewGameMode();
        if (newGm == GameMode.SURVIVAL || newGm == GameMode.ADVENTURE) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && staffManager.isInStaffMode(player)) {
                    player.setAllowFlight(true);
                    player.setFlying(true);
                    staffManager.applyStaffFlags(player);
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (staffManager.isInStaffMode(player)) {
            // Revert flags before leaving so state is completely clean
            staffManager.disableStaffMode(player, false);
        }
        staffManager.removeNoFallPlayer(player.getUniqueId());

        // Check if player left while frozen (Persistence of freeze)
        if (staffManager.isFrozen(player.getUniqueId())) {
            // DO NOT unfreeze! Keep frozen state persisted.
            String quitMsg = plugin.getConfigManager().getMessage("punishment.freeze-quit-alert",
                    Map.of("player", player.getName()), false);
            if (quitMsg.contains("Missing message")) {
                quitMsg = "<red>Игрок <white>" + player.getName() + "</white> вышел с сервера во время заморозки!</red>";
            }
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("moderatio.staff")) {
                    MessageUtils.send(staff, quitMsg);
                }
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String timeStr = LocalDateTime.now().format(dtf);
            String logLine = String.format("[%s] [FREEZE_QUIT] Игрок: %s (UUID: %s) покинул сервер во время заморозки! | Мир: %s (%d, %d, %d)",
                    timeStr, player.getName(), player.getUniqueId(),
                    player.getWorld().getName(),
                    player.getLocation().getBlockX(),
                    player.getLocation().getBlockY(),
                    player.getLocation().getBlockZ());
            plugin.getAuditService().writeRawLog(logLine);
            plugin.getLogger().warning(logLine);
        }

        if (plugin.getVanishManager() != null) {
            plugin.getVanishManager().cleanup(player);
        }
        if (plugin.getGlowingManager() != null) {
            plugin.getGlowingManager().cleanup(player);
            plugin.getGlowingManager().onTargetQuit(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (staffManager.isFrozen(player.getUniqueId())) {
            String alertStaff = plugin.getConfigManager().getMessage("punishment.freeze-rejoin-alert",
                    Map.of("player", player.getName()), false);
            if (alertStaff.contains("Missing message")) {
                alertStaff = "<red>Игрок <white>" + player.getName() + "</white> зашел на сервер и всё ещё находится в заморозке!</red>";
            }
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("moderatio.staff")) {
                    MessageUtils.send(staff, alertStaff);
                }
            }

            String freezeWarn = plugin.getConfigManager().getMessage("punishment.freeze-join-warning", false);
            if (freezeWarn.contains("Missing message")) {
                freezeWarn = "<red>Вы находитесь в режиме заморозки модерацией! Перемещение заблокировано.</red>";
            }
            MessageUtils.send(player, freezeWarn);
        }
    }
}
