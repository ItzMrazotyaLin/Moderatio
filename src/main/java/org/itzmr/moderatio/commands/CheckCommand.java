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
import org.itzmr.moderatio.utils.MessageUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Command executor and tab completer for /check (starts cheat check).
 */
public class CheckCommand implements CommandExecutor, TabCompleter {

    private final Moderatio plugin;

    public CheckCommand(Moderatio plugin) {
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

        if (args.length < 1) {
            MessageUtils.send(player, "<red>Использование: /" + label + " <игрок></red>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = Bukkit.getPlayer(args[0]);
        }

        if (target == null || !target.isOnline()) {
            MessageUtils.send(player, plugin.getConfigManager().getMessage("general.player-not-found"));
            return true;
        }

        plugin.getCheckManager().startCheck(player, target);
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
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.equals(sender)) continue;
                if (!plugin.getCheckManager().isUnderCheck(p.getUniqueId()) && p.getName().toLowerCase().startsWith(prefix)) {
                    list.add(p.getName());
                }
            }
            return list;
        }

        return Collections.emptyList();
    }
}
