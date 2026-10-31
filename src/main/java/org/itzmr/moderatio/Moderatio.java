package org.itzmr.moderatio;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.itzmr.moderatio.audit.StaffAuditService;
import org.itzmr.moderatio.commands.BanCommand;
import org.itzmr.moderatio.commands.CheckCommand;
import org.itzmr.moderatio.commands.ContactCommand;
import org.itzmr.moderatio.commands.ModeratioCommand;
import org.itzmr.moderatio.commands.MuteCommand;
import org.itzmr.moderatio.commands.StaffCommand;
import org.itzmr.moderatio.commands.UnbanCommand;
import org.itzmr.moderatio.commands.UncheckCommand;
import org.itzmr.moderatio.commands.UnmuteCommand;
import org.itzmr.moderatio.commands.UnwarnCommand;
import org.itzmr.moderatio.commands.WarnCommand;
import org.itzmr.moderatio.config.ConfigManager;
import org.itzmr.moderatio.config.ConfigUpdater;
import org.itzmr.moderatio.database.DatabaseManager;
import org.itzmr.moderatio.dialogs.ModerationDialogService;
import org.itzmr.moderatio.listeners.PunishmentListener;
import org.itzmr.moderatio.listeners.StaffGuiListener;
import org.itzmr.moderatio.listeners.StaffInteractListener;
import org.itzmr.moderatio.listeners.StaffProtectionListener;
import org.itzmr.moderatio.listeners.StaffWorldListener;
import org.itzmr.moderatio.punishments.PunishmentManager;
import org.itzmr.moderatio.staff.CheckManager;
import org.itzmr.moderatio.staff.GlowingManager;
import org.itzmr.moderatio.staff.StaffManager;
import org.itzmr.moderatio.staff.VanishManager;

import java.util.List;

/**
 * Main class for the Moderatio plugin.
 */
public final class Moderatio extends JavaPlugin {

    private static Moderatio instance;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private StaffManager staffManager;
    private ModerationDialogService dialogService;
    private StaffAuditService auditService;
    private VanishManager vanishManager;
    private GlowingManager glowingManager;
    private CheckManager checkManager;
    private PunishmentManager punishmentManager;

    public static Moderatio getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // 1. Smart Configuration Update & Initialization
        ConfigUpdater.updateAll(this, "config.yml", "messages.yml", "db.yml");
        this.configManager = new ConfigManager(this);

        // 2. Initialize Database and Tables
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.init();

        // 3. Initialize Managers & Services
        this.auditService = new StaffAuditService(this);
        this.staffManager = new StaffManager(this);
        this.vanishManager = new VanishManager(this);
        this.glowingManager = new GlowingManager(this);
        this.checkManager = new CheckManager(this);
        this.punishmentManager = new PunishmentManager(this);
        this.punishmentManager.init();
        this.dialogService = new ModerationDialogService(this);

        // 4. Register Event Listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new StaffProtectionListener(this), this);
        pm.registerEvents(new StaffWorldListener(this), this);
        pm.registerEvents(new StaffInteractListener(this), this);
        pm.registerEvents(new PunishmentListener(this), this);
        pm.registerEvents(this.vanishManager, this);
        pm.registerEvents(this.checkManager, this);
        pm.registerEvents(new StaffGuiListener(this), this);

        // 5. Register Commands
        registerCommands();

