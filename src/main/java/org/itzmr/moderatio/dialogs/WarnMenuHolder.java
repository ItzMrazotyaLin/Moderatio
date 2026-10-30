package org.itzmr.moderatio.dialogs;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom inventory holder for categorized warn selection GUI.
 */
public class WarnMenuHolder implements InventoryHolder {

    private final Player target;
    private Inventory inventory;
    private final Map<Integer, WarnAction> slotActions = new HashMap<>();

    public record WarnAction(String category, String reason) {}

    public WarnMenuHolder(Player target) {
        this.target = target;
    }

    public Player getTarget() {
        return target;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void setAction(int slot, String category, String reason) {
        slotActions.put(slot, new WarnAction(category, reason));
    }

    public WarnAction getAction(int slot) {
        return slotActions.get(slot);
    }
}
