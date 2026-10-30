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
import org.itzmr.moderatio.database.Punishment;
import org.itzmr.moderatio.utils.MessageUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Command executor and tab completer for /unban.
 */
public class UnbanCommand implements CommandExecutor, TabCompleter {

    private final Moderatio plugin;
    private static final UUID CONSOLE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public UnbanCommand(Moderatio plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("moderatio.punish.unban")) {
            MessageUtils.send(sender, plugin.getConfigManager().getMessage("general.no-permission"));
            return true;
        }

        if (args.length < 1) {
            MessageUtils.send(sender, "<red>Использование: /" + label + " <игрок></red>");
            return true;
        }

        String targetName = args[0];
        UUID targetUuid = plugin.getPunishmentManager().findBannedUuidByName(targetName);
        if (targetUuid == null) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
            targetUuid = off.getUniqueId();
            if (off.getName() != null) {
                targetName = off.getName();
            }
        }

        Punishment activeBan = plugin.getPunishmentManager().getActiveBan(targetUuid);
        if (activeBan != null && sender instanceof Player player) {
            boolean isDev = plugin.getAuditService().isDeveloper(player);
            if (!isDev && !player.getUniqueId().equals(activeBan.getStaffUuid())) {
                MessageUtils.send(sender, "<red>Вы не можете снять это наказание, так как оно было выдано другим модератором (" + activeBan.getStaffName() + ")!</red>");
                return true;
            }
        }

        UUID staffUuid = (sender instanceof Player p) ? p.getUniqueId() : CONSOLE_UUID;
        String staffName = sender.getName();

        plugin.getPunishmentManager().unban(targetUuid, staffUuid, staffName);

        String template = plugin.getConfigManager().getMessage("punishments.unbanned-success", false);
        if (template.contains("Missing message")) {
            template = plugin.getConfigManager().getMessage("punishment.unbanned-success", false);
        }
        if (template.contains("Missing message")) {
            template = "<green>Игрок <white>{target}</white> успешно разблокирован!</green>";
        }

        MessageUtils.send(sender, template.replace("{target}", targetName));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("moderatio.punish.unban")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> list = new ArrayList<>();
            for (Punishment ban : plugin.getPunishmentManager().getActiveBans().values()) {
                if (ban.getTargetName().toLowerCase().startsWith(prefix)) {
                    list.add(ban.getTargetName());
                }
            }
            return list;
        }

        return Collections.emptyList();
    }
}
