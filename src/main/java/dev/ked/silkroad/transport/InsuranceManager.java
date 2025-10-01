package dev.ked.silkroad.transport;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.contracts.Contract;
import dev.ked.silkroad.integration.TownyIntegration;
import dev.ked.silkroad.integration.VaultEconomy;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/**
 * Manages insurance payments to nation/town treasuries.
 * Insurance is charged when accepting contracts and deposited to the transporter's home territory.
 */
public class InsuranceManager {

    private final SilkRoadPlugin plugin;
    private final VaultEconomy economy;
    private final TownyIntegration townyIntegration;
    private final MiniMessage miniMessage;

    public InsuranceManager(SilkRoadPlugin plugin, VaultEconomy economy, TownyIntegration townyIntegration) {
        this.plugin = plugin;
        this.economy = economy;
        this.townyIntegration = townyIntegration;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Calculate insurance cost for a contract.
     * Insurance = (initial bounty × insurance rate) × (1 - discount)
     */
    public double calculateInsurance(Contract contract, java.util.UUID transporterId) {
        if (!plugin.getConfig().getBoolean("economy.insurance.enabled", true)) {
            return 0.0;
        }

        double baseRate = plugin.getConfig().getDouble("economy.insurance.rate", 0.10);
        double baseCost = contract.getInitialBounty() * baseRate;

        // Apply transporter level discount
        double discount = plugin.getTransporterManager().getInsuranceDiscount(transporterId);
        double finalCost = baseCost * (1.0 - discount);

        return Math.max(0, finalCost);
    }

    /**
     * Charge insurance from a transporter and deposit to their home territory.
     * Returns true if successful, false if player cannot afford or deposit fails.
     */
    public boolean chargeInsurance(Player transporter, Contract contract, double insuranceCost) {
        if (insuranceCost <= 0) {
            return true; // No insurance needed
        }

        // Check if player has enough money
        if (!economy.has(transporter.getUniqueId(), insuranceCost)) {
            return false;
        }

        // Withdraw from player
        if (!economy.withdraw(transporter.getUniqueId(), insuranceCost)) {
            return false;
        }

        // Deposit to territory treasury (if Towny is available)
        boolean depositSuccess = false;
        String destination = "Unknown";

        if (townyIntegration != null && townyIntegration.isEnabled()) {
            depositSuccess = townyIntegration.depositInsurance(transporter, insuranceCost,
                    "Silk Road Insurance - Contract " + contract.getContractId());

            if (depositSuccess) {
                String region = townyIntegration.getPlayerRegion(transporter);
                destination = region != null ? region : "Territory";
            }
        }

        if (!depositSuccess) {
            // Fallback: money is destroyed (economic sink) if no territory system
            plugin.getLogger().info("Insurance destroyed as economic sink: $" + insuranceCost +
                    " (no territory for " + transporter.getName() + ")");
            destination = "Economic Sink";
        }

        // Notify player
        String message = plugin.getConfig().getString("messages.territory.insurancePaid",
                "Insurance paid: ${insurance} to {territory}")
                .replace("{insurance}", String.format("%.2f", insuranceCost))
                .replace("{territory}", destination);
        transporter.sendMessage(miniMessage.deserialize(message));

        plugin.getLogger().info("Insurance charged: " + transporter.getName() + " paid $" + insuranceCost +
                " to " + destination);

        return true;
    }

    /**
     * Refund insurance to a transporter (rare case, e.g., contract cancelled by admin).
     */
    public void refundInsurance(java.util.UUID transporterId, double insuranceCost) {
        if (!plugin.getConfig().getBoolean("economy.insurance.refundable", false)) {
            // Insurance is not refundable by default
            return;
        }

        economy.deposit(transporterId, insuranceCost);
        plugin.getLogger().info("Insurance refunded: $" + insuranceCost + " to " + transporterId);
    }

    /**
     * Get the insurance rate from config.
     */
    public double getInsuranceRate() {
        return plugin.getConfig().getDouble("economy.insurance.rate", 0.10);
    }

    /**
     * Check if insurance is enabled.
     */
    public boolean isInsuranceEnabled() {
        return plugin.getConfig().getBoolean("economy.insurance.enabled", true);
    }

    /**
     * Format insurance amount for display.
     */
    public String formatInsurance(double amount) {
        return "$" + String.format("%.2f", amount);
    }
}
