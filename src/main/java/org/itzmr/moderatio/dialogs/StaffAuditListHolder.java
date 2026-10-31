package org.itzmr.moderatio.dialogs;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom inventory holder for the staff audit list GUI (viewing all staff members).
 */
public class StaffAuditListHolder implements InventoryHolder {

    private final Player developer;
    private final List<String> staffMembers;
    private final int page;
    private final int totalPages;
    private Inventory inventory;
    private final Map<Integer, String> slotStaffMap = new HashMap<>();

    public StaffAuditListHolder(Player developer, List<String> staffMembers, int page, int totalPages) {
        this.developer = developer;
        this.staffMembers = staffMembers;
        this.page = page;
        this.totalPages = totalPages;
    }

    public Player getDeveloper() {
        return developer;
    }

    public List<String> getStaffMembers() {
        return staffMembers;
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

    public void mapStaff(int slot, String staffName) {
        slotStaffMap.put(slot, staffName);
    }

    public String getStaff(int slot) {
        return slotStaffMap.get(slot);
    }
}
