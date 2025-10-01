package dev.ked.silkroad.commands;

import dev.ked.bettershop.BetterShopPlugin;
import dev.ked.bettershop.ui.ShopDirectoryGUI;
import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.contracts.Contract;
import dev.ked.silkroad.contracts.ContractManager;
import dev.ked.silkroad.gui.StatsGUI;
import dev.ked.silkroad.transport.TransporterManager;
import dev.ked.silkroad.transport.TransporterManager.TransporterData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Main /silkroad command handler.
 */
public class SilkRoadCommand implements CommandExecutor, TabCompleter {

    private final SilkRoadPlugin plugin;
    private final ContractManager contractManager;
    private final TransporterManager transporterManager;
    private final MiniMessage miniMessage;

    public SilkRoadCommand(SilkRoadPlugin plugin, ContractManager contractManager,
                          TransporterManager transporterManager) {
        this.plugin = plugin;
        this.contractManager = contractManager;
        this.transporterManager = transporterManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Default: show help
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                showHelp(sender);
                return true;

            case "stats":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players!");
                    return true;
                }
                // Check if target player specified
                if (args.length > 1) {
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage("§cPlayer not found!");
                        return true;
                    }
                    new StatsGUI(plugin, (Player) sender, target.getUniqueId()).open();
                } else {
                    new StatsGUI(plugin, (Player) sender, ((Player) sender).getUniqueId()).open();
                }
                return true;

            case "contracts":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players!");
                    return true;
                }
                showContracts((Player) sender);
                return true;

            case "history":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players!");
                    return true;
                }
                showHistory((Player) sender);
                return true;

            case "leaderboard":
            case "lb":
                showLeaderboard(sender);
                return true;

            case "shops":
            case "directory":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players!");
                    return true;
                }
                // Use BetterShop's directory with silk road filter
                BetterShopPlugin betterShop = (BetterShopPlugin) Bukkit.getPluginManager().getPlugin("BetterShop");
                if (betterShop != null) {
                    new ShopDirectoryGUI(betterShop, (Player) sender, true).open();
                } else {
                    sender.sendMessage("§cBetterShop not found!");
                }
                return true;

            case "admin":
                if (!sender.hasPermission("silkroad.admin")) {
                    sender.sendMessage("§cYou don't have permission to use admin commands!");
                    return true;
                }
                return handleAdminCommand(sender, args);

            default:
                sender.sendMessage("§cUnknown subcommand! Use §e/silkroad help");
                return true;
        }
    }

    /**
     * Show help message.
     */
    private void showHelp(CommandSender sender) {
        String message = plugin.getConfig().getString("messages.help.header", "Silk Road Commands");
        sender.sendMessage(miniMessage.deserialize(message));

        sender.sendMessage(miniMessage.deserialize(plugin.getConfig().getString("messages.help.silkroad", "")));
        sender.sendMessage(miniMessage.deserialize(plugin.getConfig().getString("messages.help.stats", "")));
        sender.sendMessage(miniMessage.deserialize(plugin.getConfig().getString("messages.help.contracts", "")));

        if (sender.hasPermission("silkroad.admin")) {
            sender.sendMessage(miniMessage.deserialize(plugin.getConfig().getString("messages.help.adminReload", "")));
            sender.sendMessage(miniMessage.deserialize(plugin.getConfig().getString("messages.help.adminContracts", "")));
            sender.sendMessage(miniMessage.deserialize(plugin.getConfig().getString("messages.help.adminCancel", "")));
        }
    }

    /**
     * Show transporter stats.
     */
    private void showStats(Player player) {
        TransporterData data = transporterManager.getStats(player.getUniqueId());
        int level = data.getLevel();
        String levelName = transporterManager.getLevelName(player.getUniqueId());
        int xp = data.getXp();
        int nextLevelXp = transporterManager.getNextLevelXP(player.getUniqueId());
        int maxContracts = transporterManager.getMaxContracts(player.getUniqueId());
        int activeContracts = plugin.getContractRegistry().getActiveContractsForTransporter(player.getUniqueId()).size();
        double discount = transporterManager.getInsuranceDiscount(player.getUniqueId()) * 100;

        String message = plugin.getConfig().getString("messages.progression.stats", "")
                .replace("{level}", String.valueOf(level))
                .replace("{levelName}", levelName)
                .replace("{xp}", String.valueOf(xp))
                .replace("{nextLevelXp}", String.valueOf(nextLevelXp))
                .replace("{maxContracts}", String.valueOf(maxContracts))
                .replace("{activeContracts}", String.valueOf(activeContracts))
                .replace("{discount}", String.format("%.0f", discount))
                .replace("{completedCount}", String.valueOf(data.getCompletedContracts()))
                .replace("{totalDistance}", String.format("%.0f", data.getTotalDistance()))
                .replace("{totalEarnings}", String.format("%.2f", data.getTotalEarnings()));

        player.sendMessage(miniMessage.deserialize(message));
    }

    /**
     * Show contract history.
     */
    private void showHistory(Player player) {
        TransporterData data = transporterManager.getStats(player.getUniqueId());
        List<dev.ked.silkroad.transport.CompletedContractRecord> history = data.getRecentHistory();

        if (history.isEmpty()) {
            player.sendMessage("§eYou have no completed deliveries yet!");
            return;
        }

        player.sendMessage("§6§l━━━ Recent Delivery History ━━━");
        player.sendMessage("§7Showing last " + Math.min(10, history.size()) + " deliveries:");

        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MMM dd HH:mm");
        for (int i = 0; i < Math.min(10, history.size()); i++) {
            dev.ked.silkroad.transport.CompletedContractRecord record = history.get(i);
            player.sendMessage(String.format("§7%d. §f%dx %s §7| §e%s §7| §a$%.2f §7| §8%s",
                    i + 1,
                    record.getQuantity(),
                    formatMaterialName(record.getItemType().name()),
                    record.getRoute(),
                    record.getBountyEarned(),
                    dateFormat.format(new java.util.Date(record.getCompletedAt()))));
        }

        player.sendMessage("§7Use §e/sr stats §7to view detailed statistics!");
    }

    /**
     * Show leaderboard.
     */
    private void showLeaderboard(CommandSender sender) {
        // Get all transporter data and sort by various metrics
        List<UUID> allPlayers = new ArrayList<>();

        // Note: This requires access to all player data
        // In a production system, you'd cache this or use a database query
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            allPlayers.add(onlinePlayer.getUniqueId());
        }

        if (allPlayers.isEmpty()) {
            sender.sendMessage("§eNo transporter data available!");
            return;
        }

        // Sort by completed contracts
        allPlayers.sort((a, b) -> {
            int aCompleted = transporterManager.getStats(a).getCompletedContracts();
            int bCompleted = transporterManager.getStats(b).getCompletedContracts();
            return Integer.compare(bCompleted, aCompleted);
        });

        sender.sendMessage("§6§l━━━ Top Transporters ━━━");
        sender.sendMessage("§7Ranked by completed deliveries:");

        int rank = 1;
        for (int i = 0; i < Math.min(10, allPlayers.size()); i++) {
            UUID playerId = allPlayers.get(i);
            TransporterData data = transporterManager.getStats(playerId);

            if (data.getCompletedContracts() == 0) continue;

            String playerName = Bukkit.getOfflinePlayer(playerId).getName();
            if (playerName == null) playerName = "Unknown";

            sender.sendMessage(String.format("§7%d. §f%s §7- §e%d contracts §7| §a$%.2f earned §7| §b%.0f blocks",
                    rank++,
                    playerName,
                    data.getCompletedContracts(),
                    data.getTotalEarnings(),
                    data.getTotalDistance()));
        }

        sender.sendMessage("§7Use §e/sr stats <player> §7to view player details!");
    }

    private String formatMaterialName(String material) {
        String name = material.toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    /**
     * Show active contracts.
     */
    private void showContracts(Player player) {
        List<Contract> contracts = plugin.getContractRegistry().getActiveContractsForTransporter(player.getUniqueId());

        if (contracts.isEmpty()) {
            String message = plugin.getConfig().getString("messages.contract.noActiveContracts",
                    "You have no active contracts");
            player.sendMessage(miniMessage.deserialize(message));
            return;
        }

        player.sendMessage("§6§lYour Active Contracts:");
        for (Contract contract : contracts) {
            String status = contract.isPickedUp() ? "§aIn Transit" : "§eAwaiting Pickup";
            player.sendMessage(String.format("§7- §f%dx %s §7from §e%s §7to §e%s §7(§a$%.2f§7) %s",
                    contract.getQuantity(),
                    contract.getItem().getType().name(),
                    contract.getOriginRegion(),
                    contract.getDestinationRegion(),
                    contract.getCurrentBounty(),
                    status));
        }
    }

    /**
     * Handle admin subcommands.
     */
    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /silkroad admin <reload|contracts|cancel>");
            return true;
        }

        String adminSubCommand = args[1].toLowerCase();

        switch (adminSubCommand) {
            case "reload":
                plugin.reloadConfig();
                String message = plugin.getConfig().getString("messages.admin.reloaded",
                        "Configuration reloaded!");
                sender.sendMessage(miniMessage.deserialize(message));
                return true;

            case "contracts":
                showAllContracts(sender);
                return true;

            case "cancel":
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /silkroad admin cancel <contractId>");
                    return true;
                }
                return cancelContract(sender, args[2]);

            default:
                sender.sendMessage("§cUnknown admin subcommand!");
                return true;
        }
    }

    /**
     * Show all active contracts (admin).
     */
    private void showAllContracts(CommandSender sender) {
        List<Contract> contracts = new ArrayList<>(plugin.getContractRegistry().getAllContracts());

        sender.sendMessage("§6§lAll Active Contracts (" + contracts.size() + "):");

        int shown = 0;
        for (Contract contract : contracts) {
            if (shown >= 20) {
                sender.sendMessage("§7... and " + (contracts.size() - shown) + " more");
                break;
            }

            sender.sendMessage(String.format("§7%s: §f%s §7| §e%s→%s §7| §a$%.2f §7| %s",
                    contract.getContractId().toString().substring(0, 8),
                    contract.getState().name(),
                    contract.getOriginRegion(),
                    contract.getDestinationRegion(),
                    contract.getCurrentBounty(),
                    contract.getTransporter() != null ? "Transporter: " + contract.getTransporter().toString().substring(0, 8) : "No transporter"));

            shown++;
        }
    }

    /**
     * Cancel a contract (admin).
     */
    private boolean cancelContract(CommandSender sender, String contractIdStr) {
        try {
            // Support partial UUID matching
            Contract contract = null;
            if (contractIdStr.length() < 36) {
                // Partial match
                for (Contract c : plugin.getContractRegistry().getAllContracts()) {
                    if (c.getContractId().toString().startsWith(contractIdStr)) {
                        contract = c;
                        break;
                    }
                }
            } else {
                // Full UUID
                contract = plugin.getContractRegistry().getContract(java.util.UUID.fromString(contractIdStr));
            }

            if (contract == null) {
                String message = plugin.getConfig().getString("messages.admin.noSuchContract",
                        "No contract found with that ID!");
                sender.sendMessage(miniMessage.deserialize(message));
                return true;
            }

            contractManager.cancelContract(contract.getContractId(), "Cancelled by admin");

            String message = plugin.getConfig().getString("messages.admin.contractCancelled",
                    "Contract cancelled")
                    .replace("{contractId}", contract.getContractId().toString().substring(0, 8));
            sender.sendMessage(miniMessage.deserialize(message));

        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cInvalid contract ID!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("help");
            completions.add("stats");
            completions.add("contracts");
            completions.add("history");
            completions.add("leaderboard");
            completions.add("shops");
            completions.add("directory");
            if (sender.hasPermission("silkroad.admin")) {
                completions.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            // Add online player names for stats viewing
            for (Player online : Bukkit.getOnlinePlayers()) {
                completions.add(online.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            if (sender.hasPermission("silkroad.admin")) {
                completions.add("reload");
                completions.add("contracts");
                completions.add("cancel");
            }
        }

        return completions;
    }
}
