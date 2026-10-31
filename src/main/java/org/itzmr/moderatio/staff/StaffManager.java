package org.itzmr.moderatio.staff;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.utils.MessageUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active moderators in Staff Mode, persistent freeze, and protections.
 */
public class StaffManager {

    private final Moderatio plugin;
    private final Set<UUID> activeStaff = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> originalLocations = new ConcurrentHashMap<>();
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> noFallPlayers = ConcurrentHashMap.newKeySet();
    private final File frozenFile;
    private final FileConfiguration frozenConfig;

    public StaffManager(Moderatio plugin) {
        this.plugin = plugin;
        this.frozenFile = new File(plugin.getDataFolder(), "frozen_players.yml");
        this.frozenConfig = YamlConfiguration.loadConfiguration(this.frozenFile);
        loadFrozenPlayers();
    }

    /**
     * Loads persistent frozen players from frozen_players.yml.
     */
    public void loadFrozenPlayers() {
        frozenPlayers.clear();
        if (frozenFile.exists()) {
            List<String> list = frozenConfig.getStringList("frozen");
            for (String str : list) {
                try {
                    frozenPlayers.add(UUID.fromString(str));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /**
     * Saves persistent frozen players to frozen_players.yml.
     */
    public synchronized void saveFrozenPlayers() {
        List<String> list = new ArrayList<>();
        for (UUID uuid : frozenPlayers) {
            list.add(uuid.toString());
        }
        frozenConfig.set("frozen", list);
        try {
            frozenConfig.save(frozenFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save frozen_players.yml: " + e.getMessage());
        }
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
     * Enables Staff Mode for a player (with Vanish enabled by default).
     *
     * @param player the player
     */
    public void enableStaffMode(Player player) {
        if (player == null) return;
        activeStaff.add(player.getUniqueId());

        // Fix original location before applying staff flags
        originalLocations.putIfAbsent(player.getUniqueId(), player.getLocation().clone());

        applyStaffFlags(player);

        // 1. Automatically enable vanish by default
        if (plugin.getVanishManager() != null && !plugin.getVanishManager().isVanished(player)) {
            plugin.getVanishManager().enableVanish(player);
        }

        plugin.getAuditService().logAction(player, "STAFF_MODE_ENABLE", player.getName(), null);

        String msg = plugin.getConfigManager().getMessage("staff-mode.enabled");
        MessageUtils.send(player, msg);

        String instructions = """
                <dark_aqua>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_aqua>
                <gradient:aqua:blue><bold>  MODERATIO | РЕЖИМ ПЕРСОНАЛА АКТИВИРОВАН</bold></gradient>
                <gray>• <white>ПКМ по игроку</white> <gray>—</gray> Открыть меню управления нарушителем</gray>
                <gray>• <white>SHIFT + ЛКМ</white> <gray>—</gray> Открыть главное меню модератора</gray>
                <gray>• <white>/staff</white> <gray>—</gray> Выйти из режима с возвратом на исходную точку</gray>
                <dark_aqua>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_aqua>""";
        MessageUtils.send(player, instructions);
    }

    /**
     * Disables Staff Mode for a player and restores their original location.
     *
     * @param player  the player
     * @param sendMsg whether to send disabled message
     */
    public void disableStaffMode(Player player, boolean sendMsg) {
        if (player == null) return;
        activeStaff.remove(player.getUniqueId());

        removeStaffFlags(player);

        if (plugin.getVanishManager() != null) {
            plugin.getVanishManager().cleanup(player);
        }
        if (plugin.getGlowingManager() != null) {
            plugin.getGlowingManager().cleanup(player);
        }
        if (plugin.getCheckManager() != null) {
            plugin.getCheckManager().onModeratorExitStaff(player);
        }

        // Return to original location
        Location savedLoc = originalLocations.remove(player.getUniqueId());
        if (savedLoc != null && savedLoc.getWorld() != null) {
            World world = Bukkit.getWorld(savedLoc.getWorld().getUID());
            if (world == null) {
                world = Bukkit.getWorld(savedLoc.getWorld().getName());
            }
            if (world != null) {
                savedLoc.setWorld(world);
                player.teleport(savedLoc);
            }
        }

        plugin.getAuditService().logAction(player, "STAFF_MODE_DISABLE", player.getName(), null);

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
     * Toggles the frozen state of a player and persists to disk.
     *
     * @param target the target player
     * @return true if player is now frozen, false if thawed
     */
    public boolean toggleFreeze(Player target) {
        if (target == null) return false;
        UUID uuid = target.getUniqueId();
        if (frozenPlayers.contains(uuid)) {
            frozenPlayers.remove(uuid);
            saveFrozenPlayers();
            return false;
        } else {
            frozenPlayers.add(uuid);
            saveFrozenPlayers();
            return true;
        }
    }

    /**
     * Unfreezes a player and saves changes.
     */
    public void removeFrozen(UUID uuid) {
        if (uuid != null && frozenPlayers.remove(uuid)) {
            saveFrozenPlayers();
        }
    }

    /**
     * Checks if a moderator has reached their punishment limit and is blocked from issuing punishments.
     */
    public boolean isPunishmentBlocked(Player moderator) {
        if (moderator == null) return false;
        return plugin.getAuditService().isPunishmentBlocked(moderator);
    }

    /**
     * Checks if a moderator is allowed to perform trolling actions (exclusive to developers).
     *
     * @param moderator the moderator
     * @return true if allowed
     */
    public boolean canTroll(Player moderator) {
        if (moderator == null) return false;
        return plugin.getAuditService().isDeveloper(moderator);
    }

    /**
     * Cleans up all staff modes on server shutdown or plugin disable.
     * Restores original positions, clears vanish and staff flags.
     * Note: Persistent frozen players are preserved and saved!
     */
    public void cleanup() {
        for (UUID uuid : new ArrayList<>(activeStaff)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                disableStaffMode(player, false);
            }
        }
        activeStaff.clear();
        originalLocations.clear();
        saveFrozenPlayers();
        noFallPlayers.clear();
    }

    /**
     * Gets the saved original location of a player before entering Staff Mode.
     */
    public Location getOriginalLocation(UUID uuid) {
        return uuid != null ? originalLocations.get(uuid) : null;
    }

    /**
     * Adds a player to the no-fall damage set.
     *
     * @param uuid the player UUID
     */
    public void addNoFallPlayer(UUID uuid) {
        if (uuid != null) {
            noFallPlayers.add(uuid);
        }
    }

    /**
     * Checks if a player should be protected from the next fall damage.
     *
     * @param uuid the player UUID
     * @return true if protected
     */
    public boolean isNoFallPlayer(UUID uuid) {
        return uuid != null && noFallPlayers.contains(uuid);
    }

    /**
     * Removes a player from the no-fall damage set.
     *
     * @param uuid the player UUID
     * @return true if removed
     */
    public boolean removeNoFallPlayer(UUID uuid) {
        return uuid != null && noFallPlayers.remove(uuid);
    }
}
