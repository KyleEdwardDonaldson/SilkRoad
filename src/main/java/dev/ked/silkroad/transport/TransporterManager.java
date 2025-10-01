package dev.ked.silkroad.transport;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.contracts.Contract;
import dev.ked.silkroad.storage.TransporterDataManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages transporter levels, XP, and active contract tracking.
 */
public class TransporterManager {

    private final SilkRoadPlugin plugin;
    private final TransporterDataManager dataManager;
    private final MiniMessage miniMessage;

    // In-memory storage for transporter data
    private final Map<UUID, TransporterData> transporterData;

    public TransporterManager(SilkRoadPlugin plugin, TransporterDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.miniMessage = MiniMessage.miniMessage();
        this.transporterData = new HashMap<>();
    }

    /**
     * Get or create transporter data for a player.
     */
    private TransporterData getTransporterData(UUID playerId) {
        return transporterData.computeIfAbsent(playerId, id -> {
            // Try to load from disk first
            TransporterData data = dataManager.load(playerId);
            if (data == null) {
                data = new TransporterData(playerId);
            }
            return data;
        });
    }

    /**
     * Get a transporter's level.
     */
    public int getLevel(UUID playerId) {
        return getTransporterData(playerId).getLevel();
    }

    /**
     * Get a transporter's XP.
     */
    public int getXP(UUID playerId) {
        return getTransporterData(playerId).getXp();
    }

    /**
     * Get the maximum number of concurrent contracts a transporter can have.
     */
    public int getMaxContracts(UUID playerId) {
        if (!plugin.getConfig().getBoolean("progression.enabled", true)) {
            return 5; // Default max if progression disabled
        }

        int level = getLevel(playerId);
        ConfigurationSection levels = plugin.getConfig().getConfigurationSection("progression.levels");

        if (levels == null) {
            return 1;
        }

        return levels.getInt(level + ".maxContracts", 1);
    }

    /**
     * Get the insurance discount for a transporter (as a percentage, e.g., 0.10 = 10%).
     */
    public double getInsuranceDiscount(UUID playerId) {
        if (!plugin.getConfig().getBoolean("progression.enabled", true)) {
            return 0.0;
        }

        int level = getLevel(playerId);
        ConfigurationSection levels = plugin.getConfig().getConfigurationSection("progression.levels");

        if (levels == null) {
            return 0.0;
        }

        return levels.getDouble(level + ".insuranceDiscount", 0.0);
    }

    /**
     * Get the level name (e.g., "Novice", "Trader", etc.).
     */
    public String getLevelName(UUID playerId) {
        int level = getLevel(playerId);
        ConfigurationSection levels = plugin.getConfig().getConfigurationSection("progression.levels");

        if (levels == null) {
            return "Transporter";
        }

        return levels.getString(level + ".name", "Transporter");
    }

    /**
     * Get XP required for the next level.
     */
    public int getNextLevelXP(UUID playerId) {
        int currentLevel = getLevel(playerId);
        ConfigurationSection levels = plugin.getConfig().getConfigurationSection("progression.levels");

        if (levels == null) {
            return Integer.MAX_VALUE;
        }

        // Find next level
        for (String levelKey : levels.getKeys(false)) {
            int level = Integer.parseInt(levelKey);
            if (level > currentLevel) {
                return levels.getInt(level + ".xpRequired", Integer.MAX_VALUE);
            }
        }

        return Integer.MAX_VALUE; // Max level reached
    }

