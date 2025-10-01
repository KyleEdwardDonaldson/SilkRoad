package dev.ked.silkroad.transport;

import org.bukkit.Material;

import java.util.UUID;

/**
 * Record of a completed contract for history tracking.
 */
public class CompletedContractRecord {
    private final UUID contractId;
    private final long completedAt;
    private final String originRegion;
    private final String destinationRegion;
    private final Material itemType;
    private final int quantity;
    private final double bountyEarned;
    private final double distance;
    private final long travelTime;

    public CompletedContractRecord(UUID contractId, long completedAt, String originRegion,
                                   String destinationRegion, Material itemType, int quantity,
                                   double bountyEarned, double distance, long travelTime) {
        this.contractId = contractId;
        this.completedAt = completedAt;
        this.originRegion = originRegion;
        this.destinationRegion = destinationRegion;
        this.itemType = itemType;
        this.quantity = quantity;
        this.bountyEarned = bountyEarned;
        this.distance = distance;
        this.travelTime = travelTime;
    }

    public UUID getContractId() {
        return contractId;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public String getOriginRegion() {
        return originRegion;
    }

    public String getDestinationRegion() {
        return destinationRegion;
    }

    public Material getItemType() {
        return itemType;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getBountyEarned() {
        return bountyEarned;
    }

    public double getDistance() {
        return distance;
    }

    public long getTravelTime() {
        return travelTime;
    }

    public String getRoute() {
        return originRegion + " → " + destinationRegion;
    }
}
