package dev.ked.silkroad.gui;

import dev.ked.silkroad.SilkRoadPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * Handles clicks in Silk Road GUIs
 */
public class GUIListener implements Listener {

    private final SilkRoadPlugin plugin;

    public GUIListener(SilkRoadPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        String title = event.getView().title().toString();

        // Check if this is one of our GUIs
        // Note: Shop Directory is now handled by BetterShop's GUIListener
        if (title.contains("Silk Road Trade Post")) {
            event.setCancelled(true);
            handleTradePostMainClick(player, inventory, event.getRawSlot());
        } else if (title.contains("Browse Silk Road Shops")) {
            event.setCancelled(true);
            handleShopBrowserClick(player, inventory, event.getRawSlot());
        } else if (title.contains("Available Delivery Contracts")) {
            event.setCancelled(true);
            handleContractBrowserClick(player, inventory, event.getRawSlot());
        } else if (title.contains("My Active Deliveries")) {
            event.setCancelled(true);
            handleMyDeliveriesClick(player, inventory, event.getRawSlot());
        } else if (title.contains("Pickup Orders")) {
            event.setCancelled(true);
            handleOrderPickupClick(player, inventory, event.getRawSlot());
        } else if (title.contains("Transporter Statistics")) {
            event.setCancelled(true);
            handleStatsClick(player, inventory, event.getRawSlot());
        }
    }

    private void handleTradePostMainClick(Player player, Inventory inventory, int slot) {
        // Find the associated TradePostMainGUI
        // Since we don't have a registry, we need to recreate it or pass data differently
        // For now, we'll handle the click directly

        // This is a limitation - ideally we'd store GUI instances or use a different pattern
        // For now, just close on certain slots
        if (slot == 22) { // Close button
            player.closeInventory();
        }
        // TODO: Better GUI management system needed
    }

    private void handleShopBrowserClick(Player player, Inventory inventory, int slot) {
        if (slot == 49) { // Close/Back button
            player.closeInventory();
        }
        // TODO: Implement shop browsing
    }

    private void handleContractBrowserClick(Player player, Inventory inventory, int slot) {
        if (slot == 53) { // Back button
            player.closeInventory();
        }
        // TODO: Implement contract acceptance
    }

    private void handleMyDeliveriesClick(Player player, Inventory inventory, int slot) {
        if (slot == 26) { // Close button
            player.closeInventory();
        }
        // TODO: Implement delivery details
    }

    private void handleOrderPickupClick(Player player, Inventory inventory, int slot) {
        if (slot == 26) { // Back button
            player.closeInventory();
        }
        // TODO: Implement order claiming
    }

    private void handleStatsClick(Player player, Inventory inventory, int slot) {
        if (slot == 49) { // Close button
            player.closeInventory();
        }
    }
}
