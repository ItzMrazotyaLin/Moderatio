package org.itzmr.moderatio.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.utils.MessageUtils;
import org.itzmr.moderatio.utils.TimeParser;
import org.itzmr.moderatio.utils.TimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command executor and tab completer for /ban.
 */
public class BanCommand implements CommandExecutor, TabCompleter {

    private final Moderatio plugin;
    private static final UUID CONSOLE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public BanCommand(Moderatio plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("moderatio.punish.ban")) {
            MessageUtils.send(sender, plugin.getConfigManager().getMessage("general.no-permission"));
            return true;
        }

        if (args.length < 2) {
            MessageUtils.send(sender, "<red>Использование: /" + label + " <игрок> <срок: 1h/7d/perm> [причина]</red>");
            return true;
        }

        if (sender instanceof Player player && plugin.getStaffManager().isPunishmentBlocked(player)) {
            MessageUtils.send(player, plugin.getConfigManager().getMessage("punishment.limit-reached"));
            return true;
        }

        long durationMillis;
        try {
            durationMillis = TimeParser.parseDuration(args[1]);
        } catch (IllegalArgumentException e) {
            MessageUtils.send(sender, "<red>" + e.getMessage() + "</red>");
            return true;
        }

        String targetName = args[0];
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        UUID targetUuid;
        if (onlineTarget != null) {
            targetUuid = onlineTarget.getUniqueId();
            targetName = onlineTarget.getName();
        } else {
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
            targetUuid = offlineTarget.getUniqueId();
            if (offlineTarget.getName() != null) {
                targetName = offlineTarget.getName();
            }
        }

        String reason = "Нарушение правил";
        if (args.length >= 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) sb.append(" ");
                sb.append(args[i]);
            }
            reason = sb.toString();
        }

        UUID staffUuid = (sender instanceof Player p) ? p.getUniqueId() : CONSOLE_UUID;
        String staffName = sender.getName();

        plugin.getPunishmentManager().ban(targetUuid, targetName, staffUuid, staffName, reason, durationMillis);

        if (sender instanceof Player p) {
            plugin.getAuditService().checkPunishmentAbuse(p);
        }

        String durStr = (durationMillis <= 0) ? "Навсегда" : TimeUtils.formatDuration(durationMillis);
        Map<String, String> placeholders = Map.of(
                "target", targetName,
                "duration", durStr,
                "reason", reason,
                "moderator", staffName
        );
        MessageUtils.send(sender, plugin.getConfigManager().getMessage("punishment.banned-success", placeholders));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("moderatio.punish.ban")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    list.add(p.getName());
                }
            }
            return list;
        }

        if (args.length == 2) {
            List<String> suggestions = List.of("1h", "12h", "1d", "7d", "14d", "30d", "perm");
            String prefix = args[1].toLowerCase();
            List<String> list = new ArrayList<>();
            for (String s : suggestions) {
                if (s.startsWith(prefix)) {
                    list.add(s);
                }
            }
            return list;
        }

        if (args.length == 3) {
            List<String> reasons = new ArrayList<>();
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("ban-templates");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    String r = sec.getString(key + ".reason");
                    if (r != null) {
                        reasons.add(r);
                    }
                }
            }
            if (reasons.isEmpty()) {
                reasons.add("Использование читов");
                reasons.add("Оскорбление игроков");
                reasons.add("Гриферство");
            }
            return reasons;
        }

        return Collections.emptyList();
    }
}
