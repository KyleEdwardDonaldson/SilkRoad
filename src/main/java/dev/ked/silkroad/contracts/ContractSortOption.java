package dev.ked.silkroad.contracts;

import java.util.Comparator;

/**
 * Sort options for contract browsing.
 */
public enum ContractSortOption {
    BOUNTY_HIGH("Bounty (High → Low)", "§7Highest paying first"),
    BOUNTY_LOW("Bounty (Low → High)", "§7Lowest paying first"),
    DISTANCE_NEAR("Distance (Near → Far)", "§7Shortest trips first"),
    DISTANCE_FAR("Distance (Far → Near)", "§7Longest trips first"),
    EXPIRY_SOON("Expiring Soon", "§7Least time remaining first"),
    EXPIRY_LATER("Expiring Later", "§7Most time remaining first");

    private final String displayName;
    private final String description;

    ContractSortOption(String displayName, String description) {
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
     * Get the comparator for this sort option.
     */
    public Comparator<Contract> getComparator() {
        return switch (this) {
            case BOUNTY_HIGH -> Comparator.comparingDouble(Contract::getCurrentBounty).reversed();
            case BOUNTY_LOW -> Comparator.comparingDouble(Contract::getCurrentBounty);
            case DISTANCE_NEAR -> Comparator.comparingDouble(Contract::getTotalDistance);
            case DISTANCE_FAR -> Comparator.comparingDouble(Contract::getTotalDistance).reversed();
            case EXPIRY_SOON -> Comparator.comparingLong(Contract::getTimeRemaining);
            case EXPIRY_LATER -> Comparator.comparingLong(Contract::getTimeRemaining).reversed();
        };
    }

    /**
     * Get next sort option in cycle.
     */
    public ContractSortOption next() {
        ContractSortOption[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /**
     * Get previous sort option in cycle.
     */
    public ContractSortOption previous() {
        ContractSortOption[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }
}
