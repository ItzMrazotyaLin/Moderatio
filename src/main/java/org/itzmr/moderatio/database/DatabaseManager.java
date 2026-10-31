package org.itzmr.moderatio.database;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.itzmr.moderatio.Moderatio;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Manages database connectivity (SQLite and MySQL) and asynchronous persistence operations.
 */
public class DatabaseManager {

    private final Moderatio plugin;
    private final String storageType;
    private final ExecutorService dbExecutor;

    private String sqliteUrl;
    private Connection sqliteConnection;

    private String mySqlUrl;
    private String mySqlUser;
    private String mySqlPass;

    private BukkitTask cleanupTask;

    @FunctionalInterface
    private interface DatabaseAction<T> {
        T run(Connection connection) throws SQLException;
    }

    public DatabaseManager(Moderatio plugin) {
        this.plugin = plugin;
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "Moderatio-Database-Worker");
            thread.setDaemon(true);
            return thread;
        });

        FileConfiguration dbConfig = plugin.getConfigManager().getDbConfig();
        this.storageType = dbConfig != null ? dbConfig.getString("storage-type", "SQLITE").toUpperCase() : "SQLITE";

        setupConnectionSettings(dbConfig);
    }

    private void setupConnectionSettings(FileConfiguration dbConfig) {
        if (isSqlite()) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().warning("SQLite JDBC driver was not found on classpath!");
            }

            String fileName = dbConfig != null ? dbConfig.getString("sqlite.file", "database/data.db") : "database/data.db";
            File dbFile = new File(plugin.getDataFolder(), fileName);
            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Migrate from root data.db if present
            File oldFile = new File(plugin.getDataFolder(), "data.db");
            if (oldFile.exists() && !dbFile.exists()) {
                try {
                    java.nio.file.Files.move(oldFile.toPath(), dbFile.toPath());
                    plugin.getLogger().info("[Moderatio] Файл базы данных перемещён в " + fileName);
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not move old data.db to " + fileName + ": " + e.getMessage());
                }
            }

            this.sqliteUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        } else {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                try {
                    Class.forName("com.mysql.jdbc.Driver");
                } catch (ClassNotFoundException ex) {
                    plugin.getLogger().warning("MySQL JDBC driver was not found on classpath!");
                }
            }

            String host = dbConfig != null ? dbConfig.getString("mysql.host", "localhost") : "localhost";
            int port = dbConfig != null ? dbConfig.getInt("mysql.port", 3306) : 3306;
            String database = dbConfig != null ? dbConfig.getString("mysql.database", "moderatio") : "moderatio";
            this.mySqlUser = dbConfig != null ? dbConfig.getString("mysql.username", "root") : "root";
            this.mySqlPass = dbConfig != null ? dbConfig.getString("mysql.password", "") : "";
            this.mySqlUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true&characterEncoding=utf8";
        }
    }

    /**
     * Initializes tables and periodic cleanup task asynchronously.
     */
    public CompletableFuture<Void> init() {
        return CompletableFuture.runAsync(() -> {
            try {
                createTables();
                plugin.getLogger().info("[Moderatio] База данных (" + storageType + ") успешно инициализирована.");
            } catch (SQLException e) {
                plugin.getLogger().severe("[Moderatio] Ошибка при создании таблиц базы данных: " + e.getMessage());
                e.printStackTrace();
            }

            FileConfiguration dbConfig = plugin.getConfigManager().getDbConfig();
            int cleanupInterval = dbConfig != null ? dbConfig.getInt("cleanup-interval-seconds", 60) : 60;
            if (cleanupInterval > 0) {
                this.cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                        this::cleanupExpiredPunishments, cleanupInterval * 20L, cleanupInterval * 20L);
            }

            // Run expired data purge on startup if enabled
            if (plugin.getConfigManager().isDbCleanupEnabled()) {
                purgeExpiredData(plugin.getConfigManager().getDbCleanupRetainDays());
            }
        }, dbExecutor);
    }

    public boolean isSqlite() {
        return !"MYSQL".equalsIgnoreCase(storageType);
    }

    public String getStorageType() {
        return storageType;
    }

    private synchronized void ensureSqliteConnected() throws SQLException {
        if (sqliteConnection == null || sqliteConnection.isClosed()) {
            sqliteConnection = DriverManager.getConnection(sqliteUrl);
            sqliteConnection.setAutoCommit(true);
            try (Statement stmt = sqliteConnection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA busy_timeout=5000;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
            }
        }
    }

    private <T> T execute(DatabaseAction<T> action) throws SQLException {
        if (isSqlite()) {
            ensureSqliteConnected();
            if (!sqliteConnection.getAutoCommit()) {
                sqliteConnection.setAutoCommit(true);
            }
            return action.run(sqliteConnection);
        } else {
            try (Connection conn = DriverManager.getConnection(mySqlUrl, mySqlUser, mySqlPass)) {
                return action.run(conn);
            }
        }
    }

    private void createTables() throws SQLException {
        String idType = isSqlite() ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "INT AUTO_INCREMENT PRIMARY KEY";

        String punishmentsSql = "CREATE TABLE IF NOT EXISTS moderatio_punishments (" +
                "id " + idType + ", " +
                "type VARCHAR(16) NOT NULL, " +
                "target_uuid VARCHAR(36) NOT NULL, " +
                "target_name TEXT NOT NULL, " +
                "staff_uuid VARCHAR(36) NOT NULL, " +
                "staff_name TEXT NOT NULL, " +
                "reason TEXT NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "expires_at BIGINT NOT NULL, " +
                "active BOOLEAN NOT NULL DEFAULT 1, " +
                "category VARCHAR(16) NOT NULL DEFAULT 'CHAT'" +
                ");";

        String staffLogsSql = "CREATE TABLE IF NOT EXISTS moderatio_staff_logs (" +
                "id " + idType + ", " +
                "staff_uuid VARCHAR(36) NOT NULL, " +
                "staff_name TEXT NOT NULL, " +
                "action_type VARCHAR(32) NOT NULL, " +
                "details TEXT, " +
                "world VARCHAR(64), " +
                "x DOUBLE, " +
                "y DOUBLE, " +
                "z DOUBLE, " +
                "timestamp BIGINT NOT NULL" +
                ");";

        String indexPunishments = "CREATE INDEX IF NOT EXISTS idx_punishments_target ON moderatio_punishments(target_uuid, active);";
        String indexLogs = "CREATE INDEX IF NOT EXISTS idx_staff_logs_staff ON moderatio_staff_logs(staff_uuid);";

        execute(conn -> {
            try (Statement st = conn.createStatement()) {
                st.execute(punishmentsSql);
                try {
                    st.execute("ALTER TABLE moderatio_punishments ADD COLUMN category VARCHAR(16) NOT NULL DEFAULT 'CHAT';");
                } catch (SQLException ignored) {
                }
                st.execute(staffLogsSql);
                try {
                    st.execute(indexPunishments);
                    st.execute(indexLogs);
                } catch (SQLException ignored) {
                }
            }
            return null;
        });
    }

    /**
     * Persists a punishment asynchronously and assigns its generated ID.
     */
    public CompletableFuture<Void> addPunishment(Punishment punishment) {
        if (punishment == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO moderatio_punishments (type, target_uuid, target_name, staff_uuid, staff_name, reason, created_at, expires_at, active, category) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
            try {
                execute(conn -> {
                    if (!conn.getAutoCommit()) {
                        conn.setAutoCommit(true);
                    }
                    try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, punishment.getType().name());
                        ps.setString(2, punishment.getTargetUuid().toString());
                        ps.setString(3, punishment.getTargetName() != null ? punishment.getTargetName() : "Unknown");
                        ps.setString(4, punishment.getStaffUuid() != null ? punishment.getStaffUuid().toString() : "00000000-0000-0000-0000-000000000000");
                        ps.setString(5, punishment.getStaffName() != null ? punishment.getStaffName() : "Console");
                        ps.setString(6, punishment.getReason() != null ? punishment.getReason() : "Нарушение правил");
                        ps.setLong(7, punishment.getCreatedAt());
                        ps.setLong(8, punishment.getExpiresAt());
                        ps.setBoolean(9, punishment.isActive());
                        ps.setString(10, punishment.getCategory() != null ? punishment.getCategory() : "CHAT");
                        ps.executeUpdate();

                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (rs.next()) {
                                punishment.setId(rs.getInt(1));
                            }
                        }
                    }
                    return null;
                });
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, dbExecutor).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Ошибка записи наказания в БД:", ex);
            return null;
        });
    }

    /**
     * Retrieves all active punishments for a target player asynchronously.
     * Automatically deactivates expired entries.
     */
    public CompletableFuture<List<Punishment>> getActivePunishments(UUID targetUuid) {
        if (targetUuid == null) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> list = new ArrayList<>();
            String sql = "SELECT id, type, target_uuid, target_name, staff_uuid, staff_name, reason, created_at, expires_at, active, category " +
                    "FROM moderatio_punishments WHERE target_uuid = ? AND active = 1;";
            try {
                execute(conn -> {
                    long now = System.currentTimeMillis();
                    List<Integer> expiredIds = new ArrayList<>();

                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, targetUuid.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                int id = rs.getInt("id");
                                PunishmentType type = PunishmentType.fromString(rs.getString("type"));
                                UUID tUuid = UUID.fromString(rs.getString("target_uuid"));
                                String tName = rs.getString("target_name");
                                UUID sUuid = UUID.fromString(rs.getString("staff_uuid"));
                                String sName = rs.getString("staff_name");
                                String reason = rs.getString("reason");
                                long createdAt = rs.getLong("created_at");
                                long expiresAt = rs.getLong("expires_at");
                                boolean active = rs.getBoolean("active");
                                String category = "CHAT";
                                try {
                                    category = rs.getString("category");
                                    if (category == null || category.isBlank()) category = "CHAT";
                                } catch (SQLException ignored) {
                                }

                                if (expiresAt > 0 && now > expiresAt) {
                                    expiredIds.add(id);
                                } else {
                                    list.add(new Punishment(id, type, tUuid, tName, sUuid, sName, reason, createdAt, expiresAt, active, category));
                                }
                            }
                        }
                    }

                    if (!expiredIds.isEmpty()) {
                        String updateSql = "UPDATE moderatio_punishments SET active = 0 WHERE id = ?;";
                        try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                            for (int expId : expiredIds) {
                                updatePs.setInt(1, expId);
                                updatePs.addBatch();
                            }
                            updatePs.executeBatch();
                        }
                    }
                    return null;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to retrieve active punishments for " + targetUuid + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
            return list;
        }, dbExecutor);
    }

    /**
     * Loads all active bans from database asynchronously.
     */
    public CompletableFuture<List<Punishment>> loadActiveBans() {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> list = new ArrayList<>();
            String sql = "SELECT id, type, target_uuid, target_name, staff_uuid, staff_name, reason, created_at, expires_at, active, category " +
                    "FROM moderatio_punishments WHERE type = 'BAN' AND active = 1 AND (expires_at = -1 OR expires_at > ?);";
            try {
                execute(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setLong(1, System.currentTimeMillis());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                int id = rs.getInt("id");
                                PunishmentType type = PunishmentType.fromString(rs.getString("type"));
                                UUID tUuid = UUID.fromString(rs.getString("target_uuid"));
                                String tName = rs.getString("target_name");
                                UUID sUuid = UUID.fromString(rs.getString("staff_uuid"));
                                String sName = rs.getString("staff_name");
                                String reason = rs.getString("reason");
                                long createdAt = rs.getLong("created_at");
                                long expiresAt = rs.getLong("expires_at");
                                boolean active = rs.getBoolean("active");
                                String category = "GAME";
                                try {
                                    category = rs.getString("category");
                                    if (category == null || category.isBlank()) category = "GAME";
                                } catch (SQLException ignored) {
                                }
                                list.add(new Punishment(id, type, tUuid, tName, sUuid, sName, reason, createdAt, expiresAt, active, category));
                            }
                        }
                    }
                    return null;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load active bans: " + e.getMessage());
            }
            return list;
        }, dbExecutor);
    }

    /**
     * Deactivates all active punishments of a specific type for a target.
     */
    public CompletableFuture<Void> deactivatePunishments(UUID targetUuid, PunishmentType type) {
        if (targetUuid == null || type == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE moderatio_punishments SET active = 0 WHERE target_uuid = ? AND type = ? AND active = 1;";
            try {
                execute(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, targetUuid.toString());
                        ps.setString(2, type.name());
                        ps.executeUpdate();
                    }
                    return null;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to deactivate " + type + " for " + targetUuid + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, dbExecutor).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Ошибка деактивации наказания в БД:", ex);
            return null;
        });
    }

    /**
     * Deactivates a punishment by ID asynchronously.
     */
    public CompletableFuture<Void> deactivatePunishment(int id) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE moderatio_punishments SET active = 0 WHERE id = ?;";
            try {
                execute(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    }
                    return null;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to deactivate punishment " + id + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, dbExecutor).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Ошибка деактивации наказания в БД:", ex);
            return null;
        });
    }

    /**
     * Retrieves all punishments issued by a staff member by UUID asynchronously.
     */
    public CompletableFuture<List<Punishment>> getPunishmentsByStaff(UUID staffUuid) {
        if (staffUuid == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> list = new ArrayList<>();
            String sql = "SELECT id, type, target_uuid, target_name, staff_uuid, staff_name, reason, created_at, expires_at, active, category " +
                    "FROM moderatio_punishments WHERE staff_uuid = ? ORDER BY created_at DESC;";
            try {
                execute(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, staffUuid.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                list.add(mapPunishment(rs));
                            }
                        }
                    }
                    return null;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to retrieve punishments by staff " + staffUuid + ": " + e.getMessage());
            }
            return list;
        }, dbExecutor);
    }

    /**
     * Retrieves all punishments issued by a staff member by name asynchronously.
     */
    public CompletableFuture<List<Punishment>> getPunishmentsByStaffName(String staffName) {
        if (staffName == null || staffName.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> list = new ArrayList<>();
            String sql = "SELECT id, type, target_uuid, target_name, staff_uuid, staff_name, reason, created_at, expires_at, active, category " +
                    "FROM moderatio_punishments WHERE LOWER(staff_name) = LOWER(?) ORDER BY created_at DESC;";
            try {
                execute(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, staffName.trim());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                list.add(mapPunishment(rs));
                            }
                        }
                    }
                    return null;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to retrieve punishments by staff name " + staffName + ": " + e.getMessage());
            }
            return list;
        }, dbExecutor);
    }

    /**
     * Retrieves all punishments received by a target player asynchronously.
     */
    public CompletableFuture<List<Punishment>> getPunishmentsByTarget(UUID targetUuid) {
        if (targetUuid == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> list = new ArrayList<>();
            String sql = "SELECT id, type, target_uuid, target_name, staff_uuid, staff_name, reason, created_at, expires_at, active, category " +
                    "FROM moderatio_punishments WHERE target_uuid = ? ORDER BY created_at DESC;";
            try {
                execute(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, targetUuid.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                list.add(mapPunishment(rs));
                            }
                        }
                    }
                    return null;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to retrieve punishments by target " + targetUuid + ": " + e.getMessage());
            }
            return list;
        }, dbExecutor);
    }

    /**
     * Retrieves all unique staff names that have ever issued punishments or performed actions.
     */
    public CompletableFuture<Set<String>> getAllStaffNames() {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> set = new LinkedHashSet<>();
            String sql = "SELECT DISTINCT staff_name FROM moderatio_punishments UNION SELECT DISTINCT staff_name FROM moderatio_staff_logs;";
            try {
                execute(conn -> {
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(sql)) {
                        while (rs.next()) {
                            String name = rs.getString("staff_name");
                            if (name != null && !name.isBlank() && !name.equalsIgnoreCase("Console")) {
                                set.add(name);
                            }
                        }
                    }
                    return null;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to retrieve staff names: " + e.getMessage());
            }
            return set;
        }, dbExecutor);
    }

    private Punishment mapPunishment(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        PunishmentType type = PunishmentType.fromString(rs.getString("type"));
        UUID tUuid = UUID.fromString(rs.getString("target_uuid"));
        String tName = rs.getString("target_name");
        UUID sUuid = UUID.fromString(rs.getString("staff_uuid"));
        String sName = rs.getString("staff_name");
        String reason = rs.getString("reason");
        long createdAt = rs.getLong("created_at");
        long expiresAt = rs.getLong("expires_at");
        boolean active = rs.getBoolean("active");
        String category = "CHAT";
        try {
            category = rs.getString("category");
            if (category == null || category.isBlank()) category = "CHAT";
        } catch (SQLException ignored) {
        }
        return new Punishment(id, type, tUuid, tName, sUuid, sName, reason, createdAt, expiresAt, active, category);
    }

    /**
     * Logs a staff member's action into moderatio_staff_logs asynchronously.
     */
    public CompletableFuture<Void> logStaffAction(StaffLog log) {
        if (log == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO moderatio_staff_logs (staff_uuid, staff_name, action_type, details, world, x, y, z, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
            try {
                execute(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, log.getStaffUuid().toString());
                        ps.setString(2, log.getStaffName());
                        ps.setString(3, log.getActionType());
                        ps.setString(4, log.getDetails());
                        ps.setString(5, log.getWorld());
                        ps.setDouble(6, log.getX());
                        ps.setDouble(7, log.getY());
                        ps.setDouble(8, log.getZ());
                        ps.setLong(9, log.getTimestamp());
                        ps.executeUpdate();

                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (rs.next()) {
                                log.setId(rs.getInt(1));
                            }
                        }
                    }
                    return null;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to insert staff log into database: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, dbExecutor).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Ошибка записи лога сотрудника в БД:", ex);
            return null;
        });
    }

    /**
     * Periodically cleans up expired active punishments.
     */
    public CompletableFuture<Integer> cleanupExpiredPunishments() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE moderatio_punishments SET active = 0 WHERE active = 1 AND expires_at > 0 AND expires_at < ?;";
            try {
                return execute(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setLong(1, System.currentTimeMillis());
                        return ps.executeUpdate();
                    }
                });
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to clean up expired punishments: " + e.getMessage());
                return 0;
            }
        }, dbExecutor);
    }

    /**
     * Purges expired logs and finished/canceled punishments older than retainDays.
     * Active and permanent bans/mutes are NEVER deleted!
     *
     * @param retainDays number of days to retain inactive/expired records
     */
    public CompletableFuture<Void> purgeExpiredData(int retainDays) {
        return CompletableFuture.runAsync(() -> {
            long now = System.currentTimeMillis();
            long threshold = now - (retainDays * 86_400_000L);

            String deleteLogsSql = "DELETE FROM moderatio_staff_logs WHERE timestamp < ?;";
            String deletePunishmentsSql = "DELETE FROM moderatio_punishments " +
                    "WHERE (active = 0 OR (expires_at != -1 AND expires_at < ?)) AND created_at < ?;";

            try {
                execute(conn -> {
                    // 1. Delete old staff logs
                    try (PreparedStatement psLogs = conn.prepareStatement(deleteLogsSql)) {
                        psLogs.setLong(1, threshold);
                        psLogs.executeUpdate();
                    }

                    // 2. Delete old finished/canceled punishments
                    try (PreparedStatement psPunishments = conn.prepareStatement(deletePunishmentsSql)) {
                        psPunishments.setLong(1, now);
                        psPunishments.setLong(2, threshold);
                        psPunishments.executeUpdate();
                    }
                    return null;
                });

                plugin.getLogger().info("[Moderatio] База данных очищена от устаревших записей старше " + retainDays + " дн.");
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to purge expired database records: " + e.getMessage());
            }
        }, dbExecutor);
    }

    /**
     * Reloads database settings and triggers cleanup if enabled.
     */
    public void reload() {
        if (plugin.getConfigManager().isDbCleanupEnabled()) {
            purgeExpiredData(plugin.getConfigManager().getDbCleanupRetainDays());
        }
    }

    /**
     * Safely closes connection pool and shuts down executor.
     */
    public void close() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (sqliteConnection != null) {
            try {
                if (!sqliteConnection.isClosed()) {
                    sqliteConnection.close();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing SQLite connection: " + e.getMessage());
            }
        }
    }
}
