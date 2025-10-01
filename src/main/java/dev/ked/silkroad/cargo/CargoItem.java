package dev.ked.silkroad.cargo;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.contracts.Contract;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Create and validate soulbound cargo bundle items.
 * Cargo items are special items that:
 * - Represent a delivery contract
 * - Cannot be dropped, traded, or stored
 * - Persist through death
 * - Display live contract information
 */
public class CargoItem {

    private static final String CARGO_KEY = "silkroad_cargo";
    private static final String CONTRACT_ID_KEY = "silkroad_contract_id";

    /**
     * Create a cargo item for a contract.
     */
    public static ItemStack createCargoItem(SilkRoadPlugin plugin, Contract contract) {
        // Get cargo material from config
        String materialName = plugin.getConfig().getString("cargo.material", "BUNDLE");
        Material cargoMaterial;
        try {
            cargoMaterial = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid cargo material: " + materialName + ", using BUNDLE");
            cargoMaterial = Material.BUNDLE;
        }

        ItemStack cargo = new ItemStack(cargoMaterial);
        ItemMeta meta = cargo.getItemMeta();

        // Set display name
        String itemName = formatItemName(contract.getItem());
        Component displayName = Component.text("📦 Cargo: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(itemName, NamedTextColor.YELLOW));
        meta.displayName(displayName);

        // Set lore
        List<Component> lore = buildCargoLore(plugin, contract);
        meta.lore(lore);

        // Store contract ID in PDC
        NamespacedKey contractKey = new NamespacedKey(plugin, CONTRACT_ID_KEY);
        meta.getPersistentDataContainer().set(contractKey, PersistentDataType.STRING,
                contract.getContractId().toString());

        // Mark as cargo item
        NamespacedKey cargoKey = new NamespacedKey(plugin, CARGO_KEY);
        meta.getPersistentDataContainer().set(cargoKey, PersistentDataType.BYTE, (byte) 1);

        cargo.setItemMeta(meta);
        return cargo;
    }

    /**
     * Build the lore for a cargo item.
     */
    private static List<Component> buildCargoLore(SilkRoadPlugin plugin, Contract contract) {
        List<Component> lore = new ArrayList<>();

        // Item info
        lore.add(Component.text("Quantity: ", NamedTextColor.GRAY)
                .append(Component.text(contract.getQuantity(), NamedTextColor.WHITE)));

        lore.add(Component.text("From: ", NamedTextColor.GRAY)
                .append(Component.text(contract.getOriginRegion(), NamedTextColor.YELLOW)));

        lore.add(Component.text("To: ", NamedTextColor.GRAY)
                .append(Component.text(contract.getDestinationRegion(), NamedTextColor.YELLOW)));

        lore.add(Component.empty());

        // Bounty info
        double currentBounty = contract.getCurrentBounty();
        lore.add(Component.text("Current Bounty: ", NamedTextColor.GRAY)
                .append(Component.text("$" + String.format("%.2f", currentBounty), NamedTextColor.GREEN)));

        lore.add(Component.text("Decay: ", NamedTextColor.RED)
                .append(Component.text("-$" + String.format("%.2f", contract.getDecayRate()) + "/sec",
                        NamedTextColor.WHITE)));

        // Time remaining
        long timeRemaining = contract.getTimeRemaining();
        String timeStr = formatTimeRemaining(timeRemaining);
        lore.add(Component.text("Time Left: ", NamedTextColor.GRAY)
                .append(Component.text(timeStr, timeRemaining < 600000 ? NamedTextColor.RED : NamedTextColor.YELLOW)));

        lore.add(Component.empty());

        // Status
        String status = contract.isPickedUp() ? "✓ Picked Up" : "⚠ Awaiting Pickup";
        NamedTextColor statusColor = contract.isPickedUp() ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        lore.add(Component.text("Status: ", NamedTextColor.GRAY)
                .append(Component.text(status, statusColor)));

        lore.add(Component.empty());

        // Instructions
        if (!contract.isPickedUp()) {
            lore.add(Component.text("Right-click shop to pickup", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("Right-click Trade Post to deliver", NamedTextColor.YELLOW));
        }

        lore.add(Component.empty());

        // Warning
        lore.add(Component.text("Soulbound - Cannot drop or trade", NamedTextColor.RED, TextDecoration.ITALIC));

        return lore;
    }

    /**
     * Check if an item is a cargo item.
     */
    public static boolean isCargoItem(SilkRoadPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        NamespacedKey cargoKey = new NamespacedKey(plugin, CARGO_KEY);
        return meta.getPersistentDataContainer().has(cargoKey, PersistentDataType.BYTE);
    }

    /**
     * Get the contract ID from a cargo item.
     */
    public static UUID getContractId(SilkRoadPlugin plugin, ItemStack item) {
        if (!isCargoItem(plugin, item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        NamespacedKey contractKey = new NamespacedKey(plugin, CONTRACT_ID_KEY);
        String contractIdStr = meta.getPersistentDataContainer().get(contractKey, PersistentDataType.STRING);

        if (contractIdStr == null) {
            return null;
        }

        try {
            return UUID.fromString(contractIdStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Update the lore of a cargo item with current contract data.
     */
    public static void updateCargoLore(SilkRoadPlugin plugin, ItemStack item, Contract contract) {
        if (!isCargoItem(plugin, item)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        List<Component> lore = buildCargoLore(plugin, contract);
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Remove a cargo item from a player's inventory.
     */
    public static boolean removeCargo(Player player, UUID contractId) {
        SilkRoadPlugin plugin = SilkRoadPlugin.getInstance();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isCargoItem(plugin, item)) {
                UUID itemContractId = getContractId(plugin, item);
                if (contractId.equals(itemContractId)) {
                    player.getInventory().remove(item);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Find a cargo item in a player's inventory by contract ID.
     */
    public static ItemStack findCargo(Player player, UUID contractId) {
        SilkRoadPlugin plugin = SilkRoadPlugin.getInstance();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isCargoItem(plugin, item)) {
                UUID itemContractId = getContractId(plugin, item);
                if (contractId.equals(itemContractId)) {
                    return item;
                }
            }
        }

        return null;
    }

    /**
     * Format an ItemStack's display name.
     */
    private static String formatItemName(ItemStack item) {
        if (item == null) {
            return "Unknown";
        }

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        // Convert material name to readable format
        String name = item.getType().name().toLowerCase().replace('_', ' ');
        return capitalizeWords(name);
    }

    /**
     * Capitalize first letter of each word.
     */
    private static String capitalizeWords(String str) {
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

    /**
     * Format time remaining as human-readable string.
     */
    private static String formatTimeRemaining(long milliseconds) {
        long seconds = milliseconds / 1000;

        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return minutes + "m " + secs + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }
}
