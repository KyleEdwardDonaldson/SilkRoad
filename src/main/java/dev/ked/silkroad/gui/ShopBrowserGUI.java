package dev.ked.silkroad.gui;

import dev.ked.silkroad.SilkRoadPlugin;
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
 * Shop Browser GUI - Browse Silk Road-enabled shops from other regions
 */
public class ShopBrowserGUI {

    private final SilkRoadPlugin plugin;
    private final TradePost tradePost;
    private final Player player;
    private final Inventory inventory;
    private int page = 0;

    public ShopBrowserGUI(SilkRoadPlugin plugin, TradePost tradePost, Player player) {
        this.plugin = plugin;
        this.tradePost = tradePost;
        this.player = player;

        // Create 54-slot inventory (6 rows)
        this.inventory = Bukkit.createInventory(null, 54,
                Component.text("Browse Silk Road Shops").color(NamedTextColor.BLUE));

        buildGUI();
    }

    private void buildGUI() {
        inventory.clear();

        // TODO: Get actual shops from BetterShop API
        // For now, show placeholder message

        // Fill with decorative glass
        ItemStack grayPane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, grayPane);
        }

        // Placeholder message
        ItemStack placeholder = createItem(Material.BARRIER,
            "§c§lNo Shops Available",
            List.of(
                "§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                "§7BetterShop integration is not",
                "§7yet fully implemented.",
                "",
                "§7This feature will allow you to:",
                "§e• Browse shops from all regions",
                "§e• Purchase items remotely",
                "§e• Create delivery contracts",
                "",
                "§7Coming soon!",
                "§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            ));
        inventory.setItem(22, placeholder);

        // Navigation buttons
        inventory.setItem(48, createPreviousButton());
        inventory.setItem(49, createBackButton());
        inventory.setItem(50, createNextButton());
    }

    private ItemStack createPreviousButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7Click to view previous page");
        if (page == 0) {
            lore.add("§c§lFirst page");
        }

        return createItem(Material.ARROW, "§e§l← Previous Page", lore);
    }

    private ItemStack createNextButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7Click to view next page");
        // TODO: Check if there's a next page
        lore.add("§c§lLast page");

        return createItem(Material.ARROW, "§e§lNext Page →", lore);
    }

    private ItemStack createBackButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7Return to main menu");

        return createItem(Material.BARRIER, "§c§lBack", lore);
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

    public void open() {
        player.openInventory(inventory);
    }

    public void handleClick(int slot) {
        if (slot == 48) {
            // Previous page
            if (page > 0) {
                page--;
                buildGUI();
            }
        } else if (slot == 49) {
            // Back to main menu
            new TradePostMainGUI(plugin, tradePost, player).open();
        } else if (slot == 50) {
            // Next page
            // TODO: Check if there's a next page before incrementing
            // page++;
            // buildGUI();
        }
        // TODO: Handle shop clicks (slots 0-44)
    }

    public Inventory getInventory() {
        return inventory;
    }
}
