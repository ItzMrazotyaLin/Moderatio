package org.itzmr.moderatio.staff;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.utils.MessageUtils;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-moderator client-side glowing for suspected target players.
 */
public class GlowingManager {

    private static final String TEAM_NAME = "mod_glow";

    private final Moderatio plugin;
    // Moderator UUID -> Set of target UUIDs being watched
    private final Map<UUID, Set<UUID>> activeGlows = new ConcurrentHashMap<>();
    // Target UUID -> Set of moderator UUIDs watching them
    private final Map<UUID, Set<UUID>> targetWatchers = new ConcurrentHashMap<>();

    public GlowingManager(Moderatio plugin) {
        this.plugin = plugin;
    }

    public boolean isGlowing(UUID moderatorId, UUID targetId) {
        if (moderatorId == null || targetId == null) return false;
        Set<UUID> set = activeGlows.get(moderatorId);
        return set != null && set.contains(targetId);
    }

    /**
     * Toggles glowing of target player for the moderator.
     */
    public boolean toggleGlowing(Player moderator, Player target) {
        if (moderator == null || target == null) return false;
        if (isGlowing(moderator.getUniqueId(), target.getUniqueId())) {
            disableGlowing(moderator, target);
            return false;
        } else {
            enableGlowing(moderator, target);
            return true;
        }
    }

    /**
     * Enables glowing of target player exclusively for the specified moderator.
     */
    public void enableGlowing(Player moderator, Player target) {
        if (moderator == null || target == null || !moderator.isOnline() || !target.isOnline()) {
            return;
        }

        UUID modId = moderator.getUniqueId();
        UUID targetId = target.getUniqueId();

        // 1. Ensure private scoreboard for moderator
        Scoreboard board = moderator.getScoreboard();
        if (board.equals(Bukkit.getScoreboardManager().getMainScoreboard())) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            moderator.setScoreboard(board);
        }

        // 2. Setup isolated glowing team on moderator's private scoreboard
        Team team = board.getTeam(TEAM_NAME);
        if (team == null) {
            team = board.registerNewTeam(TEAM_NAME);
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        team.color(NamedTextColor.GOLD);
        if (!team.hasEntry(target.getName())) {
            team.addEntry(target.getName());
        }

        // 3. Activate glowing on the target entity so the full body silhouette glows through walls
        target.setGlowing(true);

        activeGlows.computeIfAbsent(modId, k -> ConcurrentHashMap.newKeySet()).add(targetId);
        targetWatchers.computeIfAbsent(targetId, k -> ConcurrentHashMap.newKeySet()).add(modId);

        plugin.getAuditService().logAction(moderator, "GLOW_ENABLE", target.getName(), null);
        MessageUtils.send(moderator, plugin.getConfigManager().getMessage("glowing.enabled", Map.of("target", target.getName())));
    }

    /**
     * Disables glowing of target player for the specified moderator.
     */
    public void disableGlowing(Player moderator, Player target) {
        if (moderator == null || target == null) return;

        UUID modId = moderator.getUniqueId();
        UUID targetId = target.getUniqueId();

        Set<UUID> set = activeGlows.get(modId);
        if (set != null) {
            set.remove(targetId);
            if (set.isEmpty()) {
                activeGlows.remove(modId);
            }
        }

        Set<UUID> watchers = targetWatchers.get(targetId);
        if (watchers != null) {
            watchers.remove(modId);
            if (watchers.isEmpty()) {
                targetWatchers.remove(targetId);
                if (target.isOnline()) {
                    target.setGlowing(false);
                }
            }
        }

        if (moderator.isOnline()) {
            Scoreboard board = moderator.getScoreboard();
            Team team = board.getTeam(TEAM_NAME);
            if (team != null) {
                team.removeEntry(target.getName());
            }
        }

        plugin.getAuditService().logAction(moderator, "GLOW_DISABLE", target.getName(), null);
        MessageUtils.send(moderator, plugin.getConfigManager().getMessage("glowing.disabled", Map.of("target", target.getName())));
    }

    /**
     * Cleans up all glowing targets for a moderator (e.g. on exit from Staff Mode or disconnect).
     */
    public void cleanup(Player moderator) {
        if (moderator == null) return;
        UUID modId = moderator.getUniqueId();
        Set<UUID> targets = activeGlows.remove(modId);
        if (targets != null) {
            Scoreboard board = moderator.getScoreboard();
            Team team = board.getTeam(TEAM_NAME);
            for (UUID targetId : targets) {
                Player target = Bukkit.getPlayer(targetId);
                if (team != null && target != null) {
                    team.removeEntry(target.getName());
                }

                Set<UUID> watchers = targetWatchers.get(targetId);
                if (watchers != null) {
                    watchers.remove(modId);
                    if (watchers.isEmpty()) {
                        targetWatchers.remove(targetId);
                        if (target != null && target.isOnline()) {
                            target.setGlowing(false);
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles target player quitting by removing them from all active moderator tracking.
     */
    public void onTargetQuit(Player target) {
        if (target == null) return;
        UUID targetId = target.getUniqueId();
        targetWatchers.remove(targetId);

        for (Map.Entry<UUID, Set<UUID>> entry : activeGlows.entrySet()) {
            if (entry.getValue().remove(targetId)) {
                Player mod = Bukkit.getPlayer(entry.getKey());
                if (mod != null && mod.isOnline()) {
                    Team team = mod.getScoreboard().getTeam(TEAM_NAME);
                    if (team != null) {
                        team.removeEntry(target.getName());
                    }
                }
            }
        }
        target.setGlowing(false);
    }
}
