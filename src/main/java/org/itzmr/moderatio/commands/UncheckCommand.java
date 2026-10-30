package org.itzmr.moderatio.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.staff.CheckSession;
import org.itzmr.moderatio.utils.MessageUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Command executor and tab completer for /uncheck (finishes cheat check).
 */
public class UncheckCommand implements CommandExecutor, TabCompleter {

    private final Moderatio plugin;

    public UncheckCommand(Moderatio plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtils.send(sender, plugin.getConfigManager().getMessage("general.only-players"));
            return true;
        }

        if (!player.hasPermission("moderatio.staff")) {
            MessageUtils.send(player, plugin.getConfigManager().getMessage("general.no-permission"));
            return true;
        }

        Player target = null;
        boolean passed = true;

        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                target = Bukkit.getPlayer(args[0]);
            }
        } else {
            // Check if moderator is currently checking someone
            CheckSession session = plugin.getCheckManager().getSessionByModerator(player.getUniqueId());
            if (session != null) {
                target = Bukkit.getPlayer(session.getSuspectUuid());
            }
        }

        if (args.length >= 2) {
            String status = args[1].toLowerCase();
            if (status.equals("fail") || status.equals("failed") || status.equals("false") || status.equals("читы")) {
                passed = false;
            }
        }

        if (target == null) {
            MessageUtils.send(player, "<red>Использование: /" + label + " <игрок> [passed/fail]</red>");
            return true;
        }

        if (!plugin.getCheckManager().isUnderCheck(target.getUniqueId())) {
            MessageUtils.send(player, "<red>Игрок <white>" + target.getName() + "</white> не находится на проверке!</red>");
            return true;
        }

        plugin.getCheckManager().finishCheck(player, target, passed);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("moderatio.staff")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> list = new ArrayList<>();
            for (CheckSession session : plugin.getCheckManager().getActiveSessions().values()) {
                if (session.getSuspectName().toLowerCase().startsWith(prefix)) {
                    list.add(session.getSuspectName());
                }
            }
            return list;
        }

        if (args.length == 2) {
            return List.of("passed", "fail");
        }

        return Collections.emptyList();
    }
}
