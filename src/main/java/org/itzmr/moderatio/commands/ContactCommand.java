package org.itzmr.moderatio.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.utils.MessageUtils;

import java.util.Collections;
import java.util.List;

/**
 * Command executor for suspect contact submission (/discord and /anydesk).
 */
public class ContactCommand implements CommandExecutor, TabCompleter {

    private final Moderatio plugin;
    private final String defaultService;

    public ContactCommand(Moderatio plugin, String defaultService) {
        this.plugin = plugin;
        this.defaultService = defaultService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtils.send(sender, plugin.getConfigManager().getMessage("general.only-players"));
            return true;
        }

        if (!plugin.getCheckManager().isUnderCheck(player.getUniqueId())) {
            MessageUtils.send(player, "<red>Эта команда доступна только во время активной проверки на читы!</red>");
            return true;
        }

        String service = defaultService;
        if (label.equalsIgnoreCase("discord") || label.equalsIgnoreCase("mdiscord")) {
            service = "Discord";
        } else if (label.equalsIgnoreCase("anydesk") || label.equalsIgnoreCase("manydesk")) {
            service = "AnyDesk";
        } else if (label.equalsIgnoreCase("rustdesk") || label.equalsIgnoreCase("mrustdesk")) {
            service = "RustDesk";
        }

        if (args.length < 1) {
            String hint = "<ваш " + service + ">";
            MessageUtils.send(player, "<red>Использование: /" + label + " " + hint + "</red>");
            return true;
        }

        String contact = String.join(" ", args).trim();
        plugin.getCheckManager().submitContact(player, service, contact);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
