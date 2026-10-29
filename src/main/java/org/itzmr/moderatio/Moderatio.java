package org.itzmr.moderatio;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.itzmr.moderatio.audit.StaffAuditService;
import org.itzmr.moderatio.commands.ModeratioCommand;
import org.itzmr.moderatio.commands.StaffCommand;
import org.itzmr.moderatio.config.ConfigManager;
import org.itzmr.moderatio.dialogs.ModerationDialogService;
import org.itzmr.moderatio.listeners.StaffInteractListener;
import org.itzmr.moderatio.listeners.StaffProtectionListener;
import org.itzmr.moderatio.listeners.StaffWorldListener;
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
    private StaffManager staffManager;
    private ModerationDialogService dialogService;
    private StaffAuditService auditService;
    private VanishManager vanishManager;
    private GlowingManager glowingManager;

    public static Moderatio getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // 1. Initialize Configuration
        this.configManager = new ConfigManager(this);

        // 2. Initialize Managers & Services
        this.auditService = new StaffAuditService(this);
        this.staffManager = new StaffManager(this);
        this.vanishManager = new VanishManager(this);
        this.glowingManager = new GlowingManager(this);
        this.dialogService = new ModerationDialogService(this);

        // 3. Register Event Listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new StaffProtectionListener(this), this);
        pm.registerEvents(new StaffWorldListener(this), this);
        pm.registerEvents(new StaffInteractListener(this), this);
        pm.registerEvents(this.vanishManager, this);

        // 4. Register Commands
        registerCommands();

        getLogger().info("Moderatio plugin has been successfully enabled! Running on " + Bukkit.getName() + " " + Bukkit.getVersion());
    }

    @Override
    public void onDisable() {
        if (this.glowingManager != null) {
            this.glowingManager.cleanupAll();
        }
        if (this.staffManager != null) {
            this.staffManager.cleanup();
        }
        getLogger().info("Moderatio plugin disabled. All staff modes have been safely restored.");
    }

    private void registerCommands() {
        StaffCommand staffCmd = new StaffCommand(this);
        ModeratioCommand moderatioCmd = new ModeratioCommand(this);

        // Try standard getCommand first
        PluginCommand staffPluginCmd = getCommand("staff");
        if (staffPluginCmd != null) {
            staffPluginCmd.setExecutor(staffCmd);
            staffPluginCmd.setTabCompleter(staffCmd);
        } else {
            registerCommandViaMap("staff", "Toggles staff mode", "/staff", List.of("sm", "staffmode"), staffCmd);
        }

        PluginCommand modPluginCmd = getCommand("moderatio");
        if (modPluginCmd != null) {
            modPluginCmd.setExecutor(moderatioCmd);
            modPluginCmd.setTabCompleter(moderatioCmd);
        } else {
            registerCommandViaMap("moderatio", "Moderatio administrative command", "/moderatio [reload]", List.of("mod"), moderatioCmd);
        }
    }

    private void registerCommandViaMap(String name, String description, String usage, List<String> aliases, Object handler) {
        CommandMap commandMap = Bukkit.getCommandMap();
        Command command = new Command(name, description, usage, aliases) {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                if (handler instanceof StaffCommand sc) {
                    return sc.onCommand(sender, this, commandLabel, args);
                } else if (handler instanceof ModeratioCommand mc) {
                    return mc.onCommand(sender, this, commandLabel, args);
                }
                return false;
            }

            @Override
            public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
                if (handler instanceof StaffCommand sc) {
                    List<String> list = sc.onTabComplete(sender, this, alias, args);
                    return list != null ? list : super.tabComplete(sender, alias, args);
                } else if (handler instanceof ModeratioCommand mc) {
                    List<String> list = mc.onTabComplete(sender, this, alias, args);
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
}
