package dev.ked.silkroad.transport;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.cargo.CargoItem;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks player movement while carrying cargo and awards XP for distance traveled.
 * Awards XP every 100 blocks traveled with cargo.
 */
public class MovementTracker implements Listener {

    private final SilkRoadPlugin plugin;
    private final TransporterManager transporterManager;

    // Track last location for each player
    private final Map<UUID, Location> lastLocations;

    // Accumulate distance until threshold is reached
    private final Map<UUID, Double> distanceAccumulators;

    // Configuration
    private double xpAwardInterval; // Distance in blocks before awarding XP
    private int xpPerInterval; // XP to award per interval
    private boolean trackFlying;
    private boolean trackBoating;
    private boolean trackHorseback;

    public MovementTracker(SilkRoadPlugin plugin, TransporterManager transporterManager) {
        this.plugin = plugin;
        this.transporterManager = transporterManager;
        this.lastLocations = new HashMap<>();
        this.distanceAccumulators = new HashMap<>();

        loadConfig();
    }

    /**
     * Load configuration settings
     */
    private void loadConfig() {
        xpAwardInterval = plugin.getConfig().getDouble("movement.xpAwardInterval", 100.0);
        xpPerInterval = plugin.getConfig().getInt("movement.xpPerInterval", 10);
        trackFlying = plugin.getConfig().getBoolean("movement.trackFlying", true);
        trackBoating = plugin.getConfig().getBoolean("movement.trackBoating", true);
        trackHorseback = plugin.getConfig().getBoolean("movement.trackHorseback", true);
    }

    /**
     * Reload configuration
     */
    public void reload() {
        loadConfig();
    }

    /**
     * Track player movement and award XP for carrying cargo
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Ignore if player didn't actually move (just head movement)
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() &&
                          from.getBlockY() == to.getBlockY() &&
                          from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if player has cargo
        if (!hasCargo(player)) {
            // Clean up tracking data if no cargo
            lastLocations.remove(playerId);
            distanceAccumulators.remove(playerId);
            return;
        }

        // Check movement type restrictions
        if (!trackFlying && player.isFlying()) {
            return;
        }

        if (!trackBoating && player.isInsideVehicle() &&
            player.getVehicle() != null &&
            player.getVehicle().getType().name().contains("BOAT")) {
            return;
        }

        if (!trackHorseback && player.isInsideVehicle() &&
            player.getVehicle() != null &&
            player.getVehicle().getType().name().contains("HORSE")) {
            return;
        }

        // Get last known location
        Location lastLocation = lastLocations.get(playerId);
        if (lastLocation == null) {
            // First movement, just store location
            lastLocations.put(playerId, to.clone());
            return;
        }

        // Check if still in same world
        if (!lastLocation.getWorld().equals(to.getWorld())) {
            lastLocations.put(playerId, to.clone());
            return;
        }

        // Calculate distance traveled
        double distance = lastLocation.distance(to);

        // Update last location
        lastLocations.put(playerId, to.clone());

        // Accumulate distance
        double accumulated = distanceAccumulators.getOrDefault(playerId, 0.0) + distance;

        // Award XP if threshold reached
        while (accumulated >= xpAwardInterval) {
            awardTravelXP(player);
            accumulated -= xpAwardInterval;
        }

        // Store accumulated distance
        distanceAccumulators.put(playerId, accumulated);
    }

    /**
     * Clean up tracking data when player leaves
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastLocations.remove(playerId);
        distanceAccumulators.remove(playerId);
    }

    /**
     * Check if player is carrying cargo
     */
    private boolean hasCargo(Player player) {
        // Check all inventory slots for cargo items
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && CargoItem.isCargoItem(plugin, item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Award XP for traveling with cargo
     */
    private void awardTravelXP(Player player) {
        UUID playerId = player.getUniqueId();

        // Award XP
        transporterManager.awardXP(playerId, xpPerInterval);

        // Optional: Send message to player (can be disabled in config)
        if (plugin.getConfig().getBoolean("movement.notifyPlayer", false)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                "§6+§e" + xpPerInterval + " XP §7(travel)"
            ));
        }

        // Log for debugging
        if (plugin.getConfig().getBoolean("debug.logMovementXP", false)) {
            plugin.getLogger().info(player.getName() + " earned " + xpPerInterval +
                    " XP for traveling with cargo (" +
                    String.format("%.0f", xpAwardInterval) + " blocks)");
        }
    }

    /**
     * Get total distance accumulated for a player (for debugging)
     */
    public double getAccumulatedDistance(UUID playerId) {
        return distanceAccumulators.getOrDefault(playerId, 0.0);
    }

    /**
     * Clear all tracking data (for testing)
     */
    public void clearAll() {
        lastLocations.clear();
        distanceAccumulators.clear();
    }
}
