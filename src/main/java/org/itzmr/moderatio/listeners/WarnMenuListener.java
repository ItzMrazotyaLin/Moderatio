package org.itzmr.moderatio.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.dialogs.WarnMenuHolder;
import org.itzmr.moderatio.utils.MessageUtils;

/**
 * Listener for managing inventory clicks inside the categorized warn menu GUI.
 */
public class WarnMenuListener implements Listener {

    private final Moderatio plugin;

    public WarnMenuListener(Moderatio plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof WarnMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player moderator)) {
            return;
        }

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        int slot = event.getSlot();
        Player target = holder.getTarget();

        // 1. Back button (slot 45)
        if (slot == 45) {
            moderator.closeInventory();
            plugin.getDialogService().openMainDialog(moderator, target);
            return;
        }

        // 2. Close button (slot 53)
        if (slot == 53) {
            moderator.closeInventory();
            return;
        }

        // 3. Custom reason button (slot 49) - Strictly restricted to developers
        if (slot == 49) {
            if (!plugin.getAuditService().isDeveloper(moderator)) {
                MessageUtils.send(moderator, "<red>Ввод кастомной причины доступен исключительно разработчикам!</red>");
                return;
            }
            moderator.closeInventory();
            plugin.getDialogService().openCustomWarnDialog(moderator, target);
            return;
        }

        // 4. Warn template action
        WarnMenuHolder.WarnAction action = holder.getAction(slot);
        if (action != null) {
            moderator.closeInventory();
            plugin.getDialogService().executeWarn(moderator, target, action.category(), action.reason());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof WarnMenuHolder) {
            event.setCancelled(true);
        }
    }
}
