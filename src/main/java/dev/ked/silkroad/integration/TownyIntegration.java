package dev.ked.silkroad.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.Resident;
import dev.ked.silkroad.SilkRoadPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Integration with Towny for region detection and treasury deposits.
 */
public class TownyIntegration {

    private final SilkRoadPlugin plugin;
    private final TownyAPI townyAPI;
    private final boolean useNationTreasury;

    public TownyIntegration(SilkRoadPlugin plugin) {
        this.plugin = plugin;
        this.townyAPI = TownyAPI.getInstance();
        this.useNationTreasury = plugin.getConfig().getBoolean("integration.towny.useNationTreasury", true);
    }

    /**
     * Get the region name at a location.
     * Returns the nation name if in a nation, otherwise town name, otherwise null.
     */
    public String getRegionName(Location location) {
        if (location == null) {
            return null;
        }

        TownBlock townBlock = townyAPI.getTownBlock(location);
        if (townBlock == null) {
            return null; // Wilderness
        }

        try {
            Town town = townBlock.getTown();
            if (town == null) {
                return null;
            }

            // Prefer nation name if available
            if (town.hasNation()) {
                Nation nation = town.getNation();
                return nation.getName();
            }

            // Fall back to town name
            return town.getName();
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting region name: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get a player's home region name.
     * Returns their nation name if in a nation, otherwise town name, otherwise null.
     */
    public String getPlayerRegion(Player player) {
        try {
            Resident resident = townyAPI.getResident(player);
            if (resident == null || !resident.hasTown()) {
                return null;
            }

            Town town = resident.getTown();

            // Prefer nation name if available
            if (town.hasNation()) {
                Nation nation = town.getNation();
                return nation.getName();
            }

            // Fall back to town name
            return town.getName();
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting player region: " + e.getMessage());
            return null;
        }
    }

    /**
     * Deposit insurance to a player's home nation or town treasury.
     */
    public boolean depositInsurance(Player player, double amount, String description) {
        try {
            Resident resident = townyAPI.getResident(player);
            if (resident == null || !resident.hasTown()) {
                plugin.getLogger().warning("Player " + player.getName() + " is not in a town - cannot deposit insurance");
                return false;
            }

            Town town = resident.getTown();

            // Deposit to nation treasury if enabled and available
            if (useNationTreasury && town.hasNation()) {
                Nation nation = town.getNation();
                nation.getAccount().deposit(amount, description);
                plugin.getLogger().info("Deposited $" + amount + " insurance to nation " + nation.getName());
                return true;
            }

            // Fall back to town treasury
            town.getAccount().deposit(amount, description);
            plugin.getLogger().info("Deposited $" + amount + " insurance to town " + town.getName());
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error depositing insurance: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if a location is within claimed territory.
     */
    public boolean isInClaim(Location location) {
        if (location == null) {
            return false;
        }

        TownBlock townBlock = townyAPI.getTownBlock(location);
        return townBlock != null;
    }

    /**
     * Check if a player is a member of any town.
     */
    public boolean isInTown(Player player) {
        try {
            Resident resident = townyAPI.getResident(player);
            return resident != null && resident.hasTown();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get a player's town (if any).
     */
    public Town getPlayerTown(Player player) {
        try {
            Resident resident = townyAPI.getResident(player);
            if (resident != null && resident.hasTown()) {
                return resident.getTown();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting player town: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get a player's nation (if any).
     */
    public Nation getPlayerNation(Player player) {
        try {
            Resident resident = townyAPI.getResident(player);
            if (resident != null && resident.hasTown()) {
                Town town = resident.getTown();
                if (town.hasNation()) {
                    return town.getNation();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting player nation: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get the town at a location (if any).
     */
    public Town getTownAtLocation(Location location) {
        if (location == null) {
            return null;
        }

        TownBlock townBlock = townyAPI.getTownBlock(location);
        if (townBlock == null) {
            return null;
        }

        try {
            return townBlock.getTown();
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting town at location: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if Towny is enabled.
     */
    public boolean isEnabled() {
        return townyAPI != null;
    }

    /**
     * Get formatted region name for display.
     */
    public String formatRegionName(String regionName) {
        if (regionName == null) {
            return "Wilderness";
        }
        return regionName;
    }
}
