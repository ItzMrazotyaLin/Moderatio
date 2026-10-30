package org.itzmr.moderatio.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.itzmr.moderatio.Moderatio;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages configuration files: config.yml and messages.yml.
 */
public class ConfigManager {

    private final Moderatio plugin;
    private File configFile;
    private FileConfiguration config;
    private File messagesFile;
    private FileConfiguration messages;
    private File dbConfigFile;
    private FileConfiguration dbConfig;

    public ConfigManager(Moderatio plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * Loads or reloads all configuration files after checking for structure updates.
     */
    public void load() {
        // Smart synchronization of all configuration files with JAR defaults
        ConfigUpdater.updateAll(plugin, "config.yml", "messages.yml", "db.yml");

        // Load config.yml
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.configFile = new File(plugin.getDataFolder(), "config.yml");

        // Load messages.yml
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Load db.yml
        this.dbConfigFile = new File(plugin.getDataFolder(), "db.yml");
        this.dbConfig = YamlConfiguration.loadConfiguration(dbConfigFile);
    }

    /**
     * Reloads configuration and messages files from disk.
     */
    public void reload() {
        load();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public FileConfiguration getDbConfig() {
        return dbConfig;
    }

    /**
     * Gets a message string with prefix prepended (if available).
     *
     * @param path path in messages.yml
     * @return formatted message
     */
    public String getMessage(String path) {
        return getMessage(path, true);
    }

    /**
     * Gets a message string with option to include prefix.
     *
     * @param path       path in messages.yml
     * @param withPrefix whether to prepend the prefix
     * @return formatted message
     */
    public String getMessage(String path, boolean withPrefix) {
        String msg = messages.getString(path, "<red>Missing message: " + path + "</red>");
        if (withPrefix) {
            String prefix = messages.getString("prefix", "");
            return prefix + msg;
        }
        return msg;
    }

    /**
     * Gets a message string with placeholders replaced and prefix prepended.
     *
     * @param path         path in messages.yml
     * @param placeholders map of placeholders
     * @return formatted message
     */
    public String getMessage(String path, Map<String, String> placeholders) {
        return getMessage(path, placeholders, true);
    }

    /**
     * Gets a message string with placeholders replaced and option to include prefix.
     *
     * @param path         path in messages.yml
     * @param placeholders map of placeholders
     * @param withPrefix   whether to prepend the prefix
     * @return formatted message
     */
    public String getMessage(String path, Map<String, String> placeholders, boolean withPrefix) {
        String msg = getMessage(path, withPrefix);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                msg = msg.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
            }
        }
        return msg;
    }

    /**
     * Gets keys of configured ban templates.
     */
    public Set<String> getBanTemplateKeys() {
        if (config.isConfigurationSection("ban-templates")) {
            return config.getConfigurationSection("ban-templates").getKeys(false);
        }
        return Collections.emptySet();
    }

    /**
     * Gets keys of configured mute templates.
     */
    public Set<String> getMuteTemplateKeys() {
        if (config.isConfigurationSection("mute-templates")) {
            return config.getConfigurationSection("mute-templates").getKeys(false);
        }
        return Collections.emptySet();
    }

    /**
     * Gets keys of configured warn templates.
     */
    public Set<String> getWarnTemplateKeys() {
        if (config.isConfigurationSection("warn-templates")) {
            return config.getConfigurationSection("warn-templates").getKeys(false);
        }
        return Collections.emptySet();
    }

    /**
     * Gets warn expire duration string (e.g. '1d').
     */
    public String getWarnExpireDuration() {
        return config.getString("warns.expire-duration", "1d");
    }

    /**
     * Gets warn mode ('NOTIFICATION' or 'PUNISHMENT').
     */
    public String getWarnMode() {
        return config.getString("warns.mode", "NOTIFICATION").toUpperCase();
    }

    /**
     * Gets warn limit threshold for category.
     */
    public int getWarnLimitMax(String category) {
        String cat = (category != null && category.equalsIgnoreCase("GAME")) ? "game" : "chat";
        return config.getInt("warns.limits." + cat + ".max", 3);
    }

    /**
     * Gets punishment duration for category warn escalation.
     */
    public String getWarnPunishDuration(String category) {
        String cat = (category != null && category.equalsIgnoreCase("GAME")) ? "game" : "chat";
        String def = "game".equals(cat) ? "1d" : "12h";
        return config.getString("warns.limits." + cat + ".punish-duration", def);
    }

    /**
     * Checks if trolling whitelist is enabled.
     */
    public boolean isTrollingWhitelistEnabled() {
        return config.getBoolean("trolling.whitelist-enabled", false);
    }

    /**
     * Gets trolling whitelist list of players/UUIDs.
     */
    public List<String> getTrollingWhitelist() {
        return config.getStringList("trolling.whitelist");
    }

    /**
     * Gets configured punishment limit for anti-abuse window.
     */
    public int getAntiAbusePunishmentLimit() {
        return config.getInt("anti-abuse.punishment-limit", 2);
    }

    /**
     * Gets configured anti-abuse check interval in seconds.
     */
    public int getAntiAbuseCheckIntervalSeconds() {
        return config.getInt("anti-abuse.check-interval-seconds", 60);
    }

    /**
     * Checks if punishments should be blocked when limit is reached.
     */
    public boolean isAntiAbuseBlockPunishmentsOnLimit() {
        return config.getBoolean("anti-abuse.block-punishments-on-limit", true);
    }

    /**
     * Checks if cheat check integration is enabled.
     */
    public boolean isCheatCheckEnabled() {
        return config.getBoolean("cheat-check.enabled", true);
    }

    /**
     * Gets executor mode for cheat check command ('MODERATOR' or 'CONSOLE').
     */
    public String getCheatCheckExecutor() {
        return config.getString("cheat-check.executor", "MODERATOR");
    }

    /**
     * Gets cheat check command template (fallback).
     */
    public String getCheatCheckCommand() {
        return getCheatCheckStartCommand();
    }

    /**
     * Gets start command template for cheat check.
     */
    public String getCheatCheckStartCommand() {
        return config.getString("cheat-check.start-command",
                config.getString("cheat-check.command", "check {target}"));
    }

    /**
     * Gets finish command template for cheat check.
     */
    public String getCheatCheckFinishCommand() {
        return config.getString("cheat-check.finish-command", "uncheck {target}");
    }

    /**
     * Checks if auto detection of chat check commands is enabled.
     */
    public boolean isCheatCheckAutoDetectChatCommand() {
        return config.getBoolean("cheat-check.auto-detect-chat-command", true);
    }

    /**
     * Gets the trigger command for starting a check from chat (without leading slash).
     */
    public String getCheatCheckTriggerCommand() {
        return config.getString("cheat-check.trigger-command", "check");
    }

    /**
     * Gets the trigger command for finishing a check from chat (without leading slash).
     */
    public String getCheatCheckFinishTriggerCommand() {
        return config.getString("cheat-check.finish-trigger-command", "uncheck");
    }

    /**
     * Checks if automatic database cleanup of expired and inactive records is enabled.
     */
    public boolean isDbCleanupEnabled() {
        return dbConfig != null && dbConfig.getBoolean("cleanup.enabled", true);
    }

    /**
     * Gets the number of days to retain inactive records in the database.
     */
    public int getDbCleanupRetainDays() {
        return dbConfig != null ? dbConfig.getInt("cleanup.retain-days", 30) : 30;
    }
}
