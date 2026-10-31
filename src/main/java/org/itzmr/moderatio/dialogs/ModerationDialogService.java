package org.itzmr.moderatio.dialogs;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.itzmr.moderatio.database.Punishment;
import org.itzmr.moderatio.database.PunishmentType;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.util.Vector;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.audit.StaffActionEntry;
import org.itzmr.moderatio.staff.StaffManager;
import org.itzmr.moderatio.utils.MessageUtils;
import org.itzmr.moderatio.utils.TimeParser;
import org.itzmr.moderatio.utils.TimeUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for constructing and presenting moderation modal dialogs via Paper/Purpur Dialog API.
 */
public class ModerationDialogService {

    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
            .lifetime(Duration.ofMinutes(10))
            .uses(ClickCallback.UNLIMITED_USES)
            .build();

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)\\s*(d|h|m|s|д|ч|мин|сек)?", Pattern.CASE_INSENSITIVE);

    private final Moderatio plugin;
    private final StaffManager staffManager;

    public ModerationDialogService(Moderatio plugin) {
        this.plugin = plugin;
        this.staffManager = plugin.getStaffManager();
    }

    /**
     * Opens the general staff mode menu dialog (opened via SHIFT + RMB).
     */
    public void openStaffMenuDialog(Player moderator) {
        if (!moderator.isOnline() || !staffManager.isInStaffMode(moderator)) {
            return;
        }

        boolean isVanished = plugin.getVanishManager().isVanished(moderator);
        String vanishStatus = isVanished ? "<green>ВКЛ</green>" : "<red>ВЫКЛ</red>";

        Component title = MessageUtils.parse("<gradient:aqua:blue><bold>Меню персонала (Moderatio)</bold></gradient>");
        Component body = MessageUtils.parse(
                "<gray>Текущий мир: <white>" + moderator.getWorld().getName() + "</white> | " +
                "X: <white>" + moderator.getLocation().getBlockX() + "</white> " +
                "Y: <white>" + moderator.getLocation().getBlockY() + "</white> " +
                "Z: <white>" + moderator.getLocation().getBlockZ() + "</white></gray>\n" +
                "<gray>Статус скрытия (Vanish): " + vanishStatus + "</gray>"
        );

        int buttonWidth = plugin.getConfig().getInt("dialog.button-width", 220);
        List<ActionButton> actions = new ArrayList<>();

        // 1. Vanish Toggle Button
        Component vanishLabel = MessageUtils.parse(isVanished ? "<green>👻 Режим скрытия: ВКЛ</green>" : "<gray>👻 Режим скрытия: ВЫКЛ</gray>");
        actions.add(ActionButton.builder(vanishLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        plugin.getVanishManager().toggleVanish(p);
                        openStaffMenuDialog(p);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // 2. My Punishments Button
        Component myPunishmentsLabel = MessageUtils.parse("<gold>📖 Мои наказания</gold>");
        actions.add(ActionButton.builder(myPunishmentsLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        openMyPunishmentsGui(p, 0);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // 3. Exit Staff Mode Button
        Component exitStaffLabel = MessageUtils.parse("<red>🚪 Выйти из Staff Mode</red>");
        actions.add(ActionButton.builder(exitStaffLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        staffManager.disableStaffMode(p, true);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // 4. Developers Extra Buttons
        if (plugin.getAuditService().isDeveloper(moderator)) {
            Component auditLabel = MessageUtils.parse("<aqua>📋 Журнал аудита персонала</aqua>");
            actions.add(ActionButton.builder(auditLabel)
                    .width(buttonWidth)
                    .action(DialogAction.customClick((resp, aud) -> {
                        if (aud instanceof Player p) {
                            openStaffAuditListGui(p, 0);
                        }
                    }, CALLBACK_OPTIONS))
                    .build());

            Component spawnsLabel = MessageUtils.parse("<light_purple>🧭 Спавны проверки</light_purple>");
            actions.add(ActionButton.builder(spawnsLabel)
                    .width(buttonWidth)
                    .action(DialogAction.customClick((resp, aud) -> {
                        if (aud instanceof Player p) {
                            openSpawnSettingsDialog(p);
                        }
                    }, CALLBACK_OPTIONS))
                    .build());
        }

        // Close Button (exitAction)
        ActionButton exitAction = ActionButton.builder(MessageUtils.parse("<gray>Закрыть</gray>"))
                .width(buttonWidth)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .build())
                .type(DialogType.multiAction(actions, exitAction, 1)));

        moderator.showDialog(dialog);
    }

    /**
     * Opens the staff members audit dialog (developers only).
     */
    public void openStaffAuditDialog(Player developer) {
        if (!developer.isOnline() || !plugin.getAuditService().isDeveloper(developer)) {
            return;
        }

        String rawTitle = plugin.getConfigManager().getMessage("audit.title", false);
        if (rawTitle.contains("Missing message")) {
            rawTitle = "<gradient:#38ef7d:#11998e>Аудит состава персонала</gradient>";
        }
        Component title = MessageUtils.parse(rawTitle);

        int buttonWidth = plugin.getConfig().getInt("dialog.button-width", 220);
        List<ActionButton> actions = new ArrayList<>();

        Set<String> staffNames = plugin.getAuditService().getAllKnownStaff();
        StringBuilder bodyBuilder = new StringBuilder("<gray>Выберите модератора для просмотра последних 10 действий:</gray>\n");

        for (String staffName : staffNames) {
            Player staffPlayer = Bukkit.getPlayerExact(staffName);
            boolean isOnline = staffPlayer != null && staffPlayer.isOnline();
            boolean inStaffMode = staffPlayer != null && staffManager.isInStaffMode(staffPlayer);

            String statusTag = isOnline
                    ? (inStaffMode ? "<green>[ONLINE | STAFF MODE]</green>" : "<yellow>[ONLINE]</yellow>")
                    : "<dark_gray>[OFFLINE]</dark_gray>";

            bodyBuilder.append("\n<white>• ").append(staffName).append("</white> ").append(statusTag);

            String btnText = (isOnline ? "<green>👤 " : "<gray>👤 ") + staffName;
            actions.add(ActionButton.builder(MessageUtils.parse(btnText))
                    .width(buttonWidth)
                    .action(DialogAction.customClick((resp, aud) -> {
                        if (aud instanceof Player p) {
                            openModeratorHistoryDialog(p, staffName);
                        }
                    }, CALLBACK_OPTIONS))
                    .build());
        }

        if (staffNames.isEmpty()) {
            bodyBuilder.append("\n<dark_gray>Список персонала пуст.</dark_gray>");
        }

        Component body = MessageUtils.parse(bodyBuilder.toString());

        // Back button
        Component backLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-back", false));
        ActionButton exitAction = ActionButton.builder(backLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        openStaffMenuDialog(p);
                    }
                }, CALLBACK_OPTIONS))
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .build())
                .type(DialogType.multiAction(actions, exitAction, 1)));

        developer.showDialog(dialog);
    }

    /**
     * Opens the recent actions history dialog for a specific staff member.
     */
    public void openModeratorHistoryDialog(Player developer, String staffName) {
        if (!developer.isOnline() || !plugin.getAuditService().isDeveloper(developer)) {
            return;
        }

        Component title = MessageUtils.parse("<gold>История действий: <yellow>" + staffName + "</yellow></gold>");
        int buttonWidth = plugin.getConfig().getInt("dialog.button-width", 220);

        List<StaffActionEntry> entries = plugin.getAuditService().getRecentActions(staffName);
        StringBuilder sb = new StringBuilder();
        if (entries.isEmpty()) {
            sb.append("<gray>У модератора <white>").append(staffName).append("</white> нет недавних зафиксированных действий.</gray>");
        } else {
            sb.append("<gray>Последние ").append(entries.size()).append(" действий модератора:</gray>\n\n");
            for (StaffActionEntry entry : entries) {
                sb.append(entry.toDisplayFormat()).append("\n");
            }
        }

        Component body = MessageUtils.parse(sb.toString());

        Component backLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-back", false));
        ActionButton exitAction = ActionButton.builder(backLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        openStaffAuditDialog(p);
                    }
                }, CALLBACK_OPTIONS))
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .build())
                .type(DialogType.multiAction(Collections.emptyList(), exitAction, 1)));

        developer.showDialog(dialog);
    }

    /**
     * Opens the main moderation dialog for the target player.
     */
    public void openMainDialog(Player moderator, Player target) {
        if (!target.isOnline()) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        Map<String, String> placeholders = createPlayerPlaceholders(target);

        Component title = MessageUtils.parse("<red><bold>Управление игроком: " + target.getName() + "</bold></red>");
        String rawBody = plugin.getConfigManager().getMessage("dialog.main-body", placeholders, false);
        if (plugin.getCheckManager().isUnderCheck(target.getUniqueId()) && !rawBody.contains("НА ПРОВЕРКЕ")) {
            rawBody = "<red><bold>СТАТУС: НА ПРОВЕРКЕ</bold></red>\n\n" + rawBody;
        }
        Component body = MessageUtils.parse(rawBody);

        int buttonWidth = plugin.getConfig().getInt("dialog.button-width", 220);
        List<ActionButton> actions = new ArrayList<>();

        // 1. Ban Button
        actions.add(ActionButton.builder(MessageUtils.parse("<red>🔨 Заблокировать (Бан)</red>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        if (staffManager.isPunishmentBlocked(p)) {
                            sendPunishmentBlockedMessage(p);
                            return;
                        }
                        openBanTemplatesDialog(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // 2. Mute Button
        actions.add(ActionButton.builder(MessageUtils.parse("<gold>🔇 Замутить</gold>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        if (staffManager.isPunishmentBlocked(p)) {
                            sendPunishmentBlockedMessage(p);
                            return;
                        }
                        openMuteTemplatesDialog(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // 3. Warn Button
        actions.add(ActionButton.builder(MessageUtils.parse("<yellow>📜 Выдать варн</yellow>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        if (staffManager.isPunishmentBlocked(p)) {
                            sendPunishmentBlockedMessage(p);
                            return;
                        }
                        openWarnTemplatesDialog(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        boolean isCheckingThisTarget = plugin.getCheckManager().isCheckingTarget(moderator, target);

        // 4. Cheat Check / Finish Check Button
        if (plugin.getConfigManager().isCheatCheckEnabled()) {
            if (isCheckingThisTarget) {
                // Suspicious target is already under check by this moderator -> show "Снять с проверки"
                actions.add(ActionButton.builder(MessageUtils.parse("<green><bold>Снять с проверки</bold></green>"))
                        .width(buttonWidth)
                        .action(DialogAction.customClick((resp, aud) -> {
                            if (aud instanceof Player p) {
                                plugin.getCheckManager().finishCheck(p, target);
                                openMainDialog(p, target);
                            }
                        }, CALLBACK_OPTIONS))
                        .build());
            } else {
                // Not yet checking -> show "Проверка на читы"
                actions.add(ActionButton.builder(MessageUtils.parse("<yellow><bold>Проверка на читы</bold></yellow>"))
                        .width(buttonWidth)
                        .action(DialogAction.customClick((resp, aud) -> {
                            if (aud instanceof Player p) {
                                plugin.getCheckManager().startCheck(p, target);
                                openMainDialog(p, target);
                            }
                        }, CALLBACK_OPTIONS))
                        .build());
            }
        }

        // 5. Target Punishments History Button
        actions.add(ActionButton.builder(MessageUtils.parse("<aqua>📋 История наказаний</aqua>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        openTargetHistoryGui(p, target.getUniqueId(), target.getName(), 0);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // 6. Trolling Button (Exclusive to Developers - hidden if actively checking this target)
        if (!isCheckingThisTarget && staffManager.canTroll(moderator)) {
            Component trollLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-troll", false));
            actions.add(ActionButton.builder(trollLabel)
                    .width(buttonWidth)
                    .action(DialogAction.customClick((resp, aud) -> {
                        if (aud instanceof Player p) {
                            openTrollingDialog(p, target);
                        }
                    }, CALLBACK_OPTIONS))
                    .build());
        }

        // Exit / Close button
        ActionButton exitAction = ActionButton.builder(MessageUtils.parse("<gray>Закрыть</gray>"))
                .width(buttonWidth)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .build())
                .type(DialogType.multiAction(actions, exitAction, 1)));

        moderator.showDialog(dialog);
    }

    /**
     * Opens the ban template selection dialog.
     */
    public void openBanTemplatesDialog(Player moderator, Player target) {
        if (!target.isOnline()) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        Map<String, String> placeholders = createPlayerPlaceholders(target);

        Component title = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.ban-title", placeholders, false));
        Component body = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.ban-body", placeholders, false));

        int buttonWidth = plugin.getConfig().getInt("dialog.button-width", 220);
        List<ActionButton> actions = new ArrayList<>();

        // Load templates from config
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("ban-templates");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String btnTitle = section.getString(key + ".title", key);
                String duration = section.getString(key + ".duration", "7d");
                String reason = section.getString(key + ".reason", "Нарушение правил");

                Component label = MessageUtils.parse(btnTitle + " <gray>(" + duration + ")</gray>");
                actions.add(ActionButton.builder(label)
                        .width(buttonWidth)
                        .action(DialogAction.customClick((resp, aud) -> {
                            if (aud instanceof Player p) {
                                executeBan(p, target, duration, reason);
                            }
                        }, CALLBACK_OPTIONS))
                        .build());
            }
        }

        // Custom reason/duration button
        Component customLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-custom-ban", false));
        actions.add(ActionButton.builder(customLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        openCustomBanDialog(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // Back button
        Component backLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-back", false));
        ActionButton exitAction = ActionButton.builder(backLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        openMainDialog(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .build())
                .type(DialogType.multiAction(actions, exitAction, 1)));

        moderator.showDialog(dialog);
    }

    /**
     * Opens modal dialog with input fields for custom ban reason and duration.
     */
    public void openCustomBanDialog(Player moderator, Player target) {
        if (!target.isOnline()) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        Map<String, String> placeholders = createPlayerPlaceholders(target);

        Component title = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.custom-ban-title", placeholders, false));
        int buttonWidth = plugin.getConfig().getInt("dialog.button-width", 220);

        Component reasonLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.input-reason-label", false));
        Component durationLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.input-duration-label", false));

        TextDialogInput reasonInput = DialogInput.text("reason", reasonLabel)
                .width(buttonWidth)
                .labelVisible(true)
                .initial("Нарушение правил")
                .maxLength(128)
                .build();

        TextDialogInput durationInput = DialogInput.text("duration", durationLabel)
                .width(buttonWidth)
                .labelVisible(true)
                .initial("7d")
                .maxLength(32)
                .build();

        Component applyLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-apply-ban", false));
        ActionButton applyButton = ActionButton.builder(applyLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        String inputReason = resp.getText("reason");
                        String inputDuration = resp.getText("duration");

                        String finalReason = (inputReason != null && !inputReason.trim().isEmpty())
                                ? inputReason.trim()
                                : "Нарушение правил";
                        String finalDuration = (inputDuration != null && !inputDuration.trim().isEmpty())
                                ? inputDuration.trim()
                                : "7d";

                        executeBan(p, target, finalDuration, finalReason);
                    }
                }, CALLBACK_OPTIONS))
                .build();

        Component backLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-back", false));
        ActionButton exitAction = ActionButton.builder(backLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        openBanTemplatesDialog(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .inputs(List.of(reasonInput, durationInput))
                        .build())
                .type(DialogType.multiAction(List.of(applyButton), exitAction, 1)));

        moderator.showDialog(dialog);
    }

    /**
     * Opens the mute template selection dialog.
     */
    public void openMuteTemplatesDialog(Player moderator, Player target) {
        if (!target.isOnline()) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        Map<String, String> placeholders = createPlayerPlaceholders(target);

        Component title = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.mute-title", placeholders, false));
        Component body = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.mute-body", placeholders, false));

        int buttonWidth = plugin.getConfig().getInt("dialog.button-width", 220);
        List<ActionButton> actions = new ArrayList<>();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("mute-templates");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String btnTitle = section.getString(key + ".title", key);
                String duration = section.getString(key + ".duration", "1h");
                String reason = section.getString(key + ".reason", "Нарушение правил чата");
                String customCommand = section.getString(key + ".command");

                Component label = MessageUtils.parse(btnTitle + " <gray>(" + duration + ")</gray>");
                actions.add(ActionButton.builder(label)
                        .width(buttonWidth)
                        .action(DialogAction.customClick((resp, aud) -> {
                            if (aud instanceof Player p) {
                                executeMute(p, target, customCommand, duration, reason);
                            }
                        }, CALLBACK_OPTIONS))
                        .build());
            }
        }

        Component backLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-back", false));
        ActionButton exitAction = ActionButton.builder(backLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        openMainDialog(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .build())
                .type(DialogType.multiAction(actions, exitAction, 1)));

        moderator.showDialog(dialog);
    }

    /**
     * Opens the categorized warn selection chest GUI (Zone CHAT, Zone GAME, GRAY_STAINED_GLASS_PANE divider).
     */
    public void openWarnTemplatesDialog(Player moderator, Player target) {
        if (!target.isOnline()) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        Component title = MessageUtils.parse("<gold><bold>Выдача предупреждения: " + target.getName() + "</bold></gold>");
        Component body = MessageUtils.parse("<gray>Выберите категорию и причину нарушения:</gray>");

        int buttonWidth = plugin.getConfig().getInt("dialog.button-width", 220);
        List<ActionButton> actions = new ArrayList<>();

        // Category CHAT Buttons
        actions.add(ActionButton.builder(MessageUtils.parse("<yellow>💬 Чат: Спам / Флуд</yellow>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        executeWarn(p, target, "CHAT", "Спам / Флуд");
                    }
                }, CALLBACK_OPTIONS))
                .build());

        actions.add(ActionButton.builder(MessageUtils.parse("<red>💬 Чат: Оскорбления</red>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        executeWarn(p, target, "CHAT", "Оскорбления");
                    }
                }, CALLBACK_OPTIONS))
                .build());

        actions.add(ActionButton.builder(MessageUtils.parse("<gold>💬 Чат: Ненормативная лексика</gold>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        executeWarn(p, target, "CHAT", "Ненормативная лексика");
                    }
                }, CALLBACK_OPTIONS))
                .build());

        actions.add(ActionButton.builder(MessageUtils.parse("<yellow>💬 Чат: Капс</yellow>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        executeWarn(p, target, "CHAT", "Капс");
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // Category GAME Buttons
        actions.add(ActionButton.builder(MessageUtils.parse("<dark_red>⚔ Игра: Помеха игровому процессу</dark_red>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        executeWarn(p, target, "GAME", "Помеха игровому процессу");
                    }
                }, CALLBACK_OPTIONS))
                .build());

        actions.add(ActionButton.builder(MessageUtils.parse("<dark_red>⚔ Игра: Тимкилл / Гриферство</dark_red>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        executeWarn(p, target, "GAME", "Тимкилл / Гриферство");
                    }
                }, CALLBACK_OPTIONS))
                .build());

        actions.add(ActionButton.builder(MessageUtils.parse("<gold>⚔ Игра: Багоюз</gold>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        executeWarn(p, target, "GAME", "Багоюз");
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // Only for developers: Custom reason
        if (plugin.getAuditService().isDeveloper(moderator)) {
            actions.add(ActionButton.builder(MessageUtils.parse("<aqua>✍ Своя причина</aqua>"))
                    .width(buttonWidth)
                    .action(DialogAction.customClick((resp, aud) -> {
                        if (aud instanceof Player p) {
                            openCustomWarnDialog(p, target);
                        }
                    }, CALLBACK_OPTIONS))
                    .build());
        }

        // Cancel / Back Button (returns to target main dialog)
        ActionButton exitAction = ActionButton.builder(MessageUtils.parse("<gray>Отмена</gray>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        openMainDialog(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .build())
                .type(DialogType.multiAction(actions, exitAction, 1)));

        moderator.showDialog(dialog);
    }

    public ItemStack createGuiItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Opens modal dialog with input field for custom warn reason.
     */
    public void openCustomWarnDialog(Player moderator, Player target) {
        if (!plugin.getAuditService().isDeveloper(moderator)) {
            MessageUtils.send(moderator, "<red>Ввод кастомной причины варна доступен исключительно разработчикам!</red>");
            return;
        }

        if (!target.isOnline()) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        Component title = MessageUtils.parse("<gold>Своё предупреждение: " + target.getName() + "</gold>");
        int buttonWidth = plugin.getConfig().getInt("dialog.button-width", 220);

        Component reasonLabel = MessageUtils.parse("<gray>Причина предупреждения:</gray>");
        TextDialogInput reasonInput = DialogInput.text("reason", reasonLabel)
                .width(buttonWidth)
                .labelVisible(true)
                .initial("Нарушение правил")
                .maxLength(128)
                .build();

        Component applyLabel = MessageUtils.parse("<green>Подтвердить и выдать варн</green>");
        ActionButton applyButton = ActionButton.builder(applyLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        String inputReason = resp.getText("reason");
                        String finalReason = (inputReason != null && !inputReason.trim().isEmpty())
                                ? inputReason.trim()
                                : "Нарушение правил";
                        executeWarn(p, target, finalReason);
                    }
                }, CALLBACK_OPTIONS))
                .build();

        Component backLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-back", false));
        ActionButton exitAction = ActionButton.builder(backLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        openWarnTemplatesDialog(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .inputs(List.of(reasonInput))
                        .build())
                .type(DialogType.multiAction(List.of(applyButton), exitAction, 1)));

        moderator.showDialog(dialog);
    }

    /**
     * Opens the trolling and quick action dialog.
     */
    public void openTrollingDialog(Player moderator, Player target) {
        if (!staffManager.canTroll(moderator)) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("punishment.no-troll-permission"));
            return;
        }

        if (!target.isOnline()) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        Map<String, String> placeholders = createPlayerPlaceholders(target);

        Component title = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.troll-title", placeholders, false));
        Component body = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.troll-body", placeholders, false));

        int buttonWidth = plugin.getConfig().getInt("dialog.button-width", 220);
        List<ActionButton> actions = new ArrayList<>();

        // 1. Launch with fall damage
        Component launchDamageLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-troll-launch-damage", false));
        actions.add(ActionButton.builder(launchDamageLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        launchPlayerWithDamage(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // 2. Launch safe (no fall damage)
        Component launchSafeLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-troll-launch-safe", false));
        actions.add(ActionButton.builder(launchSafeLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        launchPlayerSafe(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // 2. Freeze toggle
        Component freezeLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-troll-freeze", false));
        actions.add(ActionButton.builder(freezeLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        toggleFreezePlayer(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // 3. Fake explosion
        Component explosionLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-troll-explosion", false));
        actions.add(ActionButton.builder(explosionLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        fakeExplosion(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // 4. Fake lightning
        Component lightningLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-troll-lightning", false));
        actions.add(ActionButton.builder(lightningLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        fakeLightning(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        Component backLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-back", false));
        ActionButton exitAction = ActionButton.builder(backLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        openMainDialog(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .build())
                .type(DialogType.multiAction(actions, exitAction, 1)));

        moderator.showDialog(dialog);
    }

    /**
     * Checks if a duration string indicates a permanent punishment.
     */
    public boolean isPermanentDuration(String input) {
        if (input == null || input.trim().isEmpty()) {
            return true;
        }
        String clean = input.trim().toLowerCase();
        return clean.equals("perm") || clean.equals("permanent") || clean.equals("forever") || clean.equals("0") || clean.equals("навсегда");
    }

    /**
     * Executes ban logic for target player.
     */
    public void executeBan(Player moderator, Player target, String durationStr, String reason) {
        if (!target.isOnline()) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        if (staffManager.isPunishmentBlocked(moderator)) {
            sendPunishmentBlockedMessage(moderator);
            return;
        }

        long durationMillis;
        try {
            durationMillis = TimeParser.parseDuration(durationStr);
        } catch (IllegalArgumentException e) {
            MessageUtils.send(moderator, "<red>" + e.getMessage() + "</red>");
            return;
        }

        plugin.getPunishmentManager().ban(target.getUniqueId(), target.getName(), moderator.getUniqueId(), moderator.getName(), reason, durationMillis);
        plugin.getAuditService().checkPunishmentAbuse(moderator);

        String durDisplay = (durationMillis <= 0) ? "Навсегда" : TimeUtils.formatDuration(durationMillis);
        Map<String, String> placeholders = Map.of(
                "target", target.getName(),
                "duration", durDisplay,
                "reason", reason,
                "moderator", moderator.getName()
        );
        MessageUtils.send(moderator, plugin.getConfigManager().getMessage("punishment.banned-success", placeholders));
    }

    /**
     * Executes mute logic for target player.
     */
    public void executeMute(Player moderator, Player target, String duration, String reason) {
        executeMute(moderator, target, null, duration, reason);
    }

    /**
     * Executes mute logic for target player.
     */
    public void executeMute(Player moderator, Player target, String commandTemplate, String duration, String reason) {
        if (!target.isOnline()) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        if (staffManager.isPunishmentBlocked(moderator)) {
            sendPunishmentBlockedMessage(moderator);
            return;
        }

        long durationMillis;
        try {
            durationMillis = TimeParser.parseDuration(duration);
        } catch (IllegalArgumentException e) {
            MessageUtils.send(moderator, "<red>" + e.getMessage() + "</red>");
            return;
        }

        plugin.getPunishmentManager().mute(target.getUniqueId(), target.getName(), moderator.getUniqueId(), moderator.getName(), reason, durationMillis);
        plugin.getAuditService().checkPunishmentAbuse(moderator);

        String durDisplay = (durationMillis <= 0) ? "Навсегда" : TimeUtils.formatDuration(durationMillis);
        Map<String, String> placeholders = Map.of(
                "target", target.getName(),
                "duration", durDisplay,
                "reason", reason,
                "moderator", moderator.getName()
        );
        MessageUtils.send(moderator, plugin.getConfigManager().getMessage("punishment.muted-success", placeholders));
    }

    /**
     * Executes warn logic for target player.
     */
    public void executeWarn(Player moderator, Player target, String reason) {
        executeWarn(moderator, target, "CHAT", reason);
    }

    /**
     * Executes warn logic for target player with category.
     */
    public void executeWarn(Player moderator, Player target, String category, String reason) {
        if (!target.isOnline()) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        if (staffManager.isPunishmentBlocked(moderator)) {
            sendPunishmentBlockedMessage(moderator);
            return;
        }

        String cat = (category != null && category.equalsIgnoreCase("GAME")) ? "GAME" : "CHAT";
        String finalReason = (reason != null && !reason.isBlank()) ? reason : "Предупреждение";
        plugin.getPunishmentManager().warn(target.getUniqueId(), target.getName(), moderator.getUniqueId(), moderator.getName(), cat, finalReason);
        plugin.getAuditService().checkPunishmentAbuse(moderator);

        MessageUtils.send(moderator, "<green>Предупреждение игроку <white>" + target.getName() + "</white> успешно выдано! <gray>[<yellow>" + cat + "</yellow>] Причина: <white>" + finalReason + "</white></gray></green>");
    }

    /**
     * Executes external cheat check command for target player.
     */
    public void executeCheatCheck(Player moderator, Player target) {
        plugin.getCheckManager().startCheck(moderator, target);
    }

    /**
     * Launches the target player upward with fall damage enabled.
     */
    public void launchPlayerWithDamage(Player moderator, Player target) {
        if (!target.isOnline()) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (target.isOnline()) {
                target.setVelocity(new Vector(0, 1.5, 0));
                target.playSound(target.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
            }
        });
        plugin.getAuditService().logAction(moderator, "TROLL_LAUNCH", target.getName(), "With Fall Damage");
        Map<String, String> placeholders = Map.of("target", target.getName());
        MessageUtils.send(moderator, plugin.getConfigManager().getMessage("punishment.troll-launch-damage", placeholders));
    }

    /**
     * Launches the target player upward safely (negating the subsequent fall damage).
     */
    public void launchPlayerSafe(Player moderator, Player target) {
        if (!target.isOnline()) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        staffManager.addNoFallPlayer(target.getUniqueId());

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (target.isOnline()) {
                target.setVelocity(new Vector(0, 1.5, 0));
                target.playSound(target.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
            }
        });
        plugin.getAuditService().logAction(moderator, "TROLL_LAUNCH", target.getName(), "Safe / No Damage");
        Map<String, String> placeholders = Map.of("target", target.getName());
        MessageUtils.send(moderator, plugin.getConfigManager().getMessage("punishment.troll-launch-safe", placeholders));
    }

    public void launchPlayer(Player moderator, Player target) {
        launchPlayerWithDamage(moderator, target);
    }

    /**
     * Toggles frozen state of target player.
     */
    public void toggleFreezePlayer(Player moderator, Player target) {
        boolean frozen = staffManager.toggleFreeze(target);
        plugin.getAuditService().logAction(moderator, "TROLL_FREEZE", target.getName(), frozen ? "Freeze ON" : "Freeze OFF");
        Map<String, String> placeholders = Map.of("target", target.getName());
        if (frozen) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("punishment.troll-freeze-on", placeholders));
        } else {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("punishment.troll-freeze-off", placeholders));
        }
    }

    /**
     * Creates a fake safe explosion at the player's position.
     */
    public void fakeExplosion(Player moderator, Player target) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (target.isOnline()) {
                var loc = target.getLocation();
                target.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
                target.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            }
        });
        plugin.getAuditService().logAction(moderator, "TROLL_EXPLOSION", target.getName(), null);
        Map<String, String> placeholders = Map.of("target", target.getName());
        MessageUtils.send(moderator, plugin.getConfigManager().getMessage("punishment.troll-explosion", placeholders));
    }

    /**
     * Strikes fake visual lightning without damage at target player.
     */
    public void fakeLightning(Player moderator, Player target) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (target.isOnline()) {
                target.getWorld().strikeLightningEffect(target.getLocation());
            }
        });
        plugin.getAuditService().logAction(moderator, "TROLL_LIGHTNING", target.getName(), null);
        Map<String, String> placeholders = Map.of("target", target.getName());
        MessageUtils.send(moderator, plugin.getConfigManager().getMessage("punishment.troll-lightning", placeholders));
    }

    private void sendPunishmentBlockedMessage(Player moderator) {
        int limit = plugin.getConfigManager().getAntiAbusePunishmentLimit();
        int interval = plugin.getConfigManager().getAntiAbuseCheckIntervalSeconds();
        String blockedMsg = plugin.getConfigManager().getMessage("punishment.limit-reached",
                Map.of("limit", String.valueOf(limit), "interval", String.valueOf(interval)), false);
        if (blockedMsg.contains("Missing message")) {
            blockedMsg = "<red>Вы исчерпали лимит наказаний (" + limit + " за " + interval + "с). Действие заблокировано!</red>";
        }
        MessageUtils.send(moderator, blockedMsg);
    }

    /**
     * Creates placeholder map for player details.
     */
    private Map<String, String> createPlayerPlaceholders(Player player) {
        Map<String, String> map = new HashMap<>();
        boolean underCheck = plugin.getCheckManager().isUnderCheck(player.getUniqueId());
        map.put("target", player.getName());
        map.put("ping", String.valueOf(player.getPing()));
        map.put("health", String.valueOf((int) Math.ceil(player.getHealth())));
        map.put("max_health", String.valueOf((int) Math.ceil(player.getMaxHealth())));
        map.put("gamemode", player.getGameMode().name());
        map.put("world", player.getWorld().getName());
        map.put("check_status", underCheck ? "<red><bold>СТАТУС: НА ПРОВЕРКЕ</bold></red>\n" : "");
        map.put("status", underCheck ? "<red><bold>НА ПРОВЕРКЕ</bold></red>" : "<green>Обычный</green>");
        return map;
    }

    /**
     * Parses human-readable duration strings (e.g. 30d, 7d, 24h, 1h, perm) into Duration.
     */
    private Duration parseDuration(String input) {
        if (input == null || input.trim().isEmpty()) {
            return Duration.ofDays(7);
        }
        String clean = input.trim().toLowerCase();
        if (clean.equals("perm") || clean.equals("permanent") || clean.equals("forever") || clean.equals("0") || clean.equals("навсегда")) {
            return null; // permanent ban in Bukkit API
        }

        Matcher matcher = TIME_PATTERN.matcher(clean);
        if (matcher.find()) {
            try {
                long amount = Long.parseLong(matcher.group(1));
                String unit = matcher.group(2);
                if (unit == null) unit = "d";

                return switch (unit.toLowerCase()) {
                    case "s", "сек" -> Duration.ofSeconds(amount);
                    case "m", "мин" -> Duration.ofMinutes(amount);
                    case "h", "ч" -> Duration.ofHours(amount);
                    case "d", "д" -> Duration.ofDays(amount);
                    default -> Duration.ofDays(amount);
                };
            } catch (NumberFormatException ignored) {
            }
        }
        return Duration.ofDays(7);
    }

    /**
     * Opens the general staff mode Chest GUI (SHIFT + click).
     */
    public void openSpawnSettingsDialog(Player developer) {
        if (!developer.isOnline() || !plugin.getAuditService().isDeveloper(developer)) {
            return;
        }

        Component title = MessageUtils.parse("<gradient:light_purple:blue><bold>Настройка спавнов проверки</bold></gradient>");

        String chSpawn = plugin.getConfigManager().getCheatCheckCheaterSpawn();
        String modSpawn = plugin.getConfigManager().getCheatCheckModeratorSpawn();
        String finSpawn = plugin.getConfigManager().getCheatCheckFinishSpawn();

        String bodyText = "<gray>Текущие точки комнат проверок на читы:</gray>\n\n" +
                "<gray>• <white>cheater-spawn:</white> <yellow>" + (chSpawn != null ? chSpawn : "не задано") + "</yellow></gray>\n" +
                "<gray>• <white>moderator-spawn:</white> <yellow>" + (modSpawn != null ? modSpawn : "не задано") + "</yellow></gray>\n" +
                "<gray>• <white>finish-spawn:</white> <yellow>" + (finSpawn != null ? finSpawn : "не задано") + "</yellow></gray>\n\n" +
                "<gray>Нажмите на кнопку ниже, чтобы привязать соответствующую точку к вашей текущей позиции.</gray>";

        Component body = MessageUtils.parse(bodyText);
        int buttonWidth = plugin.getConfig().getInt("dialog.button-width", 220);
        List<ActionButton> actions = new ArrayList<>();

        actions.add(ActionButton.builder(MessageUtils.parse("<red>🎯 Установить cheater-spawn</red>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        String locStr = org.itzmr.moderatio.utils.LocationUtils.serialize(p.getLocation());
                        plugin.getConfigManager().setCheatCheckCheaterSpawn(locStr);
                        MessageUtils.send(p, "<green>Точка <white>cheater-spawn</white> успешно сохранена: <yellow>" + locStr + "</yellow></green>");
                        openSpawnSettingsDialog(p);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        actions.add(ActionButton.builder(MessageUtils.parse("<yellow>🛡 Установить moderator-spawn</yellow>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        String locStr = org.itzmr.moderatio.utils.LocationUtils.serialize(p.getLocation());
                        plugin.getConfigManager().setCheatCheckModeratorSpawn(locStr);
                        MessageUtils.send(p, "<green>Точка <white>moderator-spawn</white> успешно сохранена: <yellow>" + locStr + "</yellow></green>");
                        openSpawnSettingsDialog(p);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        actions.add(ActionButton.builder(MessageUtils.parse("<green>🚪 Установить finish-spawn</green>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        String locStr = org.itzmr.moderatio.utils.LocationUtils.serialize(p.getLocation());
                        plugin.getConfigManager().setCheatCheckFinishSpawn(locStr);
                        MessageUtils.send(p, "<green>Точка <white>finish-spawn</white> успешно сохранена: <yellow>" + locStr + "</yellow></green>");
                        openSpawnSettingsDialog(p);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        ActionButton exitAction = ActionButton.builder(MessageUtils.parse("<gray>⬅ Назад в меню</gray>"))
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        openStaffMenuDialog(p);
                    }
                }, CALLBACK_OPTIONS))
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .build())
                .type(DialogType.multiAction(actions, exitAction, 1)));

        developer.showDialog(dialog);
    }

    public void openStaffAuditListGui(Player developer, int page) {
        if (!developer.isOnline() || !plugin.getAuditService().isDeveloper(developer)) {
            return;
        }

        plugin.getDatabaseManager().getAllStaffNames().thenAccept(dbStaff -> {
            Set<String> allStaff = new LinkedHashSet<>(plugin.getAuditService().getAllKnownStaff());
            allStaff.addAll(dbStaff);
            List<String> sortedStaff = new ArrayList<>(allStaff);
            sortedStaff.sort(String.CASE_INSENSITIVE_ORDER);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!developer.isOnline()) return;

                int itemsPerPage = 28;
                int totalPages = Math.max(1, (int) Math.ceil((double) sortedStaff.size() / itemsPerPage));
                int curPage = Math.max(0, Math.min(page, totalPages - 1));

                StaffAuditListHolder holder = new StaffAuditListHolder(developer, sortedStaff, curPage, totalPages);
                Inventory inv = Bukkit.createInventory(holder, 54, MessageUtils.parse("<dark_gray>Аудит персонала [Стр. " + (curPage + 1) + "/" + totalPages + "]</dark_gray>"));
                holder.setInventory(inv);

                ItemStack glass = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, MessageUtils.parse("<dark_gray> </dark_gray>"), null);
                for (int i = 0; i < 54; i++) {
                    inv.setItem(i, glass);
                }

                int[] innerSlots = {
                        10, 11, 12, 13, 14, 15, 16,
                        19, 20, 21, 22, 23, 24, 25,
                        28, 29, 30, 31, 32, 33, 34,
                        37, 38, 39, 40, 41, 42, 43
                };

                int startIndex = curPage * itemsPerPage;
                for (int i = 0; i < innerSlots.length; i++) {
                    int staffIndex = startIndex + i;
                    if (staffIndex < sortedStaff.size()) {
                        String staffName = sortedStaff.get(staffIndex);
                        int slot = innerSlots[i];
                        holder.mapStaff(slot, staffName);

                        Player onlinePlayer = Bukkit.getPlayerExact(staffName);
                        boolean isOnline = onlinePlayer != null && onlinePlayer.isOnline();
                        boolean inStaffMode = isOnline && staffManager.isInStaffMode(onlinePlayer);
                        boolean isDev = plugin.getAuditService().isDeveloperName(staffName);

                        List<Component> lore = new ArrayList<>();
                        lore.add(MessageUtils.parse(isOnline ? "<green>● В сети</green>" : "<dark_gray>● Не в сети</dark_gray>"));
                        if (inStaffMode) {
                            lore.add(MessageUtils.parse("<aqua>● Staff Mode активен</aqua>"));
                        }
                        if (isDev) {
                            lore.add(MessageUtils.parse("<light_purple>★ Разработчик</light_purple>"));
                        }
                        lore.add(Component.empty());
                        lore.add(MessageUtils.parse("<yellow>▶ Нажмите для просмотра наказаний</yellow>"));

                        inv.setItem(slot, createSkullItem(staffName, MessageUtils.parse("<gold><bold>" + staffName + "</bold></gold>"), lore));
                    }
                }

                // Navigation
                if (curPage > 0) {
                    inv.setItem(45, createGuiItem(Material.ARROW, MessageUtils.parse("<white><bold>⬅ Предыдущая страница</bold></white>"), null));
                }
                inv.setItem(48, createGuiItem(Material.BARRIER, MessageUtils.parse("<red><bold>⬅ Назад в меню персонала</bold></red>"), null));
                inv.setItem(49, createGuiItem(Material.PAPER, MessageUtils.parse("<gold><bold>Страница " + (curPage + 1) + " из " + totalPages + "</bold></gold>"),
                        List.of(MessageUtils.parse("<gray>Всего сотрудников: <white>" + sortedStaff.size() + "</white></gray>"))));
                if (curPage < totalPages - 1) {
                    inv.setItem(53, createGuiItem(Material.ARROW, MessageUtils.parse("<white><bold>Следующая страница ➡</bold></white>"), null));
                }

                developer.openInventory(inv);
            });
        });
    }

    /**
     * Opens moderator's own punishments paginated Chest GUI.
     */
    public void openMyPunishmentsGui(Player moderator, int page) {
        openPunishmentHistoryGui(PunishmentHistoryHolder.HistoryMode.MY_PUNISHMENTS, moderator, moderator.getUniqueId(), moderator.getName(), page);
    }

    /**
     * Opens target player's punishment history paginated Chest GUI.
     */
    public void openTargetHistoryGui(Player moderator, UUID targetUuid, String targetName, int page) {
        openPunishmentHistoryGui(PunishmentHistoryHolder.HistoryMode.TARGET_HISTORY, moderator, targetUuid, targetName, page);
    }

    /**
     * Opens staff member's punishment history for developer audit.
     */
    public void openStaffMemberHistoryGui(Player developer, String staffName, int page) {
        openPunishmentHistoryGui(PunishmentHistoryHolder.HistoryMode.STAFF_AUDIT, developer, null, staffName, page);
    }

    /**
     * Generic paginated punishment history GUI.
     */
    public void openPunishmentHistoryGui(PunishmentHistoryHolder.HistoryMode mode, Player viewer, UUID filterUuid, String filterName, int page) {
        if (!viewer.isOnline()) return;

        CompletableFuture<List<Punishment>> queryFuture;
        if (mode == PunishmentHistoryHolder.HistoryMode.MY_PUNISHMENTS) {
            queryFuture = plugin.getDatabaseManager().getPunishmentsByStaff(viewer.getUniqueId());
        } else if (mode == PunishmentHistoryHolder.HistoryMode.STAFF_AUDIT) {
            queryFuture = plugin.getDatabaseManager().getPunishmentsByStaffName(filterName);
        } else {
            queryFuture = plugin.getDatabaseManager().getPunishmentsByTarget(filterUuid);
        }

        queryFuture.thenAccept(punishments -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) return;

                int itemsPerPage = 28;
                int totalPages = Math.max(1, (int) Math.ceil((double) punishments.size() / itemsPerPage));
                int curPage = Math.max(0, Math.min(page, totalPages - 1));

                String titleStr;
                if (mode == PunishmentHistoryHolder.HistoryMode.MY_PUNISHMENTS) {
                    titleStr = "<dark_gray>Мои наказания [Стр. " + (curPage + 1) + "/" + totalPages + "]</dark_gray>";
                } else if (mode == PunishmentHistoryHolder.HistoryMode.STAFF_AUDIT) {
                    titleStr = "<dark_gray>Аудит: " + filterName + " [Стр. " + (curPage + 1) + "/" + totalPages + "]</dark_gray>";
                } else {
                    titleStr = "<dark_gray>История: " + filterName + " [Стр. " + (curPage + 1) + "/" + totalPages + "]</dark_gray>";
                }

                PunishmentHistoryHolder holder = new PunishmentHistoryHolder(mode, viewer, filterUuid, filterName, punishments, curPage, totalPages);
                Inventory inv = Bukkit.createInventory(holder, 54, MessageUtils.parse(titleStr));
                holder.setInventory(inv);

                ItemStack glass = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, MessageUtils.parse("<dark_gray> </dark_gray>"), null);
                for (int i = 0; i < 54; i++) {
                    inv.setItem(i, glass);
                }

                int[] innerSlots = {
                        10, 11, 12, 13, 14, 15, 16,
                        19, 20, 21, 22, 23, 24, 25,
                        28, 29, 30, 31, 32, 33, 34,
                        37, 38, 39, 40, 41, 42, 43
                };

                int startIndex = curPage * itemsPerPage;
                for (int i = 0; i < innerSlots.length; i++) {
                    int pIndex = startIndex + i;
                    if (pIndex < punishments.size()) {
                        Punishment p = punishments.get(pIndex);
                        int slot = innerSlots[i];
                        holder.mapPunishment(slot, p);

                        boolean active = p.isActive() && !p.isExpired();
                        Material mat;
                        if (active) {
                            if (p.getType() == PunishmentType.BAN) mat = Material.RED_CONCRETE;
                            else if (p.getType() == PunishmentType.MUTE) mat = Material.ORANGE_CONCRETE;
                            else mat = Material.YELLOW_CONCRETE;
                        } else {
                            mat = Material.GRAY_CONCRETE;
                        }

                        String typeName = switch (p.getType()) {
                            case BAN -> "БАН";
                            case MUTE -> "МУТ";
                            case WARN -> "ВАРН";
                        };

                        Component displayName;
                        if (active) {
                            String color = switch (p.getType()) {
                                case BAN -> "<red>";
                                case MUTE -> "<gold>";
                                case WARN -> "<yellow>";
                            };
                            String closeColor = switch (p.getType()) {
                                case BAN -> "</red>";
                                case MUTE -> "</gold>";
                                case WARN -> "</yellow>";
                            };
                            displayName = MessageUtils.parse(color + "<bold>" + typeName + "</bold>" + closeColor + " <gray>•</gray> <white>" + p.getTargetName() + "</white>");
                        } else {
                            displayName = MessageUtils.parse("<gray><bold>" + typeName + "</bold> • " + p.getTargetName() + " <dark_gray>(Неактивно)</dark_gray></gray>");
                        }

                        List<Component> lore = new ArrayList<>();
                        lore.add(MessageUtils.parse("<gray>Дата: <white>" + TimeUtils.formatDate(p.getCreatedAt()) + "</white></gray>"));
                        lore.add(MessageUtils.parse("<gray>Тип: <white>" + typeName + "</white></gray>"));
                        lore.add(MessageUtils.parse("<gray>Нарушитель: <yellow>" + p.getTargetName() + "</yellow></gray>"));
                        lore.add(MessageUtils.parse("<gray>Модератор: <yellow>" + p.getStaffName() + "</yellow></gray>"));
                        lore.add(MessageUtils.parse("<gray>Причина: <white>" + p.getReason() + "</white></gray>"));

                        String durDisplay = p.isPermanent() ? "Навсегда" : TimeUtils.formatDuration(p.getExpiresAt() - p.getCreatedAt());
                        lore.add(MessageUtils.parse("<gray>Срок: <white>" + durDisplay + "</white></gray>"));

                        if (active) {
                            lore.add(MessageUtils.parse("<green>● Статус: Активно</green>"));
                        } else if (p.isExpired()) {
                            lore.add(MessageUtils.parse("<dark_gray>● Статус: Истекло</dark_gray>"));
                        } else {
                            lore.add(MessageUtils.parse("<red>● Статус: Снято / Отменено</red>"));
                        }
                        lore.add(Component.empty());
                        lore.add(MessageUtils.parse("<yellow>▶ Нажмите для подробностей</yellow>"));

                        inv.setItem(slot, createGuiItem(mat, displayName, lore));
                    }
                }

                // Controls
                if (curPage > 0) {
                    inv.setItem(45, createGuiItem(Material.ARROW, MessageUtils.parse("<white><bold>⬅ Назад</bold></white>"), null));
                }

                String backText = switch (mode) {
                    case MY_PUNISHMENTS -> "<red><bold>⬅ Назад в меню</bold></red>";
                    case STAFF_AUDIT -> "<red><bold>⬅ Назад к списку персонала</bold></red>";
                    case TARGET_HISTORY -> "<red><bold>⬅ Назад к управлению игроком</bold></red>";
                };
                inv.setItem(48, createGuiItem(Material.BARRIER, MessageUtils.parse(backText), null));

                inv.setItem(49, createGuiItem(Material.PAPER, MessageUtils.parse("<gold><bold>Страница " + (curPage + 1) + " из " + totalPages + "</bold></gold>"),
                        List.of(MessageUtils.parse("<gray>Всего наказаний в базе: <white>" + punishments.size() + "</white></gray>"))));

                if (curPage < totalPages - 1) {
                    inv.setItem(53, createGuiItem(Material.ARROW, MessageUtils.parse("<white><bold>Вперед ➡</bold></white>"), null));
                }

                viewer.openInventory(inv);
            });
        });
    }

    /**
     * Opens detailed punishment card GUI.
     */
    public void openPunishmentDetailGui(Player viewer, Punishment punishment, PunishmentHistoryHolder returnHolder) {
        if (!viewer.isOnline() || punishment == null) return;

        PunishmentDetailHolder holder = new PunishmentDetailHolder(viewer, punishment, returnHolder);
        Inventory inv = Bukkit.createInventory(holder, 27, MessageUtils.parse("<dark_gray>Карточка наказания #" + punishment.getId() + "</dark_gray>"));
        holder.setInventory(inv);

        ItemStack glass = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, MessageUtils.parse("<dark_gray> </dark_gray>"), null);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, glass);
        }

        boolean active = punishment.isActive() && !punishment.isExpired();
        String durDisplay = punishment.isPermanent() ? "Навсегда" : TimeUtils.formatDuration(punishment.getExpiresAt() - punishment.getCreatedAt());
        String expireDisplay = punishment.isPermanent() ? "Навсегда" : TimeUtils.formatDate(punishment.getExpiresAt());

        Component statusBadge;
        if (active) {
            statusBadge = MessageUtils.parse("<green><bold>● АКТИВНО</bold></green>");
        } else if (punishment.isExpired()) {
            statusBadge = MessageUtils.parse("<dark_gray><bold>● ИСТЕКЛО</bold></dark_gray>");
        } else {
            statusBadge = MessageUtils.parse("<red><bold>● ОТМЕНЕНО / СНЯТО</bold></red>");
        }

        List<Component> bookLore = new ArrayList<>();
        bookLore.add(MessageUtils.parse("<gray>Нарушитель: <white>" + punishment.getTargetName() + "</white></gray>"));
        bookLore.add(MessageUtils.parse("<dark_gray>UUID цели: " + (punishment.getTargetUuid() != null ? punishment.getTargetUuid().toString() : "-") + "</dark_gray>"));
        bookLore.add(MessageUtils.parse("<gray>Модератор: <yellow>" + punishment.getStaffName() + "</yellow></gray>"));
        bookLore.add(MessageUtils.parse("<dark_gray>UUID модератора: " + (punishment.getStaffUuid() != null ? punishment.getStaffUuid().toString() : "-") + "</dark_gray>"));
        bookLore.add(MessageUtils.parse("<gray>Тип наказания: <white>" + punishment.getType().name() + "</white></gray>"));
        if (punishment.getType() == PunishmentType.WARN && punishment.getCategory() != null) {
            bookLore.add(MessageUtils.parse("<gray>Категория варна: <yellow>" + punishment.getCategory() + "</yellow></gray>"));
        }
        bookLore.add(MessageUtils.parse("<gray>Причина: <white>" + punishment.getReason() + "</white></gray>"));
        bookLore.add(MessageUtils.parse("<gray>Дата выдачи: <white>" + TimeUtils.formatDate(punishment.getCreatedAt()) + "</white></gray>"));
        bookLore.add(MessageUtils.parse("<gray>Точный срок: <white>" + durDisplay + "</white></gray>"));
        bookLore.add(MessageUtils.parse("<gray>Дата окончания: <white>" + expireDisplay + "</white></gray>"));
        bookLore.add(Component.empty());
        bookLore.add(MessageUtils.parse("<gray>Статус: </gray>").append(statusBadge));

        inv.setItem(13, createGuiItem(Material.BOOK, MessageUtils.parse("<gold><bold>Наказание #" + punishment.getId() + "</bold></gold>"), bookLore));

        // Cancellation button if active
        if (active) {
            boolean isDev = plugin.getAuditService().isDeveloper(viewer);
            boolean isOwner = punishment.getStaffUuid() != null && punishment.getStaffUuid().equals(viewer.getUniqueId());

            List<Component> cancelLore = new ArrayList<>();
            cancelLore.add(MessageUtils.parse("<gray>Досрочно прекратить действие наказания</gray>"));
            cancelLore.add(MessageUtils.parse("<gray>и снять ограничения с игрока.</gray>"));
            cancelLore.add(Component.empty());
            if (isDev || isOwner) {
                cancelLore.add(MessageUtils.parse("<green>▶ Нажмите для отмены наказания</green>"));
            } else {
                cancelLore.add(MessageUtils.parse("<dark_red>✖ Доступно только автору или разработчику</dark_red>"));
            }

            inv.setItem(11, createGuiItem(Material.LIME_DYE, MessageUtils.parse("<red><bold>✖ Отменить наказание</bold></red>"), cancelLore));
        }

        // Return button
        inv.setItem(22, createGuiItem(Material.ARROW, MessageUtils.parse("<white><bold>⬅ Назад к списку</bold></white>"), List.of(
                MessageUtils.parse("<gray>Вернуться к странице наказаний</gray>")
        )));

        viewer.openInventory(inv);
    }

    /**
     * Helper to create a player skull ItemStack.
     */
    public ItemStack createSkullItem(String ownerName, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(ownerName));
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