    /**
     * Award XP and handle level-ups.
     */
    public void awardXP(UUID playerId, int xp) {
        if (!plugin.getConfig().getBoolean("progression.enabled", true)) {
            return;
        }

        TransporterData data = getTransporterData(playerId);
        int oldLevel = data.getLevel();
        data.addXp(xp);

        // Check for level up
        int newLevel = calculateLevel(data.getXp());
        if (newLevel > oldLevel) {
            data.setLevel(newLevel);
            handleLevelUp(playerId, newLevel);
        }

        // Save
        dataManager.save(data);

        // Notify player
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            String message = plugin.getConfig().getString("messages.progression.xpGained", "+{xp} Transport XP")
                    .replace("{xp}", String.valueOf(xp));
            player.sendMessage(miniMessage.deserialize(message));
        }
    }

    /**
     * Calculate level from XP.
     */
    private int calculateLevel(int xp) {
        ConfigurationSection levels = plugin.getConfig().getConfigurationSection("progression.levels");
        if (levels == null) {
            return 1;
        }

        int level = 1;
        for (String levelKey : levels.getKeys(false)) {
            int requiredXp = levels.getInt(levelKey + ".xpRequired", 0);
            if (xp >= requiredXp) {
                level = Integer.parseInt(levelKey);
            } else {
                break;
            }
        }

        return level;
    }

    /**
     * Handle level-up event.
     */
    private void handleLevelUp(UUID playerId, int newLevel) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }

        String levelName = getLevelName(playerId);
        String message = plugin.getConfig().getString("messages.progression.levelUp",
                "LEVEL UP! You are now a {level} transporter!")
                .replace("{level}", levelName);

        player.sendMessage(miniMessage.deserialize(message));

        // TODO: Play level-up sound/effects
        plugin.getLogger().info(player.getName() + " reached transporter level " + newLevel + " (" + levelName + ")");
    }

    /**
     * Award XP for contract completion.
     */
    public void awardCompletionXP(UUID playerId, Contract contract) {
        if (!plugin.getConfig().getBoolean("progression.enabled", true)) {
            return;
        }

        int xp = 0;

        // Base completion bonus
        xp += plugin.getConfig().getInt("progression.xpPerCompletion", 50);

        // Distance-based XP
        double distanceXpRate = plugin.getConfig().getDouble("progression.xpPerBlock", 0.1);
        xp += (int) (contract.getTotalDistance() * distanceXpRate);

        // Region crossing bonus
        int regionBonus = plugin.getConfig().getInt("progression.xpPerRegionCrossed", 25);
        xp += contract.getRegionDistances().size() * regionBonus;

        // High-value bonus
        if (contract.getTotalValue() > 1000) {
            xp += plugin.getConfig().getInt("progression.xpHighValueBonus", 100);
        }

        awardXP(playerId, xp);

        // Update stats
        TransporterData data = getTransporterData(playerId);
        data.incrementCompletedContracts();
        data.addTotalDistance(contract.getTotalDistance());
        data.addTotalEarnings(contract.getCurrentBounty());

        // Track travel time
        if (contract.getAcceptedAt() > 0 && contract.getDeliveredAt() > 0) {
            long travelTime = contract.getDeliveredAt() - contract.getAcceptedAt();
            data.addTravelTime(travelTime);
        }

        // Track destination region
        data.recordDelivery(contract.getDestinationRegion());

        // Track all regions visited
        for (String region : contract.getRegionDistances().keySet()) {
            data.recordRegionVisit(region);
        }

        // Add to history
        CompletedContractRecord record = new CompletedContractRecord(
                contract.getContractId(),
                contract.getDeliveredAt(),
                contract.getOriginRegion(),
                contract.getDestinationRegion(),
                contract.getItem().getType(),
                contract.getQuantity(),
                contract.getCurrentBounty(),
                contract.getTotalDistance(),
                contract.getDeliveredAt() - contract.getAcceptedAt()
        );
        data.addToHistory(record);

        dataManager.save(data);
    }

    /**
     * Record a failed contract.
     */
    public void recordFailedContract(UUID playerId) {
        TransporterData data = getTransporterData(playerId);
        data.incrementFailedContracts();
        dataManager.save(data);
    }

    /**
     * Record a cancelled contract.
     */
    public void recordCancelledContract(UUID playerId) {
        TransporterData data = getTransporterData(playerId);
        data.incrementCancelledContracts();
        dataManager.save(data);
    }

    /**
     * Get transporter statistics.
     */
    public TransporterData getStats(UUID playerId) {
        return getTransporterData(playerId);
    }

    /**
     * Data class for transporter information.
     */
    public static class TransporterData {
        private final UUID playerId;
        private int level;
        private int xp;
        private int completedContracts;
        private int failedContracts;
        private int cancelledContracts;
        private double totalDistance;
        private double totalEarnings;
        private long totalTravelTime; // milliseconds
        private final Map<String, Integer> regionDeliveries; // Region name -> delivery count
        private final Map<String, Integer> regionsVisited; // All regions visited -> visit count
        private final java.util.List<CompletedContractRecord> recentHistory; // Last 50 contracts

        public TransporterData(UUID playerId) {
            this.playerId = playerId;
            this.level = 1;
            this.xp = 0;
            this.completedContracts = 0;
            this.failedContracts = 0;
            this.cancelledContracts = 0;
            this.totalDistance = 0.0;
            this.totalEarnings = 0.0;
            this.totalTravelTime = 0L;
            this.regionDeliveries = new HashMap<>();
            this.regionsVisited = new HashMap<>();
            this.recentHistory = new java.util.ArrayList<>();
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        public int getXp() {
            return xp;
        }

        public void setXp(int xp) {
            this.xp = xp;
        }

        public void addXp(int amount) {
            this.xp += amount;
        }

        public int getCompletedContracts() {
            return completedContracts;
        }

        public void setCompletedContracts(int completedContracts) {
            this.completedContracts = completedContracts;
        }

        public void incrementCompletedContracts() {
            this.completedContracts++;
        }

        public double getTotalDistance() {
            return totalDistance;
        }

        public void setTotalDistance(double totalDistance) {
            this.totalDistance = totalDistance;
        }

        public void addTotalDistance(double distance) {
            this.totalDistance += distance;
        }

        public double getTotalEarnings() {
            return totalEarnings;
        }

        public void setTotalEarnings(double totalEarnings) {
            this.totalEarnings = totalEarnings;
        }

        public void addTotalEarnings(double earnings) {
            this.totalEarnings += earnings;
        }

        public int getFailedContracts() {
            return failedContracts;
        }

        public void setFailedContracts(int failedContracts) {
            this.failedContracts = failedContracts;
        }

        public void incrementFailedContracts() {
            this.failedContracts++;
        }

        public int getCancelledContracts() {
            return cancelledContracts;
        }

        public void setCancelledContracts(int cancelledContracts) {
            this.cancelledContracts = cancelledContracts;
        }

        public void incrementCancelledContracts() {
            this.cancelledContracts++;
        }

        public long getTotalTravelTime() {
            return totalTravelTime;
        }

        public void setTotalTravelTime(long totalTravelTime) {
            this.totalTravelTime = totalTravelTime;
        }

        public void addTravelTime(long milliseconds) {
            this.totalTravelTime += milliseconds;
        }

        public Map<String, Integer> getRegionDeliveries() {
            return regionDeliveries;
        }

        public void recordDelivery(String region) {
            regionDeliveries.put(region, regionDeliveries.getOrDefault(region, 0) + 1);
        }

        public Map<String, Integer> getRegionsVisited() {
            return regionsVisited;
        }

        public void recordRegionVisit(String region) {
            regionsVisited.put(region, regionsVisited.getOrDefault(region, 0) + 1);
        }

        public String getFavoriteRegion() {
            return regionDeliveries.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("None");
        }

        public double getSuccessRate() {
            int total = completedContracts + failedContracts + cancelledContracts;
            if (total == 0) return 0.0;
            return (double) completedContracts / total * 100.0;
        }

        public int getTotalContracts() {
            return completedContracts + failedContracts + cancelledContracts;
        }

        public java.util.List<CompletedContractRecord> getRecentHistory() {
            return recentHistory;
        }

        public void addToHistory(CompletedContractRecord record) {
            recentHistory.add(0, record); // Add to front
            // Keep only last 50
            if (recentHistory.size() > 50) {
                recentHistory.remove(recentHistory.size() - 1);
            }
        }
    }
}
