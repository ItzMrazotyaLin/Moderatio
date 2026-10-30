package org.itzmr.moderatio.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.utils.MessageUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Command executor and tab completer for /warn.
 */
public class WarnCommand implements CommandExecutor, TabCompleter {

    private final Moderatio plugin;
    private static final UUID CONSOLE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public WarnCommand(Moderatio plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("moderatio.punish.warn")) {
            MessageUtils.send(sender, plugin.getConfigManager().getMessage("general.no-permission"));
            return true;
        }

        if (args.length < 1) {
            MessageUtils.send(sender, "<red>Использование: /" + label + " <игрок> [причина]</red>");
            return true;
        }

        if (sender instanceof Player player && plugin.getStaffManager().isPunishmentBlocked(player)) {
            MessageUtils.send(player, plugin.getConfigManager().getMessage("punishment.limit-reached"));
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

        String category = "CHAT";
        int reasonStartIndex = 1;

        if (args.length >= 2) {
            String potentialCategory = args[1].toUpperCase();
            if (potentialCategory.equals("GAME") || potentialCategory.equals("CHAT")) {
                category = potentialCategory;
                reasonStartIndex = 2;
            }
        }

        String reason = "Предупреждение";
        if (args.length > reasonStartIndex) {
            StringBuilder sb = new StringBuilder();
            for (int i = reasonStartIndex; i < args.length; i++) {
                if (i > reasonStartIndex) sb.append(" ");
                sb.append(args[i]);
            }
            reason = sb.toString();
        }

        UUID staffUuid = (sender instanceof Player p) ? p.getUniqueId() : CONSOLE_UUID;
        String staffName = sender.getName();

        plugin.getPunishmentManager().warn(targetUuid, targetName, staffUuid, staffName, category, reason);

        if (sender instanceof Player p) {
            plugin.getAuditService().checkPunishmentAbuse(p);
        }

        MessageUtils.send(sender, "<green>Предупреждение игроку <white>" + targetName + "</white> успешно выдано! <gray>[<yellow>" + category + "</yellow>] Причина: <white>" + reason + "</white></gray></green>");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("moderatio.punish.warn")) {
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
            List<String> list = new ArrayList<>();
            String prefix = args[1].toUpperCase();
            if ("CHAT".startsWith(prefix)) list.add("CHAT");
            if ("GAME".startsWith(prefix)) list.add("GAME");
            return list;
        }

        if (args.length == 3) {
            if ("GAME".equalsIgnoreCase(args[1])) {
                return List.of("Помеха игре", "Тимкилл", "Багоюз", "Гриферство");
            } else {
                return List.of("Спам", "Флуд", "Оскорбление игроков", "Токсичность", "Капс");
            }
        }

        return Collections.emptyList();
    }
}
