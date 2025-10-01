package dev.ked.silkroad.bounty;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.contracts.Contract;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;

/**
 * Calculates initial bounty from distance + value using region-specific rates.
 */
public class BountyCalculator {

    private final SilkRoadPlugin plugin;

    public BountyCalculator(SilkRoadPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Calculate the initial bounty for a contract.
     * Formula: (distance component) + (value component)
     *
     * Distance component: Σ (blocks_in_region × region_rate)
     * Value component: (shopPrice × quantity) × valueMultiplier
     */
    public double calculateBounty(Contract contract) {
        double distanceComponent = calculateDistanceComponent(contract);
        double valueComponent = calculateValueComponent(contract);

        double totalBounty = distanceComponent + valueComponent;

        // Ensure minimum bounty
        return Math.max(totalBounty, 10.0);
    }

    /**
     * Calculate distance-based component of bounty.
     */
    private double calculateDistanceComponent(Contract contract) {
        Map<String, Double> regionDistances = contract.getRegionDistances();
        ConfigurationSection regionRates = plugin.getConfig().getConfigurationSection("bounty.regionRates");

        if (regionRates == null) {
            plugin.getLogger().warning("No region rates configured! Using default rate.");
            return contract.getTotalDistance() * 0.10; // Default fallback
        }

        double total = 0.0;
        for (Map.Entry<String, Double> entry : regionDistances.entrySet()) {
            String region = entry.getKey().toLowerCase();
            double distance = entry.getValue();

            // Get rate for this region (default to 0.10 if not configured)
            double rate = regionRates.getDouble(region, 0.10);

            total += distance * rate;
        }

        return total;
    }

    /**
     * Calculate value-based component of bounty.
     */
    private double calculateValueComponent(Contract contract) {
        double totalValue = contract.getShopPrice() * contract.getQuantity();
        double valueMultiplier = plugin.getConfig().getDouble("bounty.valueMultiplier", 0.15);

        return totalValue * valueMultiplier;
    }

    /**
     * Calculate adaptive decay rate.
     * Longer journeys decay slower to encourage long-distance transport.
     *
     * Formula: decayRate = baseDecayRate / (1 + (totalDistance / 1000.0))
     *
     * Examples:
     * - 500 blocks:  decay = 0.10 / 1.5  = $0.067/sec  (~92 min)
     * - 2000 blocks: decay = 0.10 / 3.0  = $0.033/sec  (~187 min)
     * - 5000 blocks: decay = 0.10 / 6.0  = $0.017/sec  (~364 min)
     *
     * Minimum duration: configured minimumDuration (default 10 minutes)
     */
    public double calculateDecayRate(Contract contract) {
        double baseDecayRate = plugin.getConfig().getDouble("bounty.decay.baseRate", 0.10);
        double totalDistance = contract.getTotalDistance();

        // Adaptive decay based on distance
        double decayRate = baseDecayRate / (1.0 + (totalDistance / 1000.0));

        // Ensure minimum contract duration
        int minimumDuration = plugin.getConfig().getInt("bounty.decay.minimumDuration", 600); // seconds
        double initialBounty = contract.getInitialBounty();

        if (initialBounty / decayRate < minimumDuration) {
            decayRate = initialBounty / minimumDuration;
        }

        return decayRate;
    }

    /**
     * Calculate required transporter level based on contract value.
     */
    public int calculateRequiredLevel(double totalValue) {
        if (!plugin.getConfig().getBoolean("progression.enabled", true)) {
            return 1; // No level requirement if progression disabled
        }

        ConfigurationSection levels = plugin.getConfig().getConfigurationSection("progression.levels");
        if (levels == null) {
            return 1;
        }

        // Find the highest level whose maxValue requirement is met
        int requiredLevel = 1;
        for (String levelKey : levels.getKeys(false)) {
            int level = Integer.parseInt(levelKey);
            double maxValue = levels.getDouble(levelKey + ".maxValue", 999999);

            if (totalValue <= maxValue) {
                requiredLevel = level;
                break;
            } else {
                requiredLevel = level; // Keep going up
            }
        }

        return requiredLevel;
    }

    /**
     * Get the region rate for a specific region (for display purposes).
     */
    public double getRegionRate(String region) {
        ConfigurationSection regionRates = plugin.getConfig().getConfigurationSection("bounty.regionRates");
        if (regionRates == null) {
            return 0.10;
        }
        return regionRates.getDouble(region.toLowerCase(), 0.10);
    }

    /**
     * Estimate time until bounty expires (in seconds).
     */
    public long estimateTimeToExpiry(Contract contract) {
        if (contract.getDecayRate() <= 0) {
            return Long.MAX_VALUE;
        }
        return (long) (contract.getCurrentBounty() / contract.getDecayRate());
    }

    /**
     * Format bounty value for display.
     */
    public String formatBounty(double bounty) {
        return String.format("$%.2f", bounty);
    }

    /**
     * Format time remaining for display.
     */
    public String formatTimeRemaining(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + "m";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }
}
