package org.itzmr.moderatio.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.staff.StaffManager;
import org.itzmr.moderatio.utils.MessageUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces staff mode restrictions: blocks damage, building, breaking, interactions, item pickup/drop,
 * and handles movement restrictions for frozen players.
 */
public class StaffProtectionListener implements Listener {

    private final Moderatio plugin;
    private final StaffManager staffManager;
    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<>();

    public StaffProtectionListener(Moderatio plugin) {
        this.plugin = plugin;
        this.staffManager = plugin.getStaffManager();
    }

    private void sendCooldownMessage(Player player, String messageKey) {
        long now = System.currentTimeMillis();
        Long last = messageCooldowns.get(player.getUniqueId());
        if (last == null || now - last > 1500) {
            messageCooldowns.put(player.getUniqueId(), now);
            String msg = plugin.getConfigManager().getMessage(messageKey);
            MessageUtils.send(player, msg);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && staffManager.isInStaffMode(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Player staffDamager = null;

        if (damager instanceof Player p) {
            staffDamager = p;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            staffDamager = p;
        }

        if (staffDamager != null && staffManager.isInStaffMode(staffDamager)) {
            event.setCancelled(true);
            sendCooldownMessage(staffDamager, "protection.cannot-damage");
            return;
        }

        if (event.getEntity() instanceof Player victim && staffManager.isInStaffMode(victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (staffManager.isInStaffMode(player)) {
            event.setCancelled(true);
            sendCooldownMessage(player, "protection.cannot-break");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (staffManager.isInStaffMode(player)) {
            event.setCancelled(true);
            sendCooldownMessage(player, "protection.cannot-place");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!staffManager.isInStaffMode(player)) {
            return;
        }

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK || action == Action.PHYSICAL) {
            event.setCancelled(true);
            if (action != Action.PHYSICAL) {
                sendCooldownMessage(player, "protection.cannot-interact");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && staffManager.isInStaffMode(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (staffManager.isInStaffMode(player)) {
            event.setCancelled(true);
            sendCooldownMessage(player, "protection.cannot-drop");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && staffManager.isInStaffMode(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!staffManager.isFrozen(player.getUniqueId())) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Check if player changed position (allow changing look direction)
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
        }
    }
}
