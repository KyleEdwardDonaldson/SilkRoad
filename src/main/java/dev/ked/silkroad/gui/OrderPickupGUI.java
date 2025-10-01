package dev.ked.silkroad.gui;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.contracts.Contract;
import dev.ked.silkroad.tradeposts.TradePost;
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
 * Order Pickup GUI - Claim delivered items at a trade post
 */
public class OrderPickupGUI {

    private final SilkRoadPlugin plugin;
    private final TradePost tradePost;
    private final Player player;
    private final Inventory inventory;
    private List<Contract> pendingOrders;

    public OrderPickupGUI(SilkRoadPlugin plugin, TradePost tradePost, Player player) {
        this.plugin = plugin;
        this.tradePost = tradePost;
        this.player = player;

        // Create 27-slot inventory (3 rows)
        this.inventory = Bukkit.createInventory(null, 27,
                Component.text("Pickup Orders").color(NamedTextColor.GREEN));

        loadPendingOrders();
        buildGUI();
    }

    private void loadPendingOrders() {
        // Get all delivered contracts for this player at this trade post
        pendingOrders = plugin.getContractManager()
                .getPendingOrdersForPlayer(player.getUniqueId(), tradePost);

        // Sort by delivery time (oldest first)
        pendingOrders.sort((c1, c2) ->
                Long.compare(c1.getDeliveredAt(), c2.getDeliveredAt()));
    }

    private void buildGUI() {
        inventory.clear();

        // Display pending orders (up to 18 slots)
        int slot = 0;
        for (int i = 0; i < Math.min(pendingOrders.size(), 18); i++) {
            Contract contract = pendingOrders.get(i);
            inventory.setItem(slot++, createOrderItem(contract, i));
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
        inventory.setItem(26, createBackButton());

        // If no orders, show message
        if (pendingOrders.isEmpty()) {
            ItemStack placeholder = createItem(Material.ENDER_CHEST,
                "§a§lNo Orders Waiting",
                List.of(
                    "§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                    "§7You have no items waiting",
                    "§7for pickup at this trade post.",
                    "",
                    "§7Orders will appear here when",
                    "§7transporters deliver items to",
                    "§7this location.",
                    "",
                    "§7Try purchasing from a Silk Road",
                    "§7shop in another region!",
                    "§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                ));
            inventory.setItem(13, placeholder);
        }
    }

    private ItemStack createOrderItem(Contract contract, int index) {
        List<String> lore = new ArrayList<>();

        // Item info
        String itemName = formatItemName(contract.getItem());
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§e📦 Item: §f" + itemName);
        lore.add("§e🔢 Quantity: §f" + contract.getQuantity());
        lore.add("");

        // Origin
        lore.add("§e📍 From: §f" + contract.getOriginRegion());
        lore.add("§e💼 Shop Owner: §f" + getPlayerName(contract.getShopOwner()));
        lore.add("");

        // Delivery info
        lore.add("§e🚚 Delivered By: §f" + getPlayerName(contract.getTransporter()));

        // Time since delivery
        long timeSince = System.currentTimeMillis() - contract.getDeliveredAt();
        String timeStr = formatTimeSince(timeSince);
        lore.add("§e⌛ Delivered: §f" + timeStr + " ago");
        lore.add("");

        // Pricing
        double totalCost = contract.getShopPrice() * contract.getQuantity();
        lore.add("§e💰 Total Cost: §a$" + String.format("%.2f", totalCost));
        lore.add("§7(Already paid)");
        lore.add("");

        lore.add("§a§l✓ Click to claim items");
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Use the actual item type from the contract
        Material displayMaterial = contract.getItem().getType();
        return createItem(displayMaterial, "§a§lOrder #" + (index + 1), lore);
    }

    private ItemStack createInfoButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§eTrade Post: §f" + tradePost.getRegionName());
        lore.add("§ePending Orders: §f" + pendingOrders.size());

        if (!pendingOrders.isEmpty()) {
            double totalValue = pendingOrders.stream()
                    .mapToDouble(c -> c.getShopPrice() * c.getQuantity())
                    .sum();
            int totalItems = pendingOrders.stream()
                    .mapToInt(Contract::getQuantity)
                    .sum();
            lore.add("§eTotal Items: §f" + totalItems);
            lore.add("§eTotal Value: §a$" + String.format("%.2f", totalValue));
        }

        lore.add("");
        lore.add("§7Click an item to claim it!");
        lore.add("§7Items will be added to your");
        lore.add("§7inventory directly.");
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.BOOK, "§6§lPickup Info", lore);
    }

    private ItemStack createBackButton() {
        return createItem(Material.BARRIER, "§c§lBack",
                List.of("§7Return to main menu"));
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

    private String formatTimeSince(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) return seconds + "s";
        else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + "m";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + "h";
        } else {
            long days = seconds / 86400;
            return days + "d";
        }
    }

    private String getPlayerName(java.util.UUID playerId) {
        if (playerId == null) return "Unknown";
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        return offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void handleClick(int slot) {
        if (slot >= 0 && slot < 18 && slot < pendingOrders.size()) {
            // Order click - claim the order
            Contract contract = pendingOrders.get(slot);

            // Check if player has inventory space
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(Component.text("Your inventory is full!")
                        .color(NamedTextColor.RED));
                return;
            }

            // Give items to player
            ItemStack items = contract.getItem().clone();
            items.setAmount(contract.getQuantity());
            player.getInventory().addItem(items);

            // Complete the contract
            plugin.getContractManager().completeContract(contract.getContractId());

            // Notify player
            player.sendMessage(Component.text("✓ Order claimed! Items added to inventory.")
                    .color(NamedTextColor.GREEN));

            // Refresh GUI
            loadPendingOrders();
            buildGUI();

        } else if (slot == 26) {
            // Back
            new TradePostMainGUI(plugin, tradePost, player).open();
        }
    }

    public Inventory getInventory() {
        return inventory;
    }
}
