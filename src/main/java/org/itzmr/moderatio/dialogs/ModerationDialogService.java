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

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", moderator.getName());
        placeholders.put("world", moderator.getWorld().getName());
        placeholders.put("x", String.valueOf(moderator.getLocation().getBlockX()));
        placeholders.put("y", String.valueOf(moderator.getLocation().getBlockY()));
        placeholders.put("z", String.valueOf(moderator.getLocation().getBlockZ()));
        placeholders.put("online", String.valueOf(Bukkit.getOnlinePlayers().size()));
        placeholders.put("max_online", String.valueOf(Bukkit.getMaxPlayers()));

        Component title = MessageUtils.parse(plugin.getConfigManager().getMessage("staff-menu.title", placeholders, false));
        Component body = MessageUtils.parse(plugin.getConfigManager().getMessage("staff-menu.body", placeholders, false));

        int buttonWidth = plugin.getConfig().getInt("dialog.button-width", 220);

        List<ActionButton> actions = new ArrayList<>();

        // 1. Vanish Toggle Button
        boolean isVanished = plugin.getVanishManager().isVanished(moderator);
        String vanishLabelKey = isVanished ? "vanish.btn-toggle-on" : "vanish.btn-toggle-off";
        String defaultVanishText = isVanished ? "<green>👻 Режим скрытия: ВКЛ" : "<gray>👻 Режим скрытия: ВЫКЛ";
        String vanishRaw = plugin.getConfigManager().getMessage(vanishLabelKey, false);
        if (vanishRaw.contains("Missing message")) {
            vanishRaw = defaultVanishText;
        }
        Component vanishLabel = MessageUtils.parse(vanishRaw);
        actions.add(ActionButton.builder(vanishLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        plugin.getVanishManager().toggleVanish(p);
                        openStaffMenuDialog(p);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // 2. Staff Audit (Developers Only)
        if (plugin.getAuditService().isDeveloper(moderator)) {
            String auditRaw = plugin.getConfigManager().getMessage("dialog.btn-audit", false);
            if (auditRaw.contains("Missing message")) {
                auditRaw = "<gold>📋 Аудит состава";
            }
            Component auditLabel = MessageUtils.parse(auditRaw);
            actions.add(ActionButton.builder(auditLabel)
                    .width(buttonWidth)
                    .action(DialogAction.customClick((resp, aud) -> {
                        if (aud instanceof Player p) {
                            openStaffAuditDialog(p);
                        }
                    }, CALLBACK_OPTIONS))
                    .build());
        }

        // 3. Exit Staff Mode Button
        Component exitStaffLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("staff-menu.btn-exit", false));
        actions.add(ActionButton.builder(exitStaffLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        staffManager.disableStaffMode(p, true);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // Close Button (exitAction)
        Component closeLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-close", false));
        ActionButton exitAction = ActionButton.builder(closeLabel)
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

        Component title = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.main-title", placeholders, false));
        String rawBody = plugin.getConfigManager().getMessage("dialog.main-body", placeholders, false);
        if (plugin.getCheckManager().isUnderCheck(target.getUniqueId()) && !rawBody.contains("НА ПРОВЕРКЕ")) {
            rawBody = "<red><bold>СТАТУС: НА ПРОВЕРКЕ</bold></red>\n\n" + rawBody;
        }
        Component body = MessageUtils.parse(rawBody);

        int buttonWidth = plugin.getConfig().getInt("dialog.button-width", 220);

        List<ActionButton> actions = new ArrayList<>();

        // 1. Ban Button
        Component banLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-ban", false));
        actions.add(ActionButton.builder(banLabel)
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
        Component muteLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-mute", false));
        actions.add(ActionButton.builder(muteLabel)
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
        String warnRaw = plugin.getConfigManager().getMessage("dialog.btn-warn", false);
        if (warnRaw.contains("Missing message")) {
            warnRaw = "<gold>📜 Выдать варн</gold>";
        }
        Component warnLabel = MessageUtils.parse(warnRaw);
        actions.add(ActionButton.builder(warnLabel)
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

        // 3. Target Glowing Toggle Button (hidden if moderator is actively checking this target)
        if (!isCheckingThisTarget) {
            boolean glowing = plugin.getGlowingManager().isGlowing(moderator.getUniqueId(), target.getUniqueId());
            String glowKey = glowing ? "dialog.btn-glow-off" : "dialog.btn-glow-on";
            String glowDefault = glowing ? "<gray>👁 Следить за игроком (Выкл. подсветку)" : "<gold>👁 Следить за игроком (Подсветка)";
            String glowRaw = plugin.getConfigManager().getMessage(glowKey, false);
            if (glowRaw.contains("Missing message")) {
                glowRaw = glowDefault;
            }
            Component glowLabel = MessageUtils.parse(glowRaw);
            actions.add(ActionButton.builder(glowLabel)
                    .width(buttonWidth)
                    .action(DialogAction.customClick((resp, aud) -> {
                        if (aud instanceof Player p) {
                            plugin.getGlowingManager().toggleGlowing(p, target);
                            openMainDialog(p, target);
                        }
                    }, CALLBACK_OPTIONS))
                    .build());
        }

        // 4. Cheat Check / Finish Check Button
        if (plugin.getConfigManager().isCheatCheckEnabled()) {
            if (isCheckingThisTarget) {
                // Suspicious target is already under check by this moderator -> show "Снять с проверки"
                String uncheckRaw = plugin.getConfigManager().getMessage("dialog.btn-cheat-uncheck", false);
                if (uncheckRaw.contains("Missing message")) {
                    uncheckRaw = "<green><bold>Снять с проверки</bold></green>";
                }
                Component uncheckLabel = MessageUtils.parse(uncheckRaw);
                actions.add(ActionButton.builder(uncheckLabel)
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
                String checkRaw = plugin.getConfigManager().getMessage("dialog.btn-cheat-check", false);
                if (checkRaw.contains("Missing message")) {
                    checkRaw = "<yellow><bold>Проверка на читы</bold></yellow>";
                }
                Component checkLabel = MessageUtils.parse(checkRaw);
                actions.add(ActionButton.builder(checkLabel)
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

        // 5. Trolling Button (Exclusive to Developers - hidden if actively checking this target)
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
        Component closeLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-close", false));
        ActionButton exitAction = ActionButton.builder(closeLabel)
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

        WarnMenuHolder holder = new WarnMenuHolder(target);
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtils.parse("<dark_gray>Выбор варна: <gold>" + target.getName() + "</gold></dark_gray>"));
        holder.setInventory(inv);

        ItemStack glass = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, MessageUtils.parse("<dark_gray> </dark_gray>"), null);
        ItemStack divider = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, MessageUtils.parse("<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</dark_gray>"), null);

        // Fill background
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, glass);
        }

        // Zone CHAT Header (Slot 4)
        inv.setItem(4, createGuiItem(Material.WRITABLE_BOOK,
                MessageUtils.parse("<yellow><bold>ЗОНА CHAT: Предупреждения общения</bold></yellow>"),
                List.of(MessageUtils.parse("<gray>Нарушения правил общения в чате</gray>"))));

        // Zone CHAT Templates
        // Slot 10: "Спам / Флуд" (WRITABLE_BOOK)
        inv.setItem(10, createGuiItem(Material.WRITABLE_BOOK,
                MessageUtils.parse("<yellow><bold>Спам / Флуд</bold></yellow>"),
                List.of(
                        MessageUtils.parse("<gray>Категория: <white>CHAT</white></gray>"),
                        MessageUtils.parse("<dark_gray>Причина: Спам и флуд в чате</dark_gray>"),
                        Component.empty(),
                        MessageUtils.parse("<green>▶ Нажмите для выдачи варна</green>")
                )));
        holder.setAction(10, "CHAT", "Спам и флуд в чате");

        // Slot 12: "Оскорбления" (PAPER)
        inv.setItem(12, createGuiItem(Material.PAPER,
                MessageUtils.parse("<red><bold>Оскорбления</bold></red>"),
                List.of(
                        MessageUtils.parse("<gray>Категория: <white>CHAT</white></gray>"),
                        MessageUtils.parse("<dark_gray>Причина: Оскорбление игроков и токсичное поведение</dark_gray>"),
                        Component.empty(),
                        MessageUtils.parse("<green>▶ Нажмите для выдачи варна</green>")
                )));
        holder.setAction(12, "CHAT", "Оскорбление игроков и токсичное поведение");

        // Slot 14: "Капс" (WRITABLE_BOOK)
        inv.setItem(14, createGuiItem(Material.WRITABLE_BOOK,
                MessageUtils.parse("<gold><bold>Капс</bold></gold>"),
                List.of(
                        MessageUtils.parse("<gray>Категория: <white>CHAT</white></gray>"),
                        MessageUtils.parse("<dark_gray>Причина: Злоупотребление верхним регистром (капс)</dark_gray>"),
                        Component.empty(),
                        MessageUtils.parse("<green>▶ Нажмите для выдачи варна</green>")
                )));
        holder.setAction(14, "CHAT", "Злоупотребление верхним регистром (капс)");

        // Slot 16: "Ненормативная лексика" (PAPER)
        inv.setItem(16, createGuiItem(Material.PAPER,
                MessageUtils.parse("<yellow><bold>Ненормативная лексика</bold></yellow>"),
                List.of(
                        MessageUtils.parse("<gray>Категория: <white>CHAT</white></gray>"),
                        MessageUtils.parse("<dark_gray>Причина: Ненормативная лексика и мат</dark_gray>"),
                        Component.empty(),
                        MessageUtils.parse("<green>▶ Нажмите для выдачи варна</green>")
                )));
        holder.setAction(16, "CHAT", "Ненормативная лексика и мат");

        // Row 2 (slots 18-26): Divider
        for (int i = 18; i <= 26; i++) {
            inv.setItem(i, divider);
        }

        // Zone GAME Header (Slot 22 inside divider)
        inv.setItem(22, createGuiItem(Material.SHIELD,
                MessageUtils.parse("<red><bold>ЗОНА GAME: Предупреждения игры</bold></red>"),
                List.of(MessageUtils.parse("<gray>Нарушения игрового процесса и взаимодействия</gray>"))));

        // Zone GAME Templates
        // Slot 29: "Помеха игре" (IRON_SWORD)
        inv.setItem(29, createGuiItem(Material.IRON_SWORD,
                MessageUtils.parse("<red><bold>Помеха игре</bold></red>"),
                List.of(
                        MessageUtils.parse("<gray>Категория: <white>GAME</white></gray>"),
                        MessageUtils.parse("<dark_gray>Причина: Создание помех игровому процессу</dark_gray>"),
                        Component.empty(),
                        MessageUtils.parse("<green>▶ Нажмите для выдачи варна</green>")
                )));
        holder.setAction(29, "GAME", "Создание помех игровому процессу");

        // Slot 31: "Тимкилл / Грифинг" (SHIELD)
        inv.setItem(31, createGuiItem(Material.SHIELD,
                MessageUtils.parse("<dark_red><bold>Тимкилл / Грифинг</bold></dark_red>"),
                List.of(
                        MessageUtils.parse("<gray>Категория: <white>GAME</white></gray>"),
                        MessageUtils.parse("<dark_gray>Причина: Тимкилл, вредительство или грифинг</dark_gray>"),
                        Component.empty(),
                        MessageUtils.parse("<green>▶ Нажмите для выдачи варна</green>")
                )));
        holder.setAction(31, "GAME", "Тимкилл, вредительство или грифинг");

        // Slot 33: "Багоюз" (IRON_SWORD)
        inv.setItem(33, createGuiItem(Material.IRON_SWORD,
                MessageUtils.parse("<gold><bold>Багоюз</bold></gold>"),
                List.of(
                        MessageUtils.parse("<gray>Категория: <white>GAME</white></gray>"),
                        MessageUtils.parse("<dark_gray>Причина: Использование багов и недоработок сервера</dark_gray>"),
                        Component.empty(),
                        MessageUtils.parse("<green>▶ Нажмите для выдачи варна</green>")
                )));
        holder.setAction(33, "GAME", "Использование багов и недоработок сервера");

        // Row 4 (slots 36-44): Divider
        for (int i = 36; i <= 44; i++) {
            inv.setItem(i, divider);
        }

        // Control buttons (Row 5)
        // Slot 45: Back
        inv.setItem(45, createGuiItem(Material.ARROW,
                MessageUtils.parse("<white><bold>⬅ Назад</bold></white>"),
                List.of(MessageUtils.parse("<gray>Вернуться в главное меню модерации</gray>"))));

        // Slot 49: Custom reason - ONLY visible and active for developers!
        if (plugin.getAuditService().isDeveloper(moderator)) {
            inv.setItem(49, createGuiItem(Material.ANVIL,
                    MessageUtils.parse("<gold><bold>✍ Своя причина варна</bold></gold>"),
                    List.of(
                            MessageUtils.parse("<gray>Ввести произвольную причину варна</gray>"),
                            MessageUtils.parse("<aqua>[Только для разработчиков]</aqua>")
                    )));
        }

        // Slot 53: Close
        inv.setItem(53, createGuiItem(Material.BARRIER,
                MessageUtils.parse("<red><bold>✖ Закрыть</bold></red>"),
                List.of(MessageUtils.parse("<gray>Закрыть меню варнов</gray>"))));

        moderator.openInventory(inv);
    }

    private ItemStack createGuiItem(Material material, Component name, List<Component> lore) {
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
}
