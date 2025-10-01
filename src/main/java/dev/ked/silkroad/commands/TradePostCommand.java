package dev.ked.silkroad.commands;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.tradeposts.TradePost;
import dev.ked.silkroad.tradeposts.TradePostManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;

/**
 * /tradepost command handler.
 */
public class TradePostCommand implements CommandExecutor, TabCompleter {

    private final SilkRoadPlugin plugin;
    private final TradePostManager tradePostManager;
    private final MiniMessage miniMessage;

    public TradePostCommand(SilkRoadPlugin plugin, TradePostManager tradePostManager) {
        this.plugin = plugin;
        this.tradePostManager = tradePostManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Default: show help
        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                return createTradePost(player);

            case "remove":
                return removeTradePost(player);

            case "info":
                return showTradePostInfo(player);

            case "list":
                return listTradePosts(player);

            default:
                player.sendMessage("§cUnknown subcommand! Use §e/tradepost help");
                return true;
        }
    }

    /**
     * Show help message.
     */
    private void showHelp(Player player) {
        player.sendMessage("§6§lTrade Post Commands:");
        player.sendMessage(miniMessage.deserialize(plugin.getConfig().getString("messages.help.tradepostCreate", "")));
        player.sendMessage(miniMessage.deserialize(plugin.getConfig().getString("messages.help.tradepostRemove", "")));
        player.sendMessage(miniMessage.deserialize(plugin.getConfig().getString("messages.help.tradepostInfo", "")));
    }

    /**
     * Create a trade post at the block the player is looking at.
     */
    private boolean createTradePost(Player player) {
        if (!player.hasPermission("silkroad.tradepost.create")) {
            String message = plugin.getConfig().getString("messages.error.noPermission",
                    "You don't have permission!");
            player.sendMessage(miniMessage.deserialize(message));
            return true;
        }

        // Get the block the player is looking at
        Block targetBlock = getTargetBlock(player);
        if (targetBlock == null) {
            String message = plugin.getConfig().getString("messages.tradePost.notLookingAt",
                    "You must be looking at a block!");
            player.sendMessage(miniMessage.deserialize(message));
            return true;
        }

        // Check if it's the correct block type
        String configuredType = plugin.getConfig().getString("tradePosts.blockType", "LECTERN");
        Material requiredType;
        try {
            requiredType = Material.valueOf(configuredType);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid trade post block type in config: " + configuredType);
            requiredType = Material.LECTERN;
        }

        if (targetBlock.getType() != requiredType) {
            player.sendMessage("§cYou must be looking at a " + requiredType.name() + " block!");
            return true;
        }

        // Create the trade post
        boolean success = tradePostManager.createTradePost(player, targetBlock.getLocation());

        if (success) {
            String message = plugin.getConfig().getString("messages.tradePost.created",
                    "Trade Post created!");
            player.sendMessage(miniMessage.deserialize(message));
        }

        return true;
    }

    /**
     * Remove a trade post at the block the player is looking at.
     */
    private boolean removeTradePost(Player player) {
        if (!player.hasPermission("silkroad.admin.tradepost") &&
            !player.hasPermission("silkroad.tradepost.create")) {
            String message = plugin.getConfig().getString("messages.error.noPermission",
                    "You don't have permission!");
            player.sendMessage(miniMessage.deserialize(message));
            return true;
        }

        // Get the block the player is looking at
        Block targetBlock = getTargetBlock(player);
        if (targetBlock == null) {
            String message = plugin.getConfig().getString("messages.tradePost.notLookingAt",
                    "You must be looking at a Trade Post!");
            player.sendMessage(miniMessage.deserialize(message));
            return true;
        }

        Location location = targetBlock.getLocation();
        if (!tradePostManager.isTradePost(location)) {
            player.sendMessage("§cThat is not a Trade Post!");
            return true;
        }

        // Check if player created it (unless admin)
        TradePost tradePost = tradePostManager.getTradePost(location);
        if (!player.hasPermission("silkroad.admin.tradepost")) {
            if (!tradePost.getCreatorId().equals(player.getUniqueId())) {
                player.sendMessage("§cYou can only remove Trade Posts you created!");
                return true;
            }
        }

        // Remove the trade post
        tradePostManager.removeTradePost(location);

        String message = plugin.getConfig().getString("messages.tradePost.removed",
                "Trade Post removed");
        player.sendMessage(miniMessage.deserialize(message));

        return true;
    }

    /**
     * Show info about the trade post the player is looking at.
     */
    private boolean showTradePostInfo(Player player) {
        Block targetBlock = getTargetBlock(player);
        if (targetBlock == null) {
            String message = plugin.getConfig().getString("messages.tradePost.notLookingAt",
                    "You must be looking at a Trade Post!");
            player.sendMessage(miniMessage.deserialize(message));
            return true;
        }

        Location location = targetBlock.getLocation();
        if (!tradePostManager.isTradePost(location)) {
            player.sendMessage("§cThat is not a Trade Post!");
            return true;
        }

        TradePost tradePost = tradePostManager.getTradePost(location);

        player.sendMessage("§6§lTrade Post Info:");
        player.sendMessage("§7Region: §e" + tradePost.getRegionName());
        player.sendMessage("§7Creator: §f" + tradePost.getCreatorId());
        player.sendMessage("§7Created: §f" + formatTime(tradePost.getCreatedAt()));
        player.sendMessage("§7Location: §f" + formatLocation(location));

        return true;
    }

    /**
     * List all trade posts.
     */
    private boolean listTradePosts(Player player) {
        List<TradePost> tradePosts = new ArrayList<>(tradePostManager.getAllTradePosts());

        if (tradePosts.isEmpty()) {
            player.sendMessage("§eThere are no Trade Posts yet!");
            return true;
        }

        player.sendMessage("§6§lTrade Posts (" + tradePosts.size() + "):");

        for (TradePost post : tradePosts) {
            Location loc = post.getLocation();
            player.sendMessage(String.format("§7- §e%s §7at §f%s",
                    post.getRegionName(),
                    formatLocation(loc)));
        }

        return true;
    }

    /**
     * Get the block the player is looking at (raycast).
     */
    private Block getTargetBlock(Player player) {
        RayTraceResult result = player.rayTraceBlocks(5.0);
        if (result == null || result.getHitBlock() == null) {
            return null;
        }
        return result.getHitBlock();
    }

    /**
     * Format a location for display.
     */
    private String formatLocation(Location loc) {
        return String.format("%d, %d, %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Format a timestamp.
     */
    private String formatTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        } else {
            return "just now";
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("create");
            completions.add("remove");
            completions.add("info");
            completions.add("list");
        }

        return completions;
    }
}
