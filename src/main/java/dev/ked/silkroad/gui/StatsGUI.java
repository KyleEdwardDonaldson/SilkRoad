package dev.ked.silkroad.gui;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.transport.CompletedContractRecord;
import dev.ked.silkroad.transport.TransporterManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * GUI displaying transporter statistics.
 */
public class StatsGUI {
    private final SilkRoadPlugin plugin;
    private final Player player;
    private final UUID targetPlayerId;
    private final Inventory inventory;

    public StatsGUI(SilkRoadPlugin plugin, Player player, UUID targetPlayerId) {
        this.plugin = plugin;
        this.player = player;
        this.targetPlayerId = targetPlayerId;
        this.inventory = Bukkit.createInventory(null, 54, Component.text("Transporter Statistics"));
    }

    public void open() {
        buildInventory();
        player.openInventory(inventory);
    }

    private void buildInventory() {
        TransporterManager.TransporterData stats = plugin.getTransporterManager().getStats(targetPlayerId);

        // Top row - Level & XP info
        inventory.setItem(4, createLevelItem(stats));

        // Second row - Core stats
        inventory.setItem(10, createContractsItem(stats));
        inventory.setItem(12, createSuccessRateItem(stats));
        inventory.setItem(14, createEarningsItem(stats));
        inventory.setItem(16, createDistanceItem(stats));

        // Third row - Region stats
        inventory.setItem(21, createFavoriteRegionItem(stats));
        inventory.setItem(23, createRegionsVisitedItem(stats));

        // Bottom rows - Recent history
        List<CompletedContractRecord> history = stats.getRecentHistory();
        int slot = 27;
        for (int i = 0; i < Math.min(10, history.size()) && slot < 37; i++) {
            inventory.setItem(slot++, createHistoryItem(history.get(i)));
        }

        // Close button
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.displayName(Component.text("Close").color(NamedTextColor.RED));
        closeButton.setItemMeta(closeMeta);
        inventory.setItem(49, closeButton);
    }

    private ItemStack createLevelItem(TransporterManager.TransporterData stats) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();

        String levelName = plugin.getTransporterManager().getLevelName(targetPlayerId);
        meta.displayName(Component.text("Level " + stats.getLevel() + " - " + levelName)
                .color(NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("XP: " + stats.getXp() + " / " +
                plugin.getTransporterManager().getNextLevelXP(targetPlayerId))
                .color(NamedTextColor.YELLOW));
        lore.add(Component.text("Max Contracts: " +
                plugin.getTransporterManager().getMaxContracts(targetPlayerId))
                .color(NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createContractsItem(TransporterManager.TransporterData stats) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Contracts").color(NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("✓ Completed: " + stats.getCompletedContracts())
                .color(NamedTextColor.GREEN));
        lore.add(Component.text("✗ Failed: " + stats.getFailedContracts())
                .color(NamedTextColor.RED));
        lore.add(Component.text("⊗ Cancelled: " + stats.getCancelledContracts())
                .color(NamedTextColor.GRAY));
        lore.add(Component.text("Total: " + stats.getTotalContracts())
                .color(NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSuccessRateItem(TransporterManager.TransporterData stats) {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Success Rate").color(NamedTextColor.AQUA));

        double successRate = stats.getSuccessRate();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(String.format("%.1f%%", successRate))
                .color(successRate >= 80 ? NamedTextColor.GREEN :
                        successRate >= 50 ? NamedTextColor.YELLOW : NamedTextColor.RED));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEarningsItem(TransporterManager.TransporterData stats) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Total Earnings").color(NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(String.format("%.2f essence", stats.getTotalEarnings()))
                .color(NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDistanceItem(TransporterManager.TransporterData stats) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Total Distance").color(NamedTextColor.BLUE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(String.format("%.0f blocks", stats.getTotalDistance()))
                .color(NamedTextColor.AQUA));

        long hours = stats.getTotalTravelTime() / (1000 * 60 * 60);
        long minutes = (stats.getTotalTravelTime() / (1000 * 60)) % 60;
        lore.add(Component.text(String.format("Travel Time: %dh %dm", hours, minutes))
                .color(NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFavoriteRegionItem(TransporterManager.TransporterData stats) {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Favorite Region").color(NamedTextColor.LIGHT_PURPLE));

        List<Component> lore = new ArrayList<>();
        String favoriteRegion = stats.getFavoriteRegion();
        int deliveries = stats.getRegionDeliveries().getOrDefault(favoriteRegion, 0);
        lore.add(Component.text(favoriteRegion).color(NamedTextColor.YELLOW));
        lore.add(Component.text(deliveries + " deliveries").color(NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRegionsVisitedItem(TransporterManager.TransporterData stats) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Regions Visited").color(NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(stats.getRegionsVisited().size() + " unique regions")
                .color(NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHistoryItem(CompletedContractRecord record) {
        ItemStack item = new ItemStack(record.getItemType());
        ItemMeta meta = item.getItemMeta();

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, HH:mm");
        meta.displayName(Component.text(record.getQuantity() + "x " + formatMaterialName(record.getItemType()))
                .color(NamedTextColor.WHITE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(record.getRoute()).color(NamedTextColor.GRAY));
        lore.add(Component.text(String.format("Earned: %.2f", record.getBountyEarned()))
                .color(NamedTextColor.GOLD));
        lore.add(Component.text(String.format("Distance: %.0f blocks", record.getDistance()))
                .color(NamedTextColor.AQUA));
        lore.add(Component.text(dateFormat.format(new Date(record.getCompletedAt())))
                .color(NamedTextColor.DARK_GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public void handleClick(int slot) {
        if (slot == 49) {
            player.closeInventory();
        }
    }
}
