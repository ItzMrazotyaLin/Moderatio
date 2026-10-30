package org.itzmr.moderatio.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility for safe and intelligent synchronization of configuration files with JAR defaults.
 * Preserves existing user settings, comments, and structure while migrating missing keys.
 */
public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    /**
     * Updates multiple YAML configuration files if their structure differs from the JAR resource.
     *
     * @param plugin    the plugin instance
     * @param fileNames array of configuration file names (e.g. config.yml, messages.yml, db.yml)
     */
    public static void updateAll(JavaPlugin plugin, String... fileNames) {
        if (plugin == null || fileNames == null) {
            return;
        }
        for (String fileName : fileNames) {
            update(plugin, fileName);
        }
    }

    /**
     * Safely updates a specific YAML configuration file.
     *
     * @param plugin   the plugin instance
     * @param fileName file name inside data folder and JAR resources
     */
    public static void update(JavaPlugin plugin, String fileName) {
        if (plugin == null || fileName == null || fileName.isBlank()) {
            return;
        }

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File file = new File(dataFolder, fileName);

        // 1. If file does not exist on disk, save default resource
        if (!file.exists()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Could not find resource " + fileName + " in JAR.");
            }
            return;
        }

        // 2. If file exists, check structure against JAR resource
        try (InputStream jarStream = plugin.getResource(fileName)) {
            if (jarStream == null) {
                return;
            }

            YamlConfiguration diskConfig = YamlConfiguration.loadConfiguration(file);
            YamlConfiguration jarConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(jarStream, StandardCharsets.UTF_8)
            );

            // Compare keys: find if any key from jarConfig is missing in diskConfig
            boolean hasMissingKeys = false;
            for (String key : jarConfig.getKeys(true)) {
                if (!diskConfig.contains(key)) {
                    hasMissingKeys = true;
                    break;
                }
            }

            if (!hasMissingKeys) {
                return;
            }

            plugin.getLogger().info("[Moderatio] Обнаружено обновление структуры для " + fileName + ". Выполняется мягкая миграция...");

            // Create backup before modifying
            File backupDir = new File(dataFolder, "backups");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            File backupFile = new File(backupDir, fileName + ".bak");
            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Save user values from disk into memory
            Map<String, Object> userValues = new LinkedHashMap<>();
            for (String key : diskConfig.getKeys(true)) {
                if (diskConfig.isConfigurationSection(key)) {
                    var sec = diskConfig.getConfigurationSection(key);
                    if (sec != null && sec.getKeys(false).isEmpty()) {
                        userValues.put(key, Collections.emptyMap());
                    }
                } else {
                    userValues.put(key, diskConfig.get(key));
                }
            }

            // Overwrite file with fresh copy from JAR
            plugin.saveResource(fileName, true);

            // Load fresh file and restore user values only for keys that existed on disk
            YamlConfiguration updatedConfig = YamlConfiguration.loadConfiguration(file);
            for (Map.Entry<String, Object> entry : userValues.entrySet()) {
                updatedConfig.set(entry.getKey(), entry.getValue());
            }

            // Save migrated YAML
            updatedConfig.save(file);
            plugin.getLogger().info("[Moderatio] Файл " + fileName + " успешно обновлён. Бэкап сохранён в backups/" + fileName + ".bak");
        } catch (IOException e) {
            plugin.getLogger().severe("[Moderatio] Ошибка при обновлении структуры " + fileName + ": " + e.getMessage());
        }
    }
}
