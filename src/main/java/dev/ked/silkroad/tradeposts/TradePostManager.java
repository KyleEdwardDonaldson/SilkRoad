package dev.ked.silkroad.tradeposts;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.integration.TownyIntegration;
import dev.ked.silkroad.storage.TradePostDataManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages trade post creation, removal, and tracking.
 */
public class TradePostManager {

    private final SilkRoadPlugin plugin;
    private final TradePostDataManager dataManager;

    // Track all trade posts by location
    private final Map<Location, TradePost> tradePostsByLocation;

    // Track trade posts by region
    private final Map<String, Set<TradePost>> tradePostsByRegion;

    public TradePostManager(SilkRoadPlugin plugin, TradePostDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.tradePostsByLocation = new ConcurrentHashMap<>();
        this.tradePostsByRegion = new ConcurrentHashMap<>();
    }

    /**
     * Create a new trade post at a location.
     */
    public boolean createTradePost(Player creator, Location location) {
        // Check if block is correct type
        Material blockType = location.getBlock().getType();
        String configuredType = plugin.getConfig().getString("tradePosts.blockType", "LECTERN");
        Material requiredType = Material.valueOf(configuredType);

        if (blockType != requiredType) {
            creator.sendMessage("§cTrade posts must be placed on " + requiredType.name() + " blocks!");
            return false;
        }

        // Check if location already has a trade post
        if (isTradePost(location)) {
            creator.sendMessage("§cA trade post already exists here!");
            return false;
        }

        // Check if in claimed territory (if required)
        if (plugin.getConfig().getBoolean("tradePosts.requiresClaim", true)) {
            TownyIntegration towny = plugin.getTownyIntegration();
            if (towny != null && towny.isEnabled()) {
                if (!towny.isInClaim(location)) {
                    creator.sendMessage("§cTrade posts must be placed in claimed territory!");
                    return false;
                }
            }
        }

        // Get region name
        String regionName = getRegionName(location);
        if (regionName == null) {
            regionName = "Wilderness";
        }

        // Check one-per-town limit
        if (plugin.getConfig().getBoolean("tradePosts.onePerTown", true)) {
            Set<TradePost> regionPosts = tradePostsByRegion.get(regionName);
            if (regionPosts != null && !regionPosts.isEmpty()) {
                creator.sendMessage("§cA trade post already exists in this region!");
                return false;
            }
        }

        // Create trade post
        TradePost tradePost = new TradePost(location, regionName, creator.getUniqueId());
        registerTradePost(tradePost);

        // Save
        dataManager.save(tradePost);

        creator.sendMessage("§aTradepost created for region: §e" + regionName);
        plugin.getLogger().info("Trade post created at " + location + " in region " + regionName +
                " by " + creator.getName());

        return true;
    }

    /**
     * Remove a trade post at a location.
     */
    public boolean removeTradePost(Location location) {
        TradePost tradePost = tradePostsByLocation.remove(location);
        if (tradePost == null) {
            return false;
        }

        // Remove from region index
        Set<TradePost> regionPosts = tradePostsByRegion.get(tradePost.getRegionName());
        if (regionPosts != null) {
            regionPosts.remove(tradePost);
            if (regionPosts.isEmpty()) {
                tradePostsByRegion.remove(tradePost.getRegionName());
            }
        }

        // Delete from storage
        dataManager.delete(location);

        plugin.getLogger().info("Trade post removed at " + location);
        return true;
    }

    /**
     * Register a trade post in memory.
     */
    public void registerTradePost(TradePost tradePost) {
        tradePostsByLocation.put(tradePost.getLocation(), tradePost);
        tradePostsByRegion.computeIfAbsent(tradePost.getRegionName(), k -> ConcurrentHashMap.newKeySet())
                .add(tradePost);
    }

    /**
     * Check if a location is a trade post.
     */
    public boolean isTradePost(Location location) {
        return tradePostsByLocation.containsKey(location);
    }

    /**
     * Get a trade post at a location.
     */
    public TradePost getTradePost(Location location) {
        return tradePostsByLocation.get(location);
    }

    /**
     * Get all trade posts.
     */
    public Collection<TradePost> getAllTradePosts() {
        return Collections.unmodifiableCollection(tradePostsByLocation.values());
    }

    /**
     * Get trade posts in a specific region.
     */
    public Set<TradePost> getTradePostsInRegion(String regionName) {
        Set<TradePost> posts = tradePostsByRegion.get(regionName);
        return posts != null ? Collections.unmodifiableSet(posts) : Collections.emptySet();
    }

    /**
     * Get the nearest trade post to a location.
     */
    public TradePost getNearestTradePost(Location location) {
        TradePost nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (TradePost post : tradePostsByLocation.values()) {
            if (!post.getLocation().getWorld().equals(location.getWorld())) {
                continue;
            }

            double distance = post.getLocation().distance(location);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = post;
            }
        }

        return nearest;
    }

    /**
     * Get the nearest trade post in a specific region.
     */
    public TradePost getNearestTradePostInRegion(String regionName) {
        Set<TradePost> regionPosts = tradePostsByRegion.get(regionName);
        if (regionPosts == null || regionPosts.isEmpty()) {
            return null;
        }

        // Just return the first one (only one per region if onePerTown is enabled)
        return regionPosts.iterator().next();
    }

    /**
     * Get region name at a location.
     */
    private String getRegionName(Location location) {
        TownyIntegration towny = plugin.getTownyIntegration();
        if (towny != null && towny.isEnabled()) {
            return towny.getRegionName(location);
        }
        return null;
    }

    /**
     * Clear all trade posts (for testing).
     */
    public void clear() {
        tradePostsByLocation.clear();
        tradePostsByRegion.clear();
    }

    /**
     * Get the count of trade posts.
     */
    public int getTradePostCount() {
        return tradePostsByLocation.size();
    }
}
