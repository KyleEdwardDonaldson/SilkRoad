package dev.ked.silkroad.bounty;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.contracts.Contract;
import dev.ked.silkroad.contracts.ContractRegistry;
import dev.ked.silkroad.contracts.ContractState;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * Handles bounty decay over time using adaptive decay formula.
 * Updates bounties every X seconds (configured) and expires contracts when bounty reaches $0.
 */
public class DecayManager {

    private final SilkRoadPlugin plugin;
    private final ContractRegistry registry;
    private final MiniMessage miniMessage;
    private BukkitTask decayTask;

    public DecayManager(SilkRoadPlugin plugin, ContractRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Start the decay task.
     */
    public void start() {
        int tickInterval = plugin.getConfig().getInt("bounty.decay.tickInterval", 5);
        long ticksInterval = tickInterval * 20L; // Convert seconds to ticks

        decayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickDecay, ticksInterval, ticksInterval);

        plugin.getLogger().info("Decay manager started (updating every " + tickInterval + " seconds)");
    }

    /**
     * Stop the decay task.
     */
    public void stop() {
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
    }

    /**
     * Tick decay for all active contracts.
     */
    private void tickDecay() {
        int tickInterval = plugin.getConfig().getInt("bounty.decay.tickInterval", 5);

        // Get all contracts that should decay
        List<Contract> activeContracts = registry.getContractsByState(ContractState.ACCEPTED);
        activeContracts.addAll(registry.getContractsByState(ContractState.IN_TRANSIT));

        for (Contract contract : activeContracts) {
            // Skip if already expired
            if (contract.isExpired()) {
                handleExpiredContract(contract);
                continue;
            }

            // Apply decay
            double decayAmount = contract.getDecayRate() * tickInterval;
            double newBounty = contract.getCurrentBounty() - decayAmount;
            contract.setCurrentBounty(newBounty);

            // Check for expiration
            if (newBounty <= 0) {
                handleExpiredContract(contract);
            } else {
                // Check for warning thresholds
                checkWarningThresholds(contract);
            }
        }
    }

    /**
     * Handle contract expiration.
     */
    private void handleExpiredContract(Contract contract) {
        plugin.getContractManager().expireContract(contract.getContractId());
    }

    /**
     * Check and send warning messages at specific time thresholds.
     */
    private void checkWarningThresholds(Contract contract) {
        long timeRemaining = contract.getTimeRemaining();

        // 30-minute warning
        if (timeRemaining <= 30 * 60 * 1000 && timeRemaining > 29 * 60 * 1000) {
            sendWarning(contract, "contract.expiryWarning30");
        }

        // 10-minute warning
        if (timeRemaining <= 10 * 60 * 1000 && timeRemaining > 9 * 60 * 1000) {
            sendWarning(contract, "contract.expiryWarning10");
        }
    }

    /**
     * Send warning message to transporter.
     */
    private void sendWarning(Contract contract, String messageKey) {
        if (contract.getTransporter() == null) {
            return;
        }

        Player transporter = Bukkit.getPlayer(contract.getTransporter());
        if (transporter == null || !transporter.isOnline()) {
            return;
        }

        String message = plugin.getConfig().getString("messages." + messageKey,
                "Warning: Contract expiring soon!")
                .replace("{bounty}", String.format("%.2f", contract.getCurrentBounty()));

        transporter.sendMessage(miniMessage.deserialize(message));
    }

    /**
     * Manually update bounty for a specific contract (for real-time display).
     */
    public void updateBounty(Contract contract) {
        if (contract == null || contract.isExpired()) {
            return;
        }

        long now = System.currentTimeMillis();
        long timeSinceAccepted = now - contract.getAcceptedAt();
        double secondsElapsed = timeSinceAccepted / 1000.0;

        double newBounty = contract.getInitialBounty() - (contract.getDecayRate() * secondsElapsed);
        contract.setCurrentBounty(Math.max(0, newBounty));
    }

    /**
     * Get the current bounty for a contract (with real-time calculation).
     */
    public double getCurrentBounty(Contract contract) {
        if (contract == null || contract.isExpired()) {
            return 0.0;
        }

        if (contract.getState() != ContractState.ACCEPTED && contract.getState() != ContractState.IN_TRANSIT) {
            return contract.getCurrentBounty();
        }

        long now = System.currentTimeMillis();
        long timeSinceAccepted = now - contract.getAcceptedAt();
        double secondsElapsed = timeSinceAccepted / 1000.0;

        double currentBounty = contract.getInitialBounty() - (contract.getDecayRate() * secondsElapsed);
        return Math.max(0, currentBounty);
    }

    /**
     * Get time remaining until bounty expires (in milliseconds).
     */
    public long getTimeRemaining(Contract contract) {
        if (contract == null || contract.isExpired()) {
            return 0;
        }

        double currentBounty = getCurrentBounty(contract);
        if (currentBounty <= 0) {
            return 0;
        }

        long secondsRemaining = (long) (currentBounty / contract.getDecayRate());
        return secondsRemaining * 1000;
    }

    /**
     * Format time remaining as human-readable string.
     */
    public String formatTimeRemaining(long milliseconds) {
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
