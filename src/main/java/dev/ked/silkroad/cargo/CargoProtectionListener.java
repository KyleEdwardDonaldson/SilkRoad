package dev.ked.silkroad.cargo;

import dev.ked.silkroad.SilkRoadPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Prevents drop, trade, and storage of cargo items.
 * Cargo items are soulbound and must remain with the transporter until delivery.
 */
public class CargoProtectionListener implements Listener {

    private final SilkRoadPlugin plugin;

    public CargoProtectionListener(SilkRoadPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Prevent dropping cargo items.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("cargo.preventDrop", true)) {
            return;
        }

        ItemStack item = event.getItemDrop().getItemStack();
        if (CargoItem.isCargoItem(plugin, item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot drop cargo items!");
        }
    }

    /**
     * Prevent trading/giving cargo items to other players.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!plugin.getConfig().getBoolean("cargo.preventTrade", true)) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null) {
            return;
        }

        // Check if clicking cargo in another player's inventory or trading GUI
        if (CargoItem.isCargoItem(plugin, item)) {
            // Allow moving within own inventory
            if (event.getClickedInventory() != null &&
                event.getClickedInventory().getHolder() instanceof Player) {
                Player holder = (Player) event.getClickedInventory().getHolder();
                if (!holder.equals(event.getWhoClicked())) {
                    // Trying to take cargo from another player
                    event.setCancelled(true);
                    event.getWhoClicked().sendMessage("§cYou cannot take cargo from other players!");
                    return;
                }
            }

            // Prevent putting cargo in trading inventories
            if (event.getInventory().getType() == InventoryType.MERCHANT ||
                event.getInventory().getType() == InventoryType.ANVIL ||
                event.getInventory().getType() == InventoryType.GRINDSTONE ||
                event.getInventory().getType() == InventoryType.CARTOGRAPHY) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage("§cYou cannot trade or modify cargo items!");
            }
        }
    }

    /**
     * Prevent storing cargo items in chests or other containers.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (!plugin.getConfig().getBoolean("cargo.preventStorage", true)) {
            return;
        }

        ItemStack item = event.getItem();
        if (CargoItem.isCargoItem(plugin, item)) {
            // Prevent moving cargo into containers (chests, hoppers, etc.)
            if (!(event.getDestination().getHolder() instanceof Player)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevent losing cargo items on death (keep in inventory).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("cargo.persistThroughDeath", true)) {
            return;
        }

        // Find and keep cargo items
        event.getDrops().removeIf(item -> {
            if (CargoItem.isCargoItem(plugin, item)) {
                // Keep the item - don't drop it
                event.getItemsToKeep().add(item);
                return true; // Remove from drops
            }
            return false;
        });
    }
}
