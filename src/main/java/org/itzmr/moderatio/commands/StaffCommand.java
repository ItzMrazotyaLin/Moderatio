package org.itzmr.moderatio.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.staff.StaffManager;
import org.itzmr.moderatio.utils.MessageUtils;

/**
 * Command executor for /staff (toggles Staff Mode).
 */
public class StaffCommand implements CommandExecutor {

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

        if (staffManager.isInStaffMode(player)) {
            staffManager.disableStaffMode(player, true);
        } else {
            staffManager.enableStaffMode(player);
        }

        return true;
    }
}
