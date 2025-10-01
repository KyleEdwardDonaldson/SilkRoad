package dev.ked.silkroad.gui;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.contracts.Contract;
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
 * My Deliveries GUI - Track active delivery contracts with live updates
 */
public class MyDeliveriesGUI {

    private final SilkRoadPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private List<Contract> activeDeliveries;

    public MyDeliveriesGUI(SilkRoadPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        // Create 27-slot inventory (3 rows)
        this.inventory = Bukkit.createInventory(null, 27,
                Component.text("My Active Deliveries").color(NamedTextColor.LIGHT_PURPLE));

        loadDeliveries();
        buildGUI();
    }

    private void loadDeliveries() {
        // Get all active contracts for this transporter
        activeDeliveries = plugin.getContractRegistry()
                .getActiveContractsForTransporter(player.getUniqueId());

        // Sort by bounty (highest first)
        activeDeliveries.sort((c1, c2) ->
                Double.compare(c2.getCurrentBounty(), c1.getCurrentBounty()));
    }

    private void buildGUI() {
        inventory.clear();

        // Display active deliveries (up to 18 slots)
        int slot = 0;
        for (int i = 0; i < Math.min(activeDeliveries.size(), 18); i++) {
            Contract contract = activeDeliveries.get(i);
            inventory.setItem(slot++, createDeliveryItem(contract, i));
        }

        // Fill empty slots with decorative glass
        ItemStack grayPane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = slot; i < 18; i++) {
            inventory.setItem(i, grayPane);
        }

        // Bottom row
        for (int i = 18; i < 27; i++) {
            inventory.setItem(i, grayPane);
        }

        // Info and close buttons
        inventory.setItem(22, createInfoButton());
        inventory.setItem(26, createCloseButton());

        // If no deliveries, show message
        if (activeDeliveries.isEmpty()) {
            ItemStack placeholder = createItem(Material.CHEST_MINECART,
                "§e§lNo Active Deliveries",
                List.of(
                    "§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                    "§7You have no active delivery",
                    "§7contracts at the moment.",
                    "",
                    "§7Visit a Trade Post and browse",
                    "§7the Delivery Jobs to find work!",
                    "",
                    "§aGet started earning bounties!",
                    "§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                ));
            inventory.setItem(13, placeholder);
        }
    }

    private ItemStack createDeliveryItem(Contract contract, int index) {
        List<String> lore = new ArrayList<>();

        // Item info
        String itemName = formatItemName(contract.getItem());
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§e📦 Cargo: §f" + itemName + " x" + contract.getQuantity());
        lore.add("");

        // Route
        lore.add("§e📍 From: §f" + contract.getOriginRegion());
        lore.add("§e📍 To: §f" + contract.getDestinationRegion());
        lore.add("");

        // Current bounty
        double currentBounty = contract.getCurrentBounty();
        NamedTextColor bountyColor = currentBounty > 50 ? NamedTextColor.GREEN :
                                      currentBounty > 10 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        lore.add("§e💰 Current Bounty: " +
                (bountyColor == NamedTextColor.GREEN ? "§a" :
                 bountyColor == NamedTextColor.YELLOW ? "§e" : "§c") +
                "$" + String.format("%.2f", currentBounty));
        lore.add("§e⏱ Decay: §c-$" + String.format("%.2f", contract.getDecayRate()) + "/sec");

        // Time remaining
        long timeRemaining = contract.getTimeRemaining();
        String timeStr = formatTime(timeRemaining);
        NamedTextColor timeColor = timeRemaining < 300000 ? NamedTextColor.RED :
                                    timeRemaining < 600000 ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
        lore.add("§e⌛ Time Left: " +
                (timeColor == NamedTextColor.RED ? "§c" :
                 timeColor == NamedTextColor.YELLOW ? "§e" : "§a") + timeStr);
        lore.add("");

        // Status
        String status;
        if (!contract.isPickedUp()) {
            status = "§e⚠ Awaiting Pickup";
            lore.add(status);
            lore.add("§7Right-click the shop to collect cargo");
        } else {
            status = "§a✓ In Transit";
            lore.add(status);
            lore.add("§7Deliver to a Trade Post in " + contract.getDestinationRegion());
        }
        lore.add("");

        // Navigation hint
        if (contract.isPickedUp()) {
            // TODO: Calculate direction and distance to destination
            lore.add("§e🧭 Navigation: §7Coming soon");
        }

        lore.add("");
        lore.add("§7Click for detailed route info");
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Use cargo material or item type
        Material displayMaterial = contract.isPickedUp() ? Material.BUNDLE : Material.MAP;
        return createItem(displayMaterial, "§d§lDelivery #" + (index + 1), lore);
    }

    private ItemStack createInfoButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§eActive Deliveries: §f" + activeDeliveries.size());

        if (!activeDeliveries.isEmpty()) {
            double totalBounty = activeDeliveries.stream()
                    .mapToDouble(Contract::getCurrentBounty)
                    .sum();
            lore.add("§eTotal Value: §a$" + String.format("%.2f", totalBounty));

            long pickupCount = activeDeliveries.stream()
                    .filter(Contract::isPickedUp)
                    .count();
            lore.add("§eIn Transit: §f" + pickupCount);
            lore.add("§eAwaiting Pickup: §f" + (activeDeliveries.size() - pickupCount));
        }

        lore.add("");
        lore.add("§7Note: Cargo lore updates");
        lore.add("§7automatically every 30 seconds");
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.BOOK, "§6§lDelivery Info", lore);
    }

    private ItemStack createCloseButton() {
        return createItem(Material.BARRIER, "§c§lClose",
                List.of("§7Click to close this menu"));
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

    private String formatItemName(ItemStack item) {
        if (item == null) return "Unknown";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().displayName().toString();
        }
        String name = item.getType().name().toLowerCase().replace('_', ' ');
        return capitalizeWords(name);
    }

    private String capitalizeWords(String str) {
        String[] words = str.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) return seconds + "s";
        else if (seconds < 3600) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return minutes + "m " + secs + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void handleClick(int slot) {
        if (slot >= 0 && slot < 18 && slot < activeDeliveries.size()) {
            // Delivery click - show detailed info
            Contract contract = activeDeliveries.get(slot);
            player.closeInventory();
            player.sendMessage(Component.text("Detailed route info coming soon!")
                    .color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Contract ID: " + contract.getContractId())
                    .color(NamedTextColor.GRAY));
        } else if (slot == 26) {
            // Close
            player.closeInventory();
        }
    }

    public Inventory getInventory() {
        return inventory;
    }
}
