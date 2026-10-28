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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.audit.StaffActionEntry;
import org.itzmr.moderatio.staff.StaffManager;
import org.itzmr.moderatio.utils.MessageUtils;

import java.time.Duration;
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
        Component body = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.main-body", placeholders, false));

        int buttonWidth = plugin.getConfig().getInt("dialog.button-width", 220);

        List<ActionButton> actions = new ArrayList<>();

        // 1. Ban Button
        Component banLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-ban", false));
        actions.add(ActionButton.builder(banLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
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
                        openMuteTemplatesDialog(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // 3. Target Glowing Toggle Button
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

        // 4. Trolling Button (Exclusive to Developers)
        if (staffManager.canTroll(moderator)) {
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

        String mode = plugin.getConfig().getString("punishments.mode",
                plugin.getConfig().getString("ban-system.mode", "CONSOLE_COMMAND")).toUpperCase();
        boolean isPerm = isPermanentDuration(durationStr);

        if ("NATIVE".equals(mode)) {
            Duration duration = isPerm ? null : parseDuration(durationStr);
            Bukkit.getScheduler().runTask(plugin, () -> {
                target.ban(reason, duration, moderator.getName(), true);
            });
        } else {
            boolean useSeparate = plugin.getConfig().getBoolean("punishments.use-separate-temp-commands", true);
            String cmdTemplate;
            if (isPerm) {
                cmdTemplate = plugin.getConfig().getString("punishments.bans.perm-command", "ban {target} {reason}");
            } else if (useSeparate) {
                cmdTemplate = plugin.getConfig().getString("punishments.bans.temp-command", "tempban {target} {duration} {reason}");
            } else {
                cmdTemplate = plugin.getConfig().getString("punishments.bans.single-command",
                        plugin.getConfig().getString("ban-system.console-command", "ban {target} {duration} {reason}"));
            }

            String rawCmd = cmdTemplate
                    .replace("{target}", target.getName())
                    .replace("{duration}", durationStr != null ? durationStr : "")
                    .replace("{reason}", reason)
                    .replace("{moderator}", moderator.getName());

            final String finalCmd = rawCmd.startsWith("/") ? rawCmd.substring(1).trim() : rawCmd.trim();

            Runnable task = () -> {
                plugin.getLogger().info("[Moderatio] Executing ban command: " + finalCmd);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
            };

            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        }

        // Action audit and anti-abuse detection
        plugin.getAuditService().logAction(moderator, "BAN", target.getName(), "Duration: " + durationStr + ", Reason: " + reason);
        plugin.getAuditService().checkPunishmentAbuse(moderator);

        Map<String, String> placeholders = Map.of(
                "target", target.getName(),
                "duration", durationStr != null ? durationStr : "perm",
                "reason", reason,
                "moderator", moderator.getName()
        );
        MessageUtils.send(moderator, plugin.getConfigManager().getMessage("punishment.banned-success", placeholders));
    }

    /**
     * Executes mute logic for target player via console command.
     */
    public void executeMute(Player moderator, Player target, String duration, String reason) {
        executeMute(moderator, target, null, duration, reason);
    }

    /**
     * Executes mute logic for target player via console command.
     */
    public void executeMute(Player moderator, Player target, String commandTemplate, String duration, String reason) {
        if (!target.isOnline()) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        String template = commandTemplate;
        if (template == null || template.isBlank()) {
            boolean useSeparate = plugin.getConfig().getBoolean("punishments.use-separate-temp-commands", true);
            boolean isPerm = isPermanentDuration(duration);
            if (isPerm) {
                template = plugin.getConfig().getString("punishments.mutes.perm-command", "mute {target} {reason}");
            } else if (useSeparate) {
                template = plugin.getConfig().getString("punishments.mutes.temp-command", "tempmute {target} {duration} {reason}");
            } else {
                template = plugin.getConfig().getString("punishments.mutes.single-command",
                        plugin.getConfig().getString("mute-system.console-command", "mute {target} {duration} {reason}"));
            }
        }

        if (template == null || template.isBlank()) {
            template = "tempmute {target} {duration} {reason}";
        }

        String rawCmd = template
                .replace("{target}", target.getName())
                .replace("{duration}", duration != null ? duration : "")
                .replace("{reason}", reason)
                .replace("{moderator}", moderator.getName());

        final String finalCmd = rawCmd.startsWith("/") ? rawCmd.substring(1).trim() : rawCmd.trim();

        // Must strictly run in the main synchronous thread
        Runnable task = () -> {
            plugin.getLogger().info("[Moderatio] Executing mute command: " + finalCmd);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
        };

        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }

        // Action audit and anti-abuse detection
        plugin.getAuditService().logAction(moderator, "MUTE", target.getName(), "Duration: " + duration + ", Reason: " + reason);
        plugin.getAuditService().checkPunishmentAbuse(moderator);

        Map<String, String> placeholders = Map.of(
                "target", target.getName(),
                "duration", duration != null ? duration : "perm",
                "reason", reason,
                "moderator", moderator.getName()
        );
        MessageUtils.send(moderator, plugin.getConfigManager().getMessage("punishment.muted-success", placeholders));
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

    /**
     * Creates placeholder map for player details.
     */
    private Map<String, String> createPlayerPlaceholders(Player player) {
        Map<String, String> map = new HashMap<>();
        map.put("target", player.getName());
        map.put("ping", String.valueOf(player.getPing()));
        map.put("health", String.valueOf((int) Math.ceil(player.getHealth())));
        map.put("max_health", String.valueOf((int) Math.ceil(player.getMaxHealth())));
        map.put("gamemode", player.getGameMode().name());
        map.put("world", player.getWorld().getName());
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
