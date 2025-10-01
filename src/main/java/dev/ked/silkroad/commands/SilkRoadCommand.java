package dev.ked.silkroad.commands;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.contracts.Contract;
import dev.ked.silkroad.contracts.ContractManager;
import dev.ked.silkroad.transport.TransporterManager;
import dev.ked.silkroad.transport.TransporterManager.TransporterData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

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
                showStats((Player) sender);
                return true;

            case "contracts":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command can only be used by players!");
                    return true;
                }
                showContracts((Player) sender);
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
            if (sender.hasPermission("silkroad.admin")) {
                completions.add("admin");
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
