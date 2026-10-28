package org.itzmr.moderatio.staff;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.utils.MessageUtils;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages moderator vanish (internal stealth or external command integration).
 */
public class VanishManager implements Listener {

    private final Moderatio plugin;
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    public VanishManager(Moderatio plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(Player player) {
        return player != null && isVanished(player.getUniqueId());
    }

    public boolean isVanished(UUID uuid) {
        return uuid != null && vanishedPlayers.contains(uuid);
    }

    /**
     * Toggles vanish mode for a moderator.
     */
    public boolean toggleVanish(Player player) {
        if (player == null) return false;
        if (isVanished(player)) {
            disableVanish(player);
            return false;
        } else {
            enableVanish(player);
            return true;
        }
    }

    /**
     * Enables vanish for a moderator.
     */
    public void enableVanish(Player player) {
        if (player == null || !player.isOnline()) return;
        vanishedPlayers.add(player.getUniqueId());

        String mode = plugin.getConfig().getString("vanish.mode", "INTERNAL").toUpperCase();
        if ("EXTERNAL".equals(mode)) {
            String cmd = plugin.getConfig().getString("vanish.external-command", "vanish {moderator}")
                    .replace("{moderator}", player.getName());
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            });
        } else {
            // INTERNAL mode: hide player from everyone and silence
            player.setSilent(true);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.hidePlayer(plugin, player);
                }
            }
        }

        plugin.getAuditService().logAction(player, "VANISH_ENABLE", player.getName(), "Mode: " + mode);
        MessageUtils.send(player, plugin.getConfigManager().getMessage("vanish.enabled"));
    }

    /**
     * Disables vanish for a moderator.
     */
    public void disableVanish(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!vanishedPlayers.remove(player.getUniqueId())) {
            return;
        }

        String mode = plugin.getConfig().getString("vanish.mode", "INTERNAL").toUpperCase();
        if ("EXTERNAL".equals(mode)) {
            String cmd = plugin.getConfig().getString("vanish.external-command", "vanish {moderator}")
                    .replace("{moderator}", player.getName());
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            });
        } else {
            // INTERNAL mode: restore visibility and sounds
            player.setSilent(false);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.showPlayer(plugin, player);
                }
            }
        }

        plugin.getAuditService().logAction(player, "VANISH_DISABLE", player.getName(), "Mode: " + mode);
        MessageUtils.send(player, plugin.getConfigManager().getMessage("vanish.disabled"));
    }

    /**
     * Cleans up vanish when player disconnects or exits Staff Mode.
     */
    public void cleanup(Player player) {
        if (player != null && isVanished(player)) {
            disableVanish(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        String mode = plugin.getConfig().getString("vanish.mode", "INTERNAL").toUpperCase();
        if ("INTERNAL".equals(mode)) {
            for (UUID vanishedId : vanishedPlayers) {
                Player vanished = Bukkit.getPlayer(vanishedId);
                if (vanished != null && vanished.isOnline() && !vanished.equals(joiner)) {
                    joiner.hidePlayer(plugin, vanished);
                }
            }
        }
    }
}
