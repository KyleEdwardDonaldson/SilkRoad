package dev.ked.silkroad.gui;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.tradeposts.TradePost;
import dev.ked.silkroad.transport.TransporterData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Main menu GUI for Trade Posts
 * Provides navigation to all Trade Post features
 */
public class TradePostMainGUI {

    private final SilkRoadPlugin plugin;
    private final TradePost tradePost;
    private final Player player;
    private final Inventory inventory;

    public TradePostMainGUI(SilkRoadPlugin plugin, TradePost tradePost, Player player) {
        this.plugin = plugin;
        this.tradePost = tradePost;
        this.player = player;

        // Create 27-slot inventory (3 rows)
        this.inventory = Bukkit.createInventory(null, 27,
                Component.text("Silk Road Trade Post").color(NamedTextColor.DARK_PURPLE));

        buildGUI();
    }

    private void buildGUI() {
        // Clear inventory
        inventory.clear();

        // Fill with decorative glass panes
        ItemStack grayPane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, grayPane);
        }

        // Row 1: Main navigation buttons (slots 10-16)
        inventory.setItem(10, createInfoButton());
        inventory.setItem(11, createBrowseShopsButton());
        inventory.setItem(12, createDeliveryJobsButton());
        inventory.setItem(13, createMyDeliveriesButton());
        inventory.setItem(14, createPickupOrdersButton());
        inventory.setItem(15, createStatsButton());

        // Row 2: Additional options
        inventory.setItem(22, createCloseButton());
    }

    private ItemStack createInfoButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§7Welcome to the Silk Road Trade Post!");
        lore.add("");
        lore.add("§eLocation: §f" + tradePost.getRegionName());
        lore.add("§eTown: §f" + tradePost.getTownName());
        lore.add("");
        lore.add("§7This trade post connects you to");
        lore.add("§7shops across all regions. Browse");
        lore.add("§7shops, accept delivery contracts,");
        lore.add("§7and claim your delivered items!");
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.LECTERN, "§6§lTrade Post Info", lore);
    }

    private ItemStack createBrowseShopsButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§7Browse Silk Road-enabled shops");
        lore.add("§7from other regions.");
        lore.add("");
        lore.add("§7Purchase items remotely and they");
        lore.add("§7will be delivered by transporters!");
        lore.add("");
        lore.add("§aClick to browse shops");
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.CHEST, "§b§lBrowse Shops", lore);
    }

    private ItemStack createDeliveryJobsButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§7View available delivery contracts");
        lore.add("§7and accept jobs to earn bounties!");
        lore.add("");

        TransporterData data = plugin.getTransporterManager().getTransporterData(player.getUniqueId());
        if (data != null) {
            lore.add("§eYour Level: §f" + data.getLevelName() + " (Lvl " + data.getLevel() + ")");
            lore.add("§eActive Contracts: §f" + data.getActiveContracts() + "/" + data.getMaxContracts());
            lore.add("§eMax Value: §f$" + String.format("%.0f", data.getMaxContractValue()));
        }
        lore.add("");
        lore.add("§aClick to view delivery jobs");
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.MAP, "§e§lDelivery Jobs", lore);
    }

    private ItemStack createMyDeliveriesButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§7Track your active delivery contracts");
        lore.add("§7with live bounty updates.");
        lore.add("");

        int activeCount = plugin.getContractManager().getActiveContractsForTransporter(player.getUniqueId()).size();
        lore.add("§eActive Deliveries: §f" + activeCount);

        if (activeCount == 0) {
            lore.add("");
            lore.add("§7You have no active deliveries");
        }
        lore.add("");
        lore.add("§aClick to track deliveries");
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.BUNDLE, "§d§lMy Deliveries", lore);
    }

    private ItemStack createPickupOrdersButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§7Claim items that have been");
        lore.add("§7delivered to this trade post.");
        lore.add("");

        int pendingCount = plugin.getContractManager().getPendingOrdersForPlayer(player.getUniqueId(), tradePost).size();
        lore.add("§ePending Orders: §f" + pendingCount);

        if (pendingCount == 0) {
            lore.add("");
            lore.add("§7No items waiting for pickup");
        }
        lore.add("");
        lore.add("§aClick to pickup items");
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.ENDER_CHEST, "§a§lPickup Orders", lore);
    }

    private ItemStack createStatsButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§7View your transporter statistics");
        lore.add("§7and progression.");
        lore.add("");

        TransporterData data = plugin.getTransporterManager().getTransporterData(player.getUniqueId());
        if (data != null) {
            lore.add("§eLevel: §f" + data.getLevelName());
            lore.add("§eXP: §f" + data.getXp() + "/" + data.getXpToNextLevel());
            lore.add("§eCompleted: §f" + data.getContractsCompleted());
            lore.add("§eEarnings: §f$" + String.format("%.2f", data.getTotalEarnings()));
        }
        lore.add("");
        lore.add("§aClick to view detailed stats");
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.PLAYER_HEAD, "§6§lTransporter Stats", lore);
    }

    private ItemStack createCloseButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7Click to close this menu");

        return createItem(Material.BARRIER, "§c§lClose", lore);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(name)
                    .decoration(TextDecoration.ITALIC, false));

            if (lore != null && !lore.isEmpty()) {
                List<Component> componentLore = new ArrayList<>();
                for (String line : lore) {
                    componentLore.add(Component.text(line)
                            .decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(componentLore);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Open the GUI for the player
     */
    public void open() {
        player.openInventory(inventory);
    }

    /**
     * Handle click events
     */
    public void handleClick(int slot) {
        switch (slot) {
            case 10: // Info - do nothing, just display
                break;
            case 11: // Browse Shops
                new ShopBrowserGUI(plugin, tradePost, player).open();
                break;
            case 12: // Delivery Jobs
                new ContractBrowserGUI(plugin, tradePost, player).open();
                break;
            case 13: // My Deliveries
                new MyDeliveriesGUI(plugin, player).open();
                break;
            case 14: // Pickup Orders
                new OrderPickupGUI(plugin, tradePost, player).open();
                break;
            case 15: // Stats
                // TODO: Create StatsGUI
                player.sendMessage(Component.text("Stats GUI coming soon! Use /silkroad stats for now.")
                        .color(NamedTextColor.YELLOW));
                player.closeInventory();
                break;
            case 22: // Close
                player.closeInventory();
                break;
            default:
                // Clicked on decorative item, do nothing
                break;
        }
    }

    /**
     * Get the inventory instance
     */
    public Inventory getInventory() {
        return inventory;
    }
}
