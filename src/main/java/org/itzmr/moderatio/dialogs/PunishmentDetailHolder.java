package org.itzmr.moderatio.dialogs;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.itzmr.moderatio.database.Punishment;

/**
 * Custom inventory holder for detailed punishment card view and cancellation.
 */
public class PunishmentDetailHolder implements InventoryHolder {

    private final Player viewer;
    private final Punishment punishment;
    private final PunishmentHistoryHolder returnHolder;
    private Inventory inventory;

    public PunishmentDetailHolder(Player viewer, Punishment punishment, PunishmentHistoryHolder returnHolder) {
        this.viewer = viewer;
        this.punishment = punishment;
        this.returnHolder = returnHolder;
    }

    public Player getViewer() {
        return viewer;
    }

    public Punishment getPunishment() {
        return punishment;
    }

    public PunishmentHistoryHolder getReturnHolder() {
        return returnHolder;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