        getLogger().info("Moderatio plugin has been successfully enabled! Running on " + Bukkit.getName() + " " + Bukkit.getVersion());
    }

    @Override
    public void onDisable() {
        if (this.staffManager != null) {
            this.staffManager.cleanup();
        }
        if (this.checkManager != null) {
            this.checkManager.cleanupAll();
        }
        if (this.glowingManager != null) {
            this.glowingManager.cleanupAll();
        }
        if (this.databaseManager != null) {
            this.databaseManager.close();
        }
        getLogger().info("Moderatio plugin disabled. All staff modes have been safely restored.");
    }

    private void registerCommands() {
        StaffCommand staffCmd = new StaffCommand(this);
        ModeratioCommand moderatioCmd = new ModeratioCommand(this);
        BanCommand banCmd = new BanCommand(this);
        UnbanCommand unbanCmd = new UnbanCommand(this);
        MuteCommand muteCmd = new MuteCommand(this);
        UnmuteCommand unmuteCmd = new UnmuteCommand(this);
        WarnCommand warnCmd = new WarnCommand(this);
        UnwarnCommand unwarnCmd = new UnwarnCommand(this);
        CheckCommand checkCmd = new CheckCommand(this);
        UncheckCommand uncheckCmd = new UncheckCommand(this);
        ContactCommand discordCmd = new ContactCommand(this, "Discord");
        ContactCommand anydeskCmd = new ContactCommand(this, "AnyDesk");
        ContactCommand rustdeskCmd = new ContactCommand(this, "RustDesk");

        registerPluginCommand("staff", staffCmd, staffCmd, "Вход в режим модератора", "/staff", List.of("sm", "staffmode"));
        registerPluginCommand("moderatio", moderatioCmd, moderatioCmd, "Главная команда управления Moderatio", "/moderatio [reload]", List.of("mod"));
        registerPluginCommand("ban", banCmd, banCmd, "Заблокировать игрока", "/ban <игрок> <срок: 1h/7d/perm> [причина]", List.of("mban"));
        registerPluginCommand("unban", unbanCmd, unbanCmd, "Разблокировать игрока", "/unban <игрок>", List.of("munban"));
        registerPluginCommand("mute", muteCmd, muteCmd, "Заблокировать чат игроку", "/mute <игрок> <срок: 1h/7d/perm> [причина]", List.of("mmute"));
        registerPluginCommand("unmute", unmuteCmd, unmuteCmd, "Разблокировать чат игроку", "/unmute <игрок>", List.of("munmute"));
        registerPluginCommand("warn", warnCmd, warnCmd, "Выдать предупреждение игроку", "/warn <игрок> [CHAT|GAME] [причина]", List.of("mwarn"));
        registerPluginCommand("unwarn", unwarnCmd, unwarnCmd, "Снять последнее предупреждение с игрока", "/unwarn <игрок> <причина>", List.of("munwarn"));
        registerPluginCommand("check", checkCmd, checkCmd, "Начать проверку игрока на читы", "/check <игрок>", List.of("mcheck"));
        registerPluginCommand("uncheck", uncheckCmd, uncheckCmd, "Завершить проверку игрока на читы", "/uncheck <игрок> [passed/fail]", List.of("muncheck"));
        registerPluginCommand("discord", discordCmd, discordCmd, "Отправить Discord модератору", "/discord <контакт>", List.of("mdiscord"));
        registerPluginCommand("anydesk", anydeskCmd, anydeskCmd, "Отправить AnyDesk модератору", "/anydesk <код>", List.of("manydesk"));
        registerPluginCommand("rustdesk", rustdeskCmd, rustdeskCmd, "Отправить RustDesk модератору", "/rustdesk <код>", List.of("mrustdesk"));
    }

    private void registerPluginCommand(String name, CommandExecutor executor, TabCompleter completer, String description, String usage, List<String> aliases) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(completer);
        } else {
            registerCommandViaMap(name, description, usage, aliases, executor, completer);
        }
    }

    private void registerCommandViaMap(String name, String description, String usage, List<String> aliases, CommandExecutor executor, TabCompleter completer) {
        CommandMap commandMap = Bukkit.getCommandMap();
        Command command = new Command(name, description, usage, aliases) {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                return executor.onCommand(sender, this, commandLabel, args);
            }

            @Override
            public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
                if (completer != null) {
                    List<String> list = completer.onTabComplete(sender, this, alias, args);
                    return list != null ? list : super.tabComplete(sender, alias, args);
                }
                return super.tabComplete(sender, alias, args);
            }
        };
        commandMap.register(getName().toLowerCase(), command);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public StaffManager getStaffManager() {
        return staffManager;
    }

    public ModerationDialogService getDialogService() {
        return dialogService;
    }

    public StaffAuditService getAuditService() {
        return auditService;
    }

    public VanishManager getVanishManager() {
        return vanishManager;
    }

    public GlowingManager getGlowingManager() {
        return glowingManager;
    }

    public CheckManager getCheckManager() {
        return checkManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }
}
