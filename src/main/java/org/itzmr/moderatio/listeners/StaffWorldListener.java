package org.itzmr.moderatio.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.staff.StaffManager;
import org.itzmr.moderatio.utils.MessageUtils;

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
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && staffManager.isInStaffMode(player)) {
                staffManager.applyStaffFlags(player);
                String msg = plugin.getConfigManager().getMessage("staff-mode.world-restored");
                MessageUtils.send(player, msg);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!staffManager.isInStaffMode(player)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && staffManager.isInStaffMode(player)) {
                staffManager.applyStaffFlags(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!staffManager.isInStaffMode(player)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && staffManager.isInStaffMode(player)) {
                staffManager.applyStaffFlags(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (staffManager.isInStaffMode(player)) {
            // Revert flags before leaving so state is completely clean
            staffManager.disableStaffMode(player, false);
        }
        staffManager.removeFrozen(player.getUniqueId());
    }
}
