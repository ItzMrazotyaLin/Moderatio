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
import java.util.Map;
import java.util.UUID;

/**
 * Command executor and tab completer for /unwarn.
 */
public class UnwarnCommand implements CommandExecutor, TabCompleter {

    private final Moderatio plugin;
    private static final UUID CONSOLE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final List<String> VALID_REASONS = List.of(
            "MISCLICK",
            "APPEAL_ACCEPTED",
            "FALSE_POSITIVE",
            "REISSUED"
    );

    public UnwarnCommand(Moderatio plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("moderatio.punish.unwarn")) {
            MessageUtils.send(sender, plugin.getConfigManager().getMessage("general.no-permission"));
            return true;
        }

        if (args.length < 2) {
            MessageUtils.send(sender, "<red>Использование: /" + label + " <игрок> <MISCLICK | APPEAL_ACCEPTED | FALSE_POSITIVE | REISSUED></red>");
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

        Punishment latestWarn = plugin.getPunishmentManager().getLatestActiveWarn(targetUuid);
        if (latestWarn == null) {
            MessageUtils.send(sender, "<red>У игрока " + targetName + " нет активных предупреждений!</red>");
            return true;
        }

        // Author check
        if (sender instanceof Player player) {
            boolean isDev = plugin.getAuditService().isDeveloper(player);
            if (!isDev && !player.getUniqueId().equals(latestWarn.getStaffUuid())) {
                MessageUtils.send(sender, "<red>Вы не можете снять это наказание, так как оно было выдано другим модератором (" + latestWarn.getStaffName() + ")!</red>");
                return true;
            }
        }

        String rawReason = args[1].toUpperCase();
        String validReason;
        String desc;
        switch (rawReason) {
            case "MISCLICK", "МИССКЛИК", "ОШИБКА" -> {
                validReason = "MISCLICK";
                desc = "Миссклик / Ошибка модератора";
            }
            case "APPEAL_ACCEPTED", "АПЕЛЛЯЦИЯ", "APPEAL" -> {
                validReason = "APPEAL_ACCEPTED";
                desc = "Апелляция принята";
            }
            case "FALSE_POSITIVE", "ЛОЖНОЕ", "FALSE" -> {
                validReason = "FALSE_POSITIVE";
                desc = "Ложное срабатывание / Ситуация решена";
            }
            case "REISSUED", "ПЕРЕВЫДАЧА" -> {
                validReason = "REISSUED";
                desc = "Перевыдача наказания";
            }
            default -> {
                MessageUtils.send(sender, "<red>Недопустимая причина снятия! Выберите из списка: <yellow>MISCLICK, APPEAL_ACCEPTED, FALSE_POSITIVE, REISSUED</yellow></red>");
                return true;
            }
        }

        UUID staffUuid = (sender instanceof Player p) ? p.getUniqueId() : CONSOLE_UUID;
        String staffName = sender.getName();

        plugin.getPunishmentManager().unwarn(targetUuid, staffUuid, staffName, validReason);

        MessageUtils.send(sender, "<green>Последнее предупреждение с игрока <white>" + targetName + "</white> успешно снято! <gray>Причина: <yellow>" + desc + "</yellow></gray></green>");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("moderatio.punish.unwarn")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> list = new ArrayList<>();
            Map<UUID, List<Punishment>> warnsMap = plugin.getPunishmentManager().getActiveWarns();
            for (Map.Entry<UUID, List<Punishment>> entry : warnsMap.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    String name = entry.getValue().get(0).getTargetName();
                    if (name != null && name.toLowerCase().startsWith(prefix)) {
                        list.add(name);
                    }
                }
            }
            if (list.isEmpty()) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(prefix)) {
                        list.add(p.getName());
                    }
                }
            }
            return list;
        }

        if (args.length == 2) {
            String prefix = args[1].toUpperCase();
            List<String> list = new ArrayList<>();
            for (String r : VALID_REASONS) {
                if (r.startsWith(prefix)) {
                    list.add(r);
                }
            }
            return list;
        }

        return Collections.emptyList();
    }
}
