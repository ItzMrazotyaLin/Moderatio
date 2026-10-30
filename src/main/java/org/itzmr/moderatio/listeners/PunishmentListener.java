package org.itzmr.moderatio.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.database.Punishment;
import org.itzmr.moderatio.punishments.PunishmentManager;
import org.itzmr.moderatio.utils.MessageUtils;
import org.itzmr.moderatio.utils.TimeUtils;

import java.util.UUID;

/**
 * Listener for handling punishment enforcements (login bans, chat mutes, and cache loading).
 */
public class PunishmentListener implements Listener {

    private final Moderatio plugin;
    private final PunishmentManager punishmentManager;

    public PunishmentListener(Moderatio plugin) {
        this.plugin = plugin;
        this.punishmentManager = plugin.getPunishmentManager();
    }

    /**
     * Intercepts player login to enforce active bans.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID targetUuid = event.getUniqueId();
        Punishment ban = punishmentManager.getActiveBan(targetUuid);
        if (ban != null) {
            Component banScreen = punishmentManager.formatBanScreen(ban);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banScreen);
        }
    }

    /**
     * Preloads active mutes and warns into cache when player joins.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        punishmentManager.loadPlayerCache(event.getPlayer().getUniqueId());
    }

    /**
     * Unloads player's active mutes and warns from cache when player leaves.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        punishmentManager.unloadPlayerCache(event.getPlayer().getUniqueId());
    }

    /**
     * Enforces active chat mutes.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Punishment mute = punishmentManager.getActiveMute(player.getUniqueId());
        if (mute != null) {
            event.setCancelled(true);

            String template = plugin.getConfigManager().getMessage("punishments.chat-muted", false);
            if (template.contains("Missing message")) {
                template = plugin.getConfigManager().getMessage("punishment.chat-muted", false);
            }
            if (template.contains("Missing message")) {
                template = "<red>Вам ограничен доступ к чату!</red> <gray>Причина: <white>{reason}</white> (осталось: <aqua>{remaining}</aqua>)</gray>";
            }

            String remaining = TimeUtils.formatRemaining(mute.getExpiresAt());
            String formatted = template
                    .replace("{reason}", mute.getReason() != null ? mute.getReason() : "Нарушение правил")
                    .replace("{remaining}", remaining);

            MessageUtils.send(player, formatted);
        }
    }
}
