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
import org.itzmr.moderatio.staff.StaffManager;
import org.itzmr.moderatio.utils.MessageUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        // 3. Trolling Button
        Component trollLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-troll", false));
        actions.add(ActionButton.builder(trollLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        openTrollingDialog(p, target);
                    }
                }, CALLBACK_OPTIONS))
                .build());

        // 4. Exit Staff Mode Button
        Component exitStaffLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-exit-staff", false));
        actions.add(ActionButton.builder(exitStaffLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        staffManager.disableStaffMode(p, true);
                    }
                }, CALLBACK_OPTIONS))
                .build());

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
                String commandTemplate = section.getString(key + ".command", "mute {target} {duration} {reason}");

                Component label = MessageUtils.parse(btnTitle + " <gray>(" + duration + ")</gray>");
                actions.add(ActionButton.builder(label)
                        .width(buttonWidth)
                        .action(DialogAction.customClick((resp, aud) -> {
                            if (aud instanceof Player p) {
                                executeMute(p, target, commandTemplate, duration, reason);
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

        // 1. Launch into air
        Component launchLabel = MessageUtils.parse(plugin.getConfigManager().getMessage("dialog.btn-troll-launch", false));
        actions.add(ActionButton.builder(launchLabel)
                .width(buttonWidth)
                .action(DialogAction.customClick((resp, aud) -> {
                    if (aud instanceof Player p) {
                        launchPlayer(p, target);
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
     * Executes ban logic for target player.
     */
    public void executeBan(Player moderator, Player target, String durationStr, String reason) {
        if (!target.isOnline()) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        String mode = plugin.getConfig().getString("ban-system.mode", "NATIVE").toUpperCase();

        if ("COMMAND".equals(mode)) {
            String cmdTemplate = plugin.getConfig().getString("ban-system.console-command", "ban {target} {duration} {reason}");
            String cmd = cmdTemplate
                    .replace("{target}", target.getName())
                    .replace("{duration}", durationStr)
                    .replace("{reason}", reason)
                    .replace("{moderator}", moderator.getName());

            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            });
        } else {
            Duration duration = parseDuration(durationStr);
            Bukkit.getScheduler().runTask(plugin, () -> {
                target.ban(reason, duration, moderator.getName(), true);
            });
        }

        Map<String, String> placeholders = Map.of(
                "target", target.getName(),
                "duration", durationStr,
                "reason", reason,
                "moderator", moderator.getName()
        );
        MessageUtils.send(moderator, plugin.getConfigManager().getMessage("punishment.banned-success", placeholders));
    }

    /**
     * Executes mute logic for target player.
     */
    public void executeMute(Player moderator, Player target, String commandTemplate, String duration, String reason) {
        if (!target.isOnline()) {
            MessageUtils.send(moderator, plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        String cmd = commandTemplate
                .replace("{target}", target.getName())
                .replace("{duration}", duration)
                .replace("{reason}", reason)
                .replace("{moderator}", moderator.getName());

        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        });

        Map<String, String> placeholders = Map.of(
                "target", target.getName(),
                "duration", duration,
                "reason", reason,
                "moderator", moderator.getName()
        );
        MessageUtils.send(moderator, plugin.getConfigManager().getMessage("punishment.muted-success", placeholders));
    }

    /**
     * Launches the target player upward.
     */
    public void launchPlayer(Player moderator, Player target) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (target.isOnline()) {
                target.setVelocity(new Vector(0, 1.8, 0));
                target.playSound(target.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
            }
        });
        Map<String, String> placeholders = Map.of("target", target.getName());
        MessageUtils.send(moderator, plugin.getConfigManager().getMessage("punishment.troll-launch", placeholders));
    }

    /**
     * Toggles frozen state of target player.
     */
    public void toggleFreezePlayer(Player moderator, Player target) {
        boolean frozen = staffManager.toggleFreeze(target);
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
