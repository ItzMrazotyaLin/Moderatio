package org.itzmr.moderatio.staff;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.utils.MessageUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-moderator client-side glowing for suspected target players.
 * The glowing flag (0x40) is sent strictly via packets to the moderator's connection.
 * The target player NEVER receives the glowing flag and NEVER knows they are being watched.
 */
public class GlowingManager {

    private static final String TEAM_NAME = "mod_glow";

    private final Moderatio plugin;
    // Moderator UUID -> Set of target UUIDs being watched
    private final Map<UUID, Set<UUID>> activeGlows = new ConcurrentHashMap<>();
    // Target UUID -> Set of moderator UUIDs watching them
    private final Map<UUID, Set<UUID>> targetWatchers = new ConcurrentHashMap<>();
    private BukkitTask refreshTask = null;

    // Reflection caching for per-player entity metadata packets
    private static volatile boolean reflectionInitialized = false;
    private static volatile boolean reflectionAvailable = false;
    private static Method getHandlePlayerMethod;
    private static Field connectionField;
    private static Method sendPacketMethod;
    private static Constructor<?> packetConstructor;
    private static Constructor<?> dataValueConstructor;
    private static Method getEntityDataMethod;
    private static Method packAllMethod;
    private static Method idMethod;
    private static Method serializerMethod;
    private static Method valueMethod;
    private static Object cachedByteSerializer;

    public GlowingManager(Moderatio plugin) {
        this.plugin = plugin;
    }

