package dev.ked.silkroad.tradeposts;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Data model for a trade post location.
 * Trade posts are physical blocks (Lecterns or Barrels) where players can:
 * - Browse available contracts
 * - Accept contracts
 * - Deliver cargo
 * - Pick up orders
 */
public class TradePost {

    private final Location location;
    private final String regionName;
    private final String townName;
    private final UUID creatorId;
    private final long createdAt;

    public TradePost(Location location, String regionName, UUID creatorId) {
        this(location, regionName, "Unknown", creatorId);
    }

    public TradePost(Location location, String regionName, String townName, UUID creatorId) {
        this.location = location;
        this.regionName = regionName;
        this.townName = townName;
        this.creatorId = creatorId;
        this.createdAt = System.currentTimeMillis();
    }

    public Location getLocation() {
        return location;
    }

    public String getRegionName() {
        return regionName;
    }

    public UUID getCreatorId() {
        return creatorId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "TradePost{" +
                "location=" + location +
                ", region=" + regionName +
                ", creator=" + creatorId +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradePost tradePost = (TradePost) o;
        return location.equals(tradePost.location);
    }

    @Override
    public int hashCode() {
        return location.hashCode();
    }
}
