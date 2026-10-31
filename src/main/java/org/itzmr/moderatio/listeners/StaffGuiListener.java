package org.itzmr.moderatio.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.itzmr.moderatio.Moderatio;
import org.itzmr.moderatio.database.Punishment;
import org.itzmr.moderatio.dialogs.PunishmentDetailHolder;
import org.itzmr.moderatio.dialogs.PunishmentHistoryHolder;
import org.itzmr.moderatio.dialogs.StaffAuditListHolder;
import org.itzmr.moderatio.utils.MessageUtils;

/**
 * Listener for handling interactions inside paginated punishment histories and staff audit GUI menus.
 */
public class StaffGuiListener implements Listener {

    private final Moderatio plugin;

    public StaffGuiListener(Moderatio plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // 1. Staff Audit List GUI
        if (event.getInventory().getHolder() instanceof StaffAuditListHolder holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
                return;
            }

            if (!plugin.getAuditService().isDeveloper(player)) {
                MessageUtils.send(player, "<red>Доступно исключительно разработчикам!</red>");
                player.closeInventory();
                return;
            }

            int slot = event.getSlot();
            int page = holder.getPage();
            int totalPages = holder.getTotalPages();

            if (slot == 45 && page > 0) {
                plugin.getDialogService().openStaffAuditListGui(player, page - 1);
            } else if (slot == 53 && page < totalPages - 1) {
                plugin.getDialogService().openStaffAuditListGui(player, page + 1);
            } else if (slot == 48) {
                player.closeInventory();
                plugin.getDialogService().openStaffMenuDialog(player);
            } else {
                String staffName = holder.getStaff(slot);
                if (staffName != null) {
                    plugin.getDialogService().openStaffMemberHistoryGui(player, staffName, 0);
                }
            }
            return;
        }

        // 2. Punishment History GUI
        if (event.getInventory().getHolder() instanceof PunishmentHistoryHolder holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
                return;
            }

            int slot = event.getSlot();
            int page = holder.getPage();
            int totalPages = holder.getTotalPages();

            if (slot == 45 && page > 0) {
                plugin.getDialogService().openPunishmentHistoryGui(holder.getMode(), player, holder.getFilterUuid(), holder.getFilterName(), page - 1);
            } else if (slot == 53 && page < totalPages - 1) {
                plugin.getDialogService().openPunishmentHistoryGui(holder.getMode(), player, holder.getFilterUuid(), holder.getFilterName(), page + 1);
            } else if (slot == 48) {
                // Back button
                if (holder.getMode() == PunishmentHistoryHolder.HistoryMode.MY_PUNISHMENTS) {
                    player.closeInventory();
                    plugin.getDialogService().openStaffMenuDialog(player);
                } else if (holder.getMode() == PunishmentHistoryHolder.HistoryMode.STAFF_AUDIT) {
                    plugin.getDialogService().openStaffAuditListGui(player, 0);
                } else if (holder.getMode() == PunishmentHistoryHolder.HistoryMode.TARGET_HISTORY) {
                    Player target = Bukkit.getPlayer(holder.getFilterUuid());
                    player.closeInventory();
                    if (target != null && target.isOnline()) {
                        plugin.getDialogService().openMainDialog(player, target);
                    } else {
                        plugin.getDialogService().openStaffMenuDialog(player);
                    }
                }
            } else {
                Punishment punishment = holder.getPunishment(slot);
                if (punishment != null) {
                    plugin.getDialogService().openPunishmentDetailGui(player, punishment, holder);
                }
            }
            return;
        }

        // 3. Punishment Detail Card GUI
        if (event.getInventory().getHolder() instanceof PunishmentDetailHolder holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
                return;
            }

            int slot = event.getSlot();
            Punishment p = holder.getPunishment();

            if (slot == 22) {
                // Back to history
                PunishmentHistoryHolder returnHolder = holder.getReturnHolder();
                if (returnHolder != null) {
                    plugin.getDialogService().openPunishmentHistoryGui(
                            returnHolder.getMode(),
                            player,
                            returnHolder.getFilterUuid(),
                            returnHolder.getFilterName(),
                            returnHolder.getPage()
                    );
                } else {
                    player.closeInventory();
                    plugin.getDialogService().openStaffMenuDialog(player);
                }
            } else if (slot == 11) {
                // Cancel punishment
                if (p == null || !p.isActive() || p.isExpired()) {
                    MessageUtils.send(player, "<yellow>Наказание уже неактивно или истекло.</yellow>");
                    return;
                }

                boolean isDev = plugin.getAuditService().isDeveloper(player);
                boolean isOwner = p.getStaffUuid() != null && p.getStaffUuid().equals(player.getUniqueId());

                if (!isDev && !isOwner) {
                    MessageUtils.send(player, "<red>Вы можете отменять только свои собственные наказания!</red>");
                    return;
                }

                plugin.getPunishmentManager().cancelPunishment(p, player.getUniqueId(), player.getName()).thenRun(() -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        MessageUtils.send(player, "<green>Наказание <white>#" + p.getId() + "</white> (<yellow>" + p.getType() + "</yellow>) для <gold>" + p.getTargetName() + "</gold> успешно отменено!</green>");
                        plugin.getDialogService().openPunishmentDetailGui(player, p, holder.getReturnHolder());
                    });
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof StaffAuditListHolder ||
                event.getInventory().getHolder() instanceof PunishmentHistoryHolder ||
                event.getInventory().getHolder() instanceof PunishmentDetailHolder) {
            event.setCancelled(true);
        }
    }
}
