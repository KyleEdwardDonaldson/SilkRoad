package dev.ked.silkroad.gui;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.contracts.Contract;
import dev.ked.silkroad.contracts.ContractFilter;
import dev.ked.silkroad.contracts.ContractSortOption;
import dev.ked.silkroad.tradeposts.TradePost;
import dev.ked.silkroad.transport.TransporterManager;
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
import java.util.stream.Collectors;

/**
 * Contract Browser GUI - View and accept available delivery contracts
 */
public class ContractBrowserGUI {

    private final SilkRoadPlugin plugin;
    private final TradePost tradePost;
    private final Player player;
    private final Inventory inventory;
    private int page = 0;
    private List<Contract> availableContracts;
    private ContractFilter currentFilter = ContractFilter.ALL;
    private ContractSortOption currentSort = ContractSortOption.BOUNTY_HIGH;

    public ContractBrowserGUI(SilkRoadPlugin plugin, TradePost tradePost, Player player) {
        this.plugin = plugin;
        this.tradePost = tradePost;
        this.player = player;

        // Create 54-slot inventory (6 rows)
        this.inventory = Bukkit.createInventory(null, 54,
                Component.text("Available Delivery Contracts").color(NamedTextColor.YELLOW));

        loadContracts();
        buildGUI();
    }

    private void loadContracts() {
        // Get all available contracts
        availableContracts = plugin.getContractRegistry().getAvailableContracts();

        // Filter out contracts the player can't accept (level requirement)
        TransporterManager transporterManager = plugin.getTransporterManager();
        TransporterManager.TransporterData transporterData = transporterManager.getStats(player.getUniqueId());

        if (transporterData != null) {
            final int playerLevel = transporterData.getLevel();
            final int maxContracts = transporterManager.getMaxContracts(player.getUniqueId());
            final int activeContracts = plugin.getContractRegistry()
                    .getActiveContractsForTransporter(player.getUniqueId()).size();

            availableContracts = availableContracts.stream()
                    .filter(c -> c.getTransporterLevel() <= playerLevel)
                    .filter(c -> activeContracts < maxContracts)
                    .filter(currentFilter.getPredicate(player, transporterManager))
                    .collect(Collectors.toList());
        }

        // Sort by selected sort option
        availableContracts.sort(currentSort.getComparator());
    }

    private void buildGUI() {
        inventory.clear();

        // Calculate pagination
        int contractsPerPage = 45;
        int startIndex = page * contractsPerPage;
        int endIndex = Math.min(startIndex + contractsPerPage, availableContracts.size());

        // Display contracts
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Contract contract = availableContracts.get(i);
            inventory.setItem(slot++, createContractItem(contract, i));
        }