    private synchronized void initReflection() {
        if (reflectionInitialized) return;
        try {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            getHandlePlayerMethod = craftPlayerClass.getMethod("getHandle");

            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            try {
                connectionField = serverPlayerClass.getField("connection");
            } catch (NoSuchFieldException e) {
                for (Field f : serverPlayerClass.getDeclaredFields()) {
                    if (f.getName().equals("connection") || f.getType().getSimpleName().contains("PacketListener")) {
                        f.setAccessible(true);
                        connectionField = f;
                        break;
                    }
                }
            }

            Class<?> connectionClass = connectionField.getType();
            for (Method m : connectionClass.getMethods()) {
                if (m.getName().equals("send") && m.getParameterCount() == 1 && m.getParameterTypes()[0].getSimpleName().contains("Packet")) {
                    sendPacketMethod = m;
                    break;
                }
            }

            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
            packetConstructor = packetClass.getConstructor(int.class, List.class);

            Class<?> dataValueClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
            for (Constructor<?> c : dataValueClass.getConstructors()) {
                if (c.getParameterCount() == 3) {
                    dataValueConstructor = c;
                    break;
                }
            }

            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            getEntityDataMethod = entityClass.getMethod("getEntityData");

            Class<?> synchedDataClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
            packAllMethod = synchedDataClass.getMethod("packAll");

            idMethod = dataValueClass.getMethod("id");
            serializerMethod = dataValueClass.getMethod("serializer");
            valueMethod = dataValueClass.getMethod("value");

            try {
                Class<?> serializersClass = Class.forName("net.minecraft.network.syncher.EntityDataSerializers");
                Field byteField = serializersClass.getField("BYTE");
                cachedByteSerializer = byteField.get(null);
            } catch (Throwable ignored) {
            }

            reflectionAvailable = (getHandlePlayerMethod != null && connectionField != null && sendPacketMethod != null
                    && packetConstructor != null && dataValueConstructor != null && getEntityDataMethod != null
                    && packAllMethod != null && idMethod != null && serializerMethod != null && valueMethod != null);

            if (reflectionAvailable) {
                plugin.getLogger().info("Per-moderator glowing packet pipeline successfully initialized.");
            } else {
                plugin.getLogger().warning("Glowing packet pipeline reflection incomplete.");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to initialize Glowing packet reflection: " + t.getMessage());
            reflectionAvailable = false;
        } finally {
            reflectionInitialized = true;
        }
    }

    /**
     * Sends entity metadata packet with glowing status EXCLUSIVELY to the moderator's connection.
     * The target player and other players never receive this packet.
     */
    public void sendEntityMetadataGlow(Player moderator, Player target, boolean glowing) {
        if (moderator == null || target == null || !moderator.isOnline() || !target.isOnline()) {
            return;
        }
        if (!reflectionInitialized) {
            initReflection();
        }
        if (!reflectionAvailable) {
            return;
        }

        try {
            // 1. Get moderator's network connection
            Object modServerPlayer = getHandlePlayerMethod.invoke(moderator);
            Object connection = connectionField.get(modServerPlayer);
            if (connection == null) return;

            // 2. Read target's current flags and serializer from its SynchedEntityData
            Object targetServerPlayer = getHandlePlayerMethod.invoke(target);
            Object entityData = getEntityDataMethod.invoke(targetServerPlayer);
            List<?> allValues = (List<?>) packAllMethod.invoke(entityData);

            byte currentFlags = 0;
            Object byteSerializer = cachedByteSerializer;
            boolean found = false;

            if (allValues != null) {
                for (Object dv : allValues) {
                    int id = (Integer) idMethod.invoke(dv);
                    if (id == 0) {
                        currentFlags = (Byte) valueMethod.invoke(dv);
                        if (byteSerializer == null) {
                            byteSerializer = serializerMethod.invoke(dv);
                        }
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                if (target.getFireTicks() > 0) currentFlags |= 0x01;
                if (target.isSneaking()) currentFlags |= 0x02;
                if (target.isSprinting()) currentFlags |= 0x08;
                if (target.isSwimming()) currentFlags |= 0x10;
                if (target.isInvisible()) currentFlags |= 0x20;
                if (target.isGliding()) currentFlags |= (byte) 0x80;
            }

            // 3. Apply glowing bit (0x40 = 64)
            byte newFlags = glowing ? (byte) (currentFlags | 0x40) : (byte) (currentFlags & ~0x40);

            // 4. Construct DataValue and ClientboundSetEntityDataPacket
            Object dataValue = dataValueConstructor.newInstance(0, byteSerializer, newFlags);
            Object packet = packetConstructor.newInstance(target.getEntityId(), List.of(dataValue));

            // 5. Send packet EXCLUSIVELY to moderator
            sendPacketMethod.invoke(connection, packet);
        } catch (Throwable t) {
            plugin.getLogger().warning("Error sending glow packet to " + moderator.getName() + ": " + t.getMessage());
        }
    }

    private synchronized void startRefreshTaskIfNeeded() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            return;
        }
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeGlows.isEmpty()) {
                stopRefreshTask();
                return;
            }
            for (Map.Entry<UUID, Set<UUID>> entry : activeGlows.entrySet()) {
                Player mod = Bukkit.getPlayer(entry.getKey());
                if (mod == null || !mod.isOnline()) continue;
                for (UUID targetId : entry.getValue()) {
                    Player tgt = Bukkit.getPlayer(targetId);
                    if (tgt != null && tgt.isOnline() && tgt.getWorld().equals(mod.getWorld())) {
                        sendEntityMetadataGlow(mod, tgt, true);
                    }
                }
            }
        }, 10L, 10L);
    }

    private synchronized void stopRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public boolean isGlowing(UUID moderatorId, UUID targetId) {
        if (moderatorId == null || targetId == null) return false;
        Set<UUID> set = activeGlows.get(moderatorId);
        return set != null && set.contains(targetId);
    }

    /**
     * Toggles glowing of target player for the moderator.
     */
    public boolean toggleGlowing(Player moderator, Player target) {
        if (moderator == null || target == null) return false;
        if (isGlowing(moderator.getUniqueId(), target.getUniqueId())) {
            disableGlowing(moderator, target);
            return false;
        } else {
            enableGlowing(moderator, target);
            return true;
        }
    }

    /**
     * Enables glowing of target player exclusively for the specified moderator.
     * Target player NEVER receives the glowing packet and NEVER knows they are glowing.
     */
    public void enableGlowing(Player moderator, Player target) {
        if (moderator == null || target == null || !moderator.isOnline() || !target.isOnline()) {
            return;
        }

        UUID modId = moderator.getUniqueId();
        UUID targetId = target.getUniqueId();

        // 1. Ensure private scoreboard for moderator
        Scoreboard board = moderator.getScoreboard();
        if (board.equals(Bukkit.getScoreboardManager().getMainScoreboard())) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            moderator.setScoreboard(board);
        }

        // 2. Setup isolated glowing team on moderator's private scoreboard
        Team team = board.getTeam(TEAM_NAME);
        if (team == null) {
            team = board.registerNewTeam(TEAM_NAME);
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        team.color(NamedTextColor.GOLD);
        if (!team.hasEntry(target.getName())) {
            team.addEntry(target.getName());
        }

        // 3. Send packet ONLY to the moderator (target.setGlowing is NOT called to prevent leak)
        sendEntityMetadataGlow(moderator, target, true);

        activeGlows.computeIfAbsent(modId, k -> ConcurrentHashMap.newKeySet()).add(targetId);
        targetWatchers.computeIfAbsent(targetId, k -> ConcurrentHashMap.newKeySet()).add(modId);

        startRefreshTaskIfNeeded();

        plugin.getAuditService().logAction(moderator, "GLOW_ENABLE", target.getName(), null);
        MessageUtils.send(moderator, plugin.getConfigManager().getMessage("glowing.enabled", Map.of("target", target.getName())));
    }

    /**
     * Disables glowing of target player for the specified moderator.
     */
    public void disableGlowing(Player moderator, Player target) {
        if (moderator == null || target == null) return;

        UUID modId = moderator.getUniqueId();
        UUID targetId = target.getUniqueId();

        Set<UUID> set = activeGlows.get(modId);
        if (set != null) {
            set.remove(targetId);
            if (set.isEmpty()) {
                activeGlows.remove(modId);
            }
        }

        Set<UUID> watchers = targetWatchers.get(targetId);
        if (watchers != null) {
            watchers.remove(modId);
            if (watchers.isEmpty()) {
                targetWatchers.remove(targetId);
            }
        }

        if (moderator.isOnline()) {
            Scoreboard board = moderator.getScoreboard();
            Team team = board.getTeam(TEAM_NAME);
            if (team != null) {
                team.removeEntry(target.getName());
            }
            sendEntityMetadataGlow(moderator, target, false);
        }

        if (activeGlows.isEmpty()) {
            stopRefreshTask();
        }

        plugin.getAuditService().logAction(moderator, "GLOW_DISABLE", target.getName(), null);
        MessageUtils.send(moderator, plugin.getConfigManager().getMessage("glowing.disabled", Map.of("target", target.getName())));
    }

    /**
     * Cleans up all glowing targets for a moderator (e.g. on exit from Staff Mode or disconnect).
     */
    public void cleanup(Player moderator) {
        if (moderator == null) return;
        UUID modId = moderator.getUniqueId();
        Set<UUID> targets = activeGlows.remove(modId);
        if (targets != null) {
            Scoreboard board = moderator.getScoreboard();
            Team team = board.getTeam(TEAM_NAME);
            for (UUID targetId : targets) {
                Player target = Bukkit.getPlayer(targetId);
                if (team != null && target != null) {
                    team.removeEntry(target.getName());
                }
                if (target != null && moderator.isOnline()) {
                    sendEntityMetadataGlow(moderator, target, false);
                }

                Set<UUID> watchers = targetWatchers.get(targetId);
                if (watchers != null) {
                    watchers.remove(modId);
                    if (watchers.isEmpty()) {
                        targetWatchers.remove(targetId);
                    }
                }
            }
        }

        if (activeGlows.isEmpty()) {
            stopRefreshTask();
        }
    }

    /**
     * Handles target player quitting by removing them from all active moderator tracking.
     */
    public void onTargetQuit(Player target) {
        if (target == null) return;
        UUID targetId = target.getUniqueId();
        targetWatchers.remove(targetId);

        for (Map.Entry<UUID, Set<UUID>> entry : activeGlows.entrySet()) {
            if (entry.getValue().remove(targetId)) {
                Player mod = Bukkit.getPlayer(entry.getKey());
                if (mod != null && mod.isOnline()) {
                    Team team = mod.getScoreboard().getTeam(TEAM_NAME);
                    if (team != null) {
                        team.removeEntry(target.getName());
                    }
                    sendEntityMetadataGlow(mod, target, false);
                }
            }
        }

        if (activeGlows.isEmpty()) {
            stopRefreshTask();
        }
    }

    /**
     * Cleans up all glows when plugin is disabled.
     */
    public void cleanupAll() {
        stopRefreshTask();
        activeGlows.clear();
        targetWatchers.clear();
    }
}
