package org.itzmr.moderatio.dialogs;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.itzmr.moderatio.database.Punishment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Custom inventory holder for paginated punishment history (own punishments, staff audit, or target history).
 */
public class PunishmentHistoryHolder implements InventoryHolder {

    public enum HistoryMode {
        MY_PUNISHMENTS,
        STAFF_AUDIT,
        TARGET_HISTORY
    }

    private final HistoryMode mode;
    private final Player viewer;
    private final UUID filterUuid;
    private final String filterName;
    private final List<Punishment> punishments;
    private final int page;
    private final int totalPages;
    private Inventory inventory;
    private final Map<Integer, Punishment> slotPunishmentMap = new HashMap<>();

    public PunishmentHistoryHolder(HistoryMode mode, Player viewer, UUID filterUuid, String filterName,
                                   List<Punishment> punishments, int page, int totalPages) {
        this.mode = mode;
        this.viewer = viewer;
        this.filterUuid = filterUuid;
        this.filterName = filterName;
        this.punishments = punishments;
        this.page = page;
        this.totalPages = totalPages;
    }

    public HistoryMode getMode() {
        return mode;
    }

    public Player getViewer() {
        return viewer;
    }

    public UUID getFilterUuid() {
        return filterUuid;
    }

    public String getFilterName() {
        return filterName;
    }

    public List<Punishment> getPunishments() {
        return punishments;
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void mapPunishment(int slot, Punishment punishment) {
        slotPunishmentMap.put(slot, punishment);
    }

    public Punishment getPunishment(int slot) {
        return slotPunishmentMap.get(slot);
    }
}
