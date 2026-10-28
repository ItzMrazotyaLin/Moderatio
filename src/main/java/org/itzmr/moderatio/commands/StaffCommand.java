package org.itzmr.moderatio.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.staff.StaffManager;
import org.itzmr.moderatio.utils.MessageUtils;

import java.util.Collections;
import java.util.List;

/**
 * Command executor for /staff (toggles Staff Mode or opens menu).
 */
public class StaffCommand implements CommandExecutor, TabCompleter {

    private final Moderatio plugin;
    private final StaffManager staffManager;

    public StaffCommand(Moderatio plugin) {
        this.plugin = plugin;
        this.staffManager = plugin.getStaffManager();
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

        if (args.length > 0 && args[0].equalsIgnoreCase("menu")) {
            if (!staffManager.isInStaffMode(player)) {
                MessageUtils.send(player, plugin.getConfigManager().getMessage("staff-mode.not-enabled"));
                return true;
            }
            plugin.getDialogService().openStaffMenuDialog(player);
            return true;
        }

        if (staffManager.isInStaffMode(player)) {
            staffManager.disableStaffMode(player, true);
        } else {
            staffManager.enableStaffMode(player);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            if ("menu".startsWith(args[0].toLowerCase())) {
                return List.of("menu");
            }
        }
        return Collections.emptyList();
    }
}
