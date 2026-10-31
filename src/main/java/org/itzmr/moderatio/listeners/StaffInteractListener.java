package org.itzmr.moderatio.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.staff.StaffManager;

/**
 * Listens for RMB interactions from moderators on target players to open the Moderation Dialog.
 */
public class StaffInteractListener implements Listener {

    private final Moderatio plugin;
    private final StaffManager staffManager;

    public StaffInteractListener(Moderatio plugin) {
        this.plugin = plugin;
        this.staffManager = plugin.getStaffManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player moderator = event.getPlayer();
        if (!staffManager.isInStaffMode(moderator)) {
            return;
        }

        if (event.getRightClicked() instanceof Player target) {
            event.setCancelled(true);
            plugin.getDialogService().openMainDialog(moderator, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!staffManager.isInStaffMode(player)) {
            return;
        }

        // Only handle main hand (LEFT_CLICK_AIR has null or HAND)
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!player.isSneaking()) {
            return;
        }

        Action action = event.getAction();
        boolean isShiftLmb = (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);

        if (isShiftLmb) {
            event.setCancelled(true);
            plugin.getDialogService().openStaffMenuDialog(player);
        }
    }
}
