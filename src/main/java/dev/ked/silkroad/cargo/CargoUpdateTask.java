package dev.ked.silkroad.cargo;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.contracts.Contract;
import dev.ked.silkroad.contracts.ContractRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Periodically updates cargo item lore with current contract data (bounty, time remaining, etc.)
 * Runs every 30 seconds by default (configurable).
 */
public class CargoUpdateTask extends BukkitRunnable {

    private final SilkRoadPlugin plugin;
    private final ContractRegistry contractRegistry;

    public CargoUpdateTask(SilkRoadPlugin plugin, ContractRegistry contractRegistry) {
        this.plugin = plugin;
        this.contractRegistry = contractRegistry;
    }

    @Override
    public void run() {
        // Iterate through all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerCargo(player);
        }
    }

    /**
     * Update all cargo items in a player's inventory
     */
    private void updatePlayerCargo(Player player) {
        boolean updated = false;

        // Check all inventory slots
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) {
                continue;
            }

            // Check if this is a cargo item
            if (!CargoItem.isCargoItem(plugin, item)) {
                continue;
            }

            // Get associated contract
            UUID contractId = CargoItem.getContractId(plugin, item);
            if (contractId == null) {
                continue;
            }

            Contract contract = contractRegistry.getContract(contractId);
            if (contract == null) {
                // Contract not found - cargo item is invalid
                // TODO: Consider removing invalid cargo items
                continue;
            }

            // Update the cargo item's lore
            CargoItem.updateCargoLore(plugin, item, contract);
            updated = true;
        }

        // Optional: Notify player if cargo was updated (can be disabled in config)
        if (updated && plugin.getConfig().getBoolean("cargo.notifyOnUpdate", false)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§7Cargo info updated"
            ));
        }
    }

    /**
     * Start the cargo update task with configured interval
     */
    public void start() {
        long intervalTicks = plugin.getConfig().getLong("cargo.loreUpdateInterval", 30L) * 20L;

        // Run async to avoid blocking main thread
        this.runTaskTimerAsynchronously(plugin, intervalTicks, intervalTicks);

        plugin.getLogger().info("Cargo lore update task started (interval: " +
                (intervalTicks / 20) + "s)");
    }
}
