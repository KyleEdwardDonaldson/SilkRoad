package dev.ked.silkroad.contracts;

import dev.ked.silkroad.transport.TransporterManager;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

/**
 * Filter options for contract browsing.
 */
public enum ContractFilter {
    ALL("All Contracts", "§7Show all available contracts"),
    HIGH_VALUE("High Value Only", "§7Contracts worth $500+"),
    EXPIRING_SOON("Expiring Soon", "§7Less than 10 minutes remaining"),
    SHORT_DISTANCE("Nearby", "§7Under 500 blocks"),
    LONG_DISTANCE("Long Haul", "§71000+ blocks"),
    HIGH_BOUNTY("High Bounty", "§7$200+ current bounty");

    private final String displayName;
    private final String description;

    ContractFilter(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get the predicate filter for this filter type.
     */
    public Predicate<Contract> getPredicate(Player player, TransporterManager transporterManager) {
        return switch (this) {
            case ALL -> contract -> true;
            case HIGH_VALUE -> contract -> contract.getTotalValue() >= 500;
            case EXPIRING_SOON -> contract -> contract.getTimeRemaining() < 600000; // 10 minutes
            case SHORT_DISTANCE -> contract -> contract.getTotalDistance() < 500;
            case LONG_DISTANCE -> contract -> contract.getTotalDistance() >= 1000;
            case HIGH_BOUNTY -> contract -> contract.getCurrentBounty() >= 200;
        };
    }

    /**
     * Get next filter in cycle.
     */
    public ContractFilter next() {
        ContractFilter[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /**
     * Get previous filter in cycle.
     */
    public ContractFilter previous() {
        ContractFilter[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }
}
