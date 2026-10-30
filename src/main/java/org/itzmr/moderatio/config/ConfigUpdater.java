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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility for safe and intelligent synchronization of configuration files with JAR defaults.
 * Preserves existing user settings, comments, and structure while migrating missing keys
 * and cleanly eliminating deprecated sections.
 */
public final class ConfigUpdater {

    private static final Map<String, String> KEY_MIGRATIONS = Map.of(
            "cheat-check", "check-system"
    );

    private static final List<String> DEPRECATED_ROOT_KEYS = List.of(
            "cheat-check",
            "ban-system",
            "mute-system"
    );

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

            // Compare keys: find if any key from jarConfig is missing in diskConfig or if disk has deprecated keys
            boolean hasMissingKeys = false;
            for (String key : jarConfig.getKeys(true)) {
                if (!diskConfig.contains(key)) {
                    hasMissingKeys = true;
                    break;
                }
            }

            boolean hasDeprecatedKeys = false;
            for (String dep : DEPRECATED_ROOT_KEYS) {
                if (diskConfig.contains(dep)) {
                    hasDeprecatedKeys = true;
                    break;
                }
            }

            if (!hasMissingKeys && !hasDeprecatedKeys) {
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

            // Save user values from disk into memory (filtering and migrating deprecated keys)
            Map<String, Object> userValues = new LinkedHashMap<>();
            for (String key : diskConfig.getKeys(true)) {
                // Check if key belongs to a deprecated root
                String matchingDeprecatedRoot = null;
                for (String dep : DEPRECATED_ROOT_KEYS) {
                    if (key.equals(dep) || key.startsWith(dep + ".")) {
                        matchingDeprecatedRoot = dep;
                        break;
                    }
                }

                if (matchingDeprecatedRoot != null) {
                    String replacementRoot = KEY_MIGRATIONS.get(matchingDeprecatedRoot);
                    if (replacementRoot != null) {
                        String suffix = key.substring(matchingDeprecatedRoot.length());
                        String migratedKey = replacementRoot + suffix;
                        if (!diskConfig.isConfigurationSection(key)) {
                            userValues.put(migratedKey, diskConfig.get(key));
                        }
                    }
                    // Skip storing under the deprecated name
                    continue;
                }

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

            // Forcibly eliminate all deprecated root keys
            for (String dep : DEPRECATED_ROOT_KEYS) {
                updatedConfig.set(dep, null);
            }

            // Save migrated YAML
            updatedConfig.save(file);
            plugin.getLogger().info("[Moderatio] Файл " + fileName + " успешно обновлён. Бэкап сохранён в backups/" + fileName + ".bak");
        } catch (IOException e) {
            plugin.getLogger().severe("[Moderatio] Ошибка при обновлении структуры " + fileName + ": " + e.getMessage());
        }
    }
}
