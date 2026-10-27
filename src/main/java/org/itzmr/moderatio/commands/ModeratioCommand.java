package org.itzmr.moderatio.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.utils.MessageUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Command executor and tab completer for /moderatio.
 */
public class ModeratioCommand implements CommandExecutor, TabCompleter {

    private final Moderatio plugin;

    public ModeratioCommand(Moderatio plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("moderatio.admin")) {
                MessageUtils.send(sender, plugin.getConfigManager().getMessage("general.no-permission"));
                return true;
            }

            plugin.getConfigManager().reload();
            MessageUtils.send(sender, plugin.getConfigManager().getMessage("general.reload-success"));
            return true;
        }

        // Default info output
        int activeStaffCount = plugin.getStaffManager().getActiveStaff().size();
        String version = plugin.getPluginMeta() != null ? plugin.getPluginMeta().getVersion() : plugin.getDescription().getVersion();
        if (version == null || version.startsWith("${") || version.isEmpty()) {
            version = plugin.getDescription().getVersion();
        }
        if (version == null || version.startsWith("${") || version.isEmpty()) {
            version = "1.0.0";
        }

        String author = "itzmr";
        if (plugin.getPluginMeta() != null && !plugin.getPluginMeta().getAuthors().isEmpty()) {
            author = String.join(", ", plugin.getPluginMeta().getAuthors());
        } else if (!plugin.getDescription().getAuthors().isEmpty()) {
            author = String.join(", ", plugin.getDescription().getAuthors());
        }

        Map<String, String> placeholders = Map.of(
                "version", version,
                "author", author,
                "active_staff", String.valueOf(activeStaffCount)
        );

        String infoMsg = plugin.getConfigManager().getMessage("general.info", placeholders, false);
        MessageUtils.send(sender, infoMsg);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            if (sender.hasPermission("moderatio.admin") && "reload".startsWith(args[0].toLowerCase())) {
                return List.of("reload");
            }
        }
        return Collections.emptyList();
    }
}
