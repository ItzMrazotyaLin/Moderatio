package org.itzmr.moderatio.staff;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.utils.MessageUtils;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active moderators in Staff Mode and their status.
 */
public class StaffManager {

    private final Moderatio plugin;
    private final Set<UUID> activeStaff = ConcurrentHashMap.newKeySet();
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();

    public StaffManager(Moderatio plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if a player is in Staff Mode.
     *
     * @param player the player
     * @return true if in staff mode
     */
    public boolean isInStaffMode(Player player) {
        return player != null && isInStaffMode(player.getUniqueId());
    }

    /**
     * Checks if a UUID is in Staff Mode.
     *
     * @param uuid the player's UUID
     * @return true if in staff mode
     */
    public boolean isInStaffMode(UUID uuid) {
        return uuid != null && activeStaff.contains(uuid);
    }

    /**
     * Enables Staff Mode for a player.
     *
     * @param player the player
     */
    public void enableStaffMode(Player player) {
        if (player == null) return;
        activeStaff.add(player.getUniqueId());

        applyStaffFlags(player);

        String msg = plugin.getConfigManager().getMessage("staff-mode.enabled");
        MessageUtils.send(player, msg);
    }

    /**
     * Disables Staff Mode for a player.
     *
     * @param player  the player
     * @param sendMsg whether to send disabled message
     */
    public void disableStaffMode(Player player, boolean sendMsg) {
        if (player == null) return;
        activeStaff.remove(player.getUniqueId());

        removeStaffFlags(player);

        if (sendMsg) {
            String msg = plugin.getConfigManager().getMessage("staff-mode.disabled");
            MessageUtils.send(player, msg);
        }
    }

    /**
     * Applies flight and invulnerability to the player.
     *
     * @param player the player
     */
    public void applyStaffFlags(Player player) {
        if (player == null || !player.isOnline()) return;
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
    }

    /**
     * Reverts flight and invulnerability flags if the player is not in creative/spectator.
     *
     * @param player the player
     */
    public void removeStaffFlags(Player player) {
        if (player == null || !player.isOnline()) return;
        player.setInvulnerable(false);

        GameMode gm = player.getGameMode();
        if (gm != GameMode.CREATIVE && gm != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    /**
     * Refreshes flags for a player if they are in Staff Mode (used on world changes/teleports).
     *
     * @param player the player
     */
    public void refreshStaffFlags(Player player) {
        if (isInStaffMode(player)) {
            applyStaffFlags(player);
        }
    }

    /**
     * Returns an unmodifiable set of all active staff UUIDs.
     */
    public Set<UUID> getActiveStaff() {
        return Collections.unmodifiableSet(activeStaff);
    }

    /**
     * Checks if a player is currently frozen.
     */
    public boolean isFrozen(UUID uuid) {
        return uuid != null && frozenPlayers.contains(uuid);
    }

    /**
     * Toggles the frozen state of a player.
     *
     * @param target the target player
     * @return true if player is now frozen, false if thawed
     */
    public boolean toggleFreeze(Player target) {
        if (target == null) return false;
        UUID uuid = target.getUniqueId();
        if (frozenPlayers.contains(uuid)) {
            frozenPlayers.remove(uuid);
            return false;
        } else {
            frozenPlayers.add(uuid);
            return true;
        }
    }

    /**
     * Unfreezes a player (e.g. on disconnect).
     */
    public void removeFrozen(UUID uuid) {
        if (uuid != null) {
            frozenPlayers.remove(uuid);
        }
    }

    /**
     * Checks if a moderator is allowed to perform trolling actions.
     *
     * @param moderator the moderator
     * @return true if allowed
     */
    public boolean canTroll(Player moderator) {
        if (moderator == null) return false;
        if (!moderator.hasPermission("moderatio.staff")) return false;

        if (!plugin.getConfigManager().isTrollingWhitelistEnabled()) {
            return true;
        }

        var whitelist = plugin.getConfigManager().getTrollingWhitelist();
        String uuidStr = moderator.getUniqueId().toString().toLowerCase();
        String name = moderator.getName().toLowerCase();

        for (String entry : whitelist) {
            if (entry != null) {
                String normalized = entry.trim().toLowerCase();
                if (normalized.equals(name) || normalized.equals(uuidStr)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Cleans up all staff modes on server shutdown or plugin disable.
     */
    public void cleanup() {
        for (UUID uuid : activeStaff) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                removeStaffFlags(player);
            }
        }
        activeStaff.clear();
        frozenPlayers.clear();
    }
}
