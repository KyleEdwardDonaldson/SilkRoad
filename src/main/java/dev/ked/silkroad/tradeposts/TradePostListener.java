package dev.ked.silkroad.tradeposts;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.cargo.CargoItem;
import dev.ked.silkroad.contracts.Contract;
import dev.ked.silkroad.contracts.ContractManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Handles interactions with trade posts.
 */
public class TradePostListener implements Listener {

    private final SilkRoadPlugin plugin;
    private final TradePostManager tradePostManager;
    private final ContractManager contractManager;

    public TradePostListener(SilkRoadPlugin plugin, TradePostManager tradePostManager,
                            ContractManager contractManager) {
        this.plugin = plugin;
        this.tradePostManager = tradePostManager;
        this.contractManager = contractManager;
    }

    /**
     * Handle right-click on trade post.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Location location = block.getLocation();
        if (!tradePostManager.isTradePost(location)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Check if player is holding cargo
        if (CargoItem.isCargoItem(plugin, itemInHand)) {
            // Attempt delivery
            handleCargoDelivery(player, itemInHand);
            event.setCancelled(true);
            return;
        }

        // TODO: Open trade post GUI
        // For now, just send a message
        player.sendMessage("§6§lTrade Post");
        player.sendMessage("§7Right-click with cargo to deliver");
        player.sendMessage("§7GUI coming soon!");

        event.setCancelled(true);
    }

    /**
     * Handle cargo delivery at trade post.
     */
    private void handleCargoDelivery(Player player, ItemStack cargoItem) {
        UUID contractId = CargoItem.getContractId(plugin, cargoItem);
        if (contractId == null) {
            player.sendMessage("§cInvalid cargo item!");
            return;
        }

        Contract contract = plugin.getContractRegistry().getContract(contractId);
        if (contract == null) {
            player.sendMessage("§cContract not found!");
            CargoItem.removeCargo(player, contractId);
            return;
        }

        // Validate transporter
        if (!contract.getTransporter().equals(player.getUniqueId())) {
            player.sendMessage("§cThis is not your contract!");
            return;
        }

        // Validate contract can be delivered
        if (!contract.canBeDelivered()) {
            player.sendMessage("§cThis contract cannot be delivered!");
            if (contract.isExpired()) {
                player.sendMessage("§cThe contract has expired!");
            }
            return;
        }

        // Validate at correct delivery location
        Location playerLocation = player.getLocation();
        String playerRegion = getRegionName(playerLocation);

        if (!contract.getDestinationRegion().equals(playerRegion)) {
            player.sendMessage("§cYou must deliver to " + contract.getDestinationRegion() + "!");
            player.sendMessage("§7You are currently in: " + playerRegion);
            return;
        }

        // Remove cargo item
        player.getInventory().remove(cargoItem);

        // Complete delivery
        boolean success = contractManager.deliverContract(player, contractId);

        if (success) {
            // Success message already sent by ContractManager
            // TODO: Play success sound/particles
        } else {
            player.sendMessage("§cFailed to deliver contract!");
        }
    }

    /**
     * Protect trade posts from being broken.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("tradePosts.protectFromBreaking", true)) {
            return;
        }

        Location location = event.getBlock().getLocation();
        if (!tradePostManager.isTradePost(location)) {
            return;
        }

        Player player = event.getPlayer();

        // Check permission
        if (player.hasPermission("silkroad.admin.tradepost")) {
            tradePostManager.removeTradePost(location);
            player.sendMessage("§eTradepost removed");
            return;
        }

        // Prevent breaking
        event.setCancelled(true);
        player.sendMessage("§cYou cannot break trade posts! Use §e/tradepost remove §cinstead.");
    }

    /**
     * Get region name at a location.
     */
    private String getRegionName(Location location) {
        if (plugin.getTownyIntegration() != null && plugin.getTownyIntegration().isEnabled()) {
            String region = plugin.getTownyIntegration().getRegionName(location);
            return region != null ? region : "Wilderness";
        }
        return "Unknown";
    }
}