        // Fill empty slots with decorative glass
        ItemStack grayPane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = slot; i < 45; i++) {
            inventory.setItem(i, grayPane);
        }

        // Bottom row decorative
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, grayPane);
        }

        // Navigation and control buttons
        inventory.setItem(45, createFilterButton());
        inventory.setItem(46, createSortButton());
        inventory.setItem(48, createPreviousButton());
        inventory.setItem(49, createInfoButton());
        inventory.setItem(50, createNextButton());
        inventory.setItem(53, createBackButton());

        // If no contracts, show message
        if (availableContracts.isEmpty()) {
            ItemStack placeholder = createItem(Material.BARRIER,
                "§c§lNo Contracts Available",
                List.of(
                    "§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                    "§7There are no delivery contracts",
                    "§7available right now.",
                    "",
                    "§7Contracts are created when players",
                    "§7purchase items from Silk Road shops.",
                    "",
                    "§7Check back later!",
                    "§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                ));
            inventory.setItem(22, placeholder);
        }
    }

    private ItemStack createContractItem(Contract contract, int index) {
        List<String> lore = new ArrayList<>();

        // Item info
        String itemName = formatItemName(contract.getItem());
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§eItem: §f" + itemName + " x" + contract.getQuantity());
        lore.add("");

        // Route
        lore.add("§eFrom: §f" + contract.getOriginRegion());
        lore.add("§eTo: §f" + contract.getDestinationRegion());
        lore.add("§eDistance: §f" + String.format("%.0f", contract.getTotalDistance()) + " blocks");
        lore.add("");

        // Payment
        lore.add("§eCurrent Bounty: §a$" + String.format("%.2f", contract.getCurrentBounty()));
        lore.add("§eDecay: §c-$" + String.format("%.2f", contract.getDecayRate()) + "/sec");

        // Time
        long timeRemaining = contract.getTimeRemaining();
        String timeStr = formatTime(timeRemaining);
        NamedTextColor timeColor = timeRemaining < 600000 ? NamedTextColor.RED : NamedTextColor.YELLOW;
        lore.add("§eTime Left: " + (timeColor == NamedTextColor.RED ? "§c" : "§f") + timeStr);
        lore.add("");

        // Requirements
        lore.add("§eRequired Level: §f" + contract.getTransporterLevel());
        TransporterData transporterData = plugin.getTransporterManager()
                .getTransporterData(player.getUniqueId());
        if (transporterData != null && transporterData.getLevel() >= contract.getTransporterLevel()) {
            lore.add("§a✓ You can accept this contract");
        } else {
            lore.add("§c✗ Level too low");
        }
        lore.add("");

        lore.add("§aClick to accept contract");
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        Material displayMaterial = contract.getItem().getType();
        return createItem(displayMaterial, "§6§lContract #" + (index + 1), lore);
    }

    private ItemStack createFilterButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§eCurrent Filter: §a" + currentFilter.getDisplayName());
        lore.add(currentFilter.getDescription());
        lore.add("");
        lore.add("§7Click to cycle filters:");
        for (ContractFilter filter : ContractFilter.values()) {
            String prefix = filter == currentFilter ? "§a▸ " : "§7  ";
            lore.add(prefix + filter.getDisplayName());
        }
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.HOPPER, "§6§lFilter Contracts", lore);
    }

    private ItemStack createSortButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§eCurrent Sort: §a" + currentSort.getDisplayName());
        lore.add(currentSort.getDescription());
        lore.add("");
        lore.add("§7Click to cycle sorting:");
        for (ContractSortOption sort : ContractSortOption.values()) {
            String prefix = sort == currentSort ? "§a▸ " : "§7  ";
            lore.add(prefix + sort.getDisplayName());
        }
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.COMPARATOR, "§6§lSort Contracts", lore);
    }

    private ItemStack createInfoButton() {
        TransporterManager.TransporterData data = plugin.getTransporterManager()
                .getStats(player.getUniqueId());

        List<String> lore = new ArrayList<>();
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        if (data != null) {
            String levelName = plugin.getTransporterManager().getLevelName(player.getUniqueId());
            lore.add("§eYour Level: §f" + levelName + " (Lvl " + data.getLevel() + ")");
            int activeContracts = plugin.getContractRegistry()
                    .getActiveContractsForTransporter(player.getUniqueId()).size();
            int maxContracts = plugin.getTransporterManager().getMaxContracts(player.getUniqueId());
            lore.add("§eActive Contracts: §f" + activeContracts + "/" + maxContracts);
        }
        lore.add("");
        lore.add("§eFiltered Contracts: §f" + availableContracts.size());
        lore.add("§ePage: §f" + (page + 1) + "/" + (getMaxPages()));
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.BOOK, "§6§lContract Info", lore);
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
        if (page >= getMaxPages() - 1) {
            lore.add("§c§lLast page");
        }

        return createItem(Material.ARROW, "§e§lNext Page →", lore);
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

    private int getMaxPages() {
        return Math.max(1, (int) Math.ceil(availableContracts.size() / 45.0));
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void handleClick(int slot) {
        if (slot >= 0 && slot < 45) {
            // Contract click
            int contractIndex = (page * 45) + slot;
            if (contractIndex < availableContracts.size()) {
                Contract contract = availableContracts.get(contractIndex);
                // TODO: Accept contract confirmation GUI or direct acceptance
                player.closeInventory();
                player.sendMessage(Component.text("Contract acceptance feature coming soon!")
                        .color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Contract ID: " + contract.getContractId())
                        .color(NamedTextColor.GRAY));
            }
        } else if (slot == 45) {
            // Cycle filter
            currentFilter = currentFilter.next();
            page = 0; // Reset to first page
            loadContracts();
            buildGUI();
        } else if (slot == 46) {
            // Cycle sort
            currentSort = currentSort.next();
            page = 0; // Reset to first page
            loadContracts();
            buildGUI();
        } else if (slot == 48) {
            // Previous page
            if (page > 0) {
                page--;
                buildGUI();
            }
        } else if (slot == 50) {
            // Next page
            if (page < getMaxPages() - 1) {
                page++;
                buildGUI();
            }
        } else if (slot == 53) {
            // Back
            new TradePostMainGUI(plugin, tradePost, player).open();
        }
    }

    public Inventory getInventory() {
        return inventory;
    }
}
