package org.itzmr.moderatio.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.staff.CheckSession;
import org.itzmr.moderatio.utils.LocationUtils;
import org.itzmr.moderatio.utils.MessageUtils;

import java.util.ArrayList;
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
            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().reload();
            }
            MessageUtils.send(sender, plugin.getConfigManager().getMessage("general.reload-success"));
            return true;
        }

        // Subcommands: /moderatio check ...
        if (args.length > 0 && args[0].equalsIgnoreCase("check")) {
            return handleCheckSubcommands(sender, label, args);
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

    private boolean handleCheckSubcommands(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            MessageUtils.send(sender, "<red>Использование: /" + label + " check <accept|decline|setcheaterspawn|setmoderspawn|setfinishspawn></red>");
            return true;
        }

        String sub = args[1].toLowerCase();

        // 1. Accept suspect's contacts
        if (sub.equals("accept")) {
            if (!sender.hasPermission("moderatio.staff")) {
                MessageUtils.send(sender, plugin.getConfigManager().getMessage("general.no-permission"));
                return true;
            }
            if (args.length < 3) {
                MessageUtils.send(sender, "<red>Использование: /" + label + " check accept <игрок></red>");
                return true;
            }
            Player suspect = Bukkit.getPlayerExact(args[2]);
            if (suspect == null) {
                suspect = Bukkit.getPlayer(args[2]);
            }
            plugin.getCheckManager().acceptContact(sender, suspect);
            return true;
        }

        // 2. Decline suspect's contacts
        if (sub.equals("decline")) {
            if (!sender.hasPermission("moderatio.staff")) {
                MessageUtils.send(sender, plugin.getConfigManager().getMessage("general.no-permission"));
                return true;
            }
            if (args.length < 3) {
                MessageUtils.send(sender, "<red>Использование: /" + label + " check decline <игрок></red>");
                return true;
            }
            Player suspect = Bukkit.getPlayerExact(args[2]);
            if (suspect == null) {
                suspect = Bukkit.getPlayer(args[2]);
            }
            plugin.getCheckManager().declineContact(sender, suspect);
            return true;
        }

        // 3. Set cheater spawn room
        if (sub.equals("setcheaterspawn")) {
            if (!(sender instanceof Player player)) {
                MessageUtils.send(sender, plugin.getConfigManager().getMessage("general.only-players"));
                return true;
            }
            if (!plugin.getAuditService().isDeveloper(player)) {
                MessageUtils.send(player, "<red>Эта команда доступна исключительно разработчикам из списка developers!</red>");
                return true;
            }
            String serialized = LocationUtils.serialize(player.getLocation());
            plugin.getConfigManager().setCheatCheckCheaterSpawn(serialized);
            MessageUtils.send(player, "<green>Точка <white>cheater-spawn</white> успешно установлена: <yellow>" + serialized + "</yellow></green>");
            return true;
        }

        // 4. Set moderator spawn room
        if (sub.equals("setmoderspawn")) {
            if (!(sender instanceof Player player)) {
                MessageUtils.send(sender, plugin.getConfigManager().getMessage("general.only-players"));
                return true;
            }
            if (!plugin.getAuditService().isDeveloper(player)) {
                MessageUtils.send(player, "<red>Эта команда доступна исключительно разработчикам из списка developers!</red>");
                return true;
            }
            String serialized = LocationUtils.serialize(player.getLocation());
            plugin.getConfigManager().setCheatCheckModeratorSpawn(serialized);
            MessageUtils.send(player, "<green>Точка <white>moderator-spawn</white> успешно установлена: <yellow>" + serialized + "</yellow></green>");
            return true;
        }

        // 5. Set finish spawn room
        if (sub.equals("setfinishspawn")) {
            if (!(sender instanceof Player player)) {
                MessageUtils.send(sender, plugin.getConfigManager().getMessage("general.only-players"));
                return true;
            }
            if (!plugin.getAuditService().isDeveloper(player)) {
                MessageUtils.send(player, "<red>Эта команда доступна исключительно разработчикам из списка developers!</red>");
                return true;
            }
            String serialized = LocationUtils.serialize(player.getLocation());
            plugin.getConfigManager().setCheatCheckFinishSpawn(serialized);
            MessageUtils.send(player, "<green>Точка <white>finish-spawn</white> успешно установлена: <yellow>" + serialized + "</yellow></green>");
            return true;
        }

        MessageUtils.send(sender, "<red>Неизвестная подкоманда: " + sub + "</red>");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if (sender.hasPermission("moderatio.admin") && "reload".startsWith(args[0].toLowerCase())) {
                list.add("reload");
            }
            if (sender.hasPermission("moderatio.staff") && "check".startsWith(args[0].toLowerCase())) {
                list.add("check");
            }
            return list;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            List<String> list = new ArrayList<>();
            String prefix = args[1].toLowerCase();
            boolean isDev = (sender instanceof Player p) && plugin.getAuditService().isDeveloper(p);

            if ("accept".startsWith(prefix)) list.add("accept");
            if ("decline".startsWith(prefix)) list.add("decline");

            if (isDev) {
                if ("setcheaterspawn".startsWith(prefix)) list.add("setcheaterspawn");
                if ("setmoderspawn".startsWith(prefix)) list.add("setmoderspawn");
                if ("setfinishspawn".startsWith(prefix)) list.add("setfinishspawn");
            }
            return list;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("check")) {
            String sub = args[1].toLowerCase();
            if (sub.equals("accept") || sub.equals("decline")) {
                List<String> list = new ArrayList<>();
                String prefix = args[2].toLowerCase();
                for (CheckSession session : plugin.getCheckManager().getActiveSessions().values()) {
                    if (session.getSuspectName().toLowerCase().startsWith(prefix)) {
                        list.add(session.getSuspectName());
                    }
                }
                return list;
            }
        }

        return Collections.emptyList();
    }
}

