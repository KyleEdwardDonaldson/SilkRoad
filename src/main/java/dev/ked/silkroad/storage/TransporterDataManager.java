package dev.ked.silkroad.storage;

import com.google.gson.*;
import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.transport.CompletedContractRecord;
import dev.ked.silkroad.transport.TransporterManager.TransporterData;
import org.bukkit.Material;

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JSON persistence for transporter XP and levels.
 */
public class TransporterDataManager {

    private final SilkRoadPlugin plugin;
    private final File dataFile;
    private final Gson gson;

    // In-memory cache
    private final Map<UUID, TransporterData> cache;

    public TransporterDataManager(SilkRoadPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "transporters.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.cache = new HashMap<>();
    }

    /**
     * Load a transporter's data from disk.
     */
    public TransporterData load(UUID playerId) {
        // Check cache first
        if (cache.containsKey(playerId)) {
            return cache.get(playerId);
        }

        // Load from disk
        loadAll();
        return cache.get(playerId);
    }

    /**
     * Save a transporter's data to cache (will be written to disk on saveAll()).
     */
    public void save(TransporterData data) {
        cache.put(data.getPlayerId(), data);
    }

    /**
     * Load all transporter data from disk.
     */
    public void loadAll() {
        if (!dataFile.exists()) {
            plugin.getLogger().info("No transporter data file found, starting fresh");
            return;
        }

        try {
            String json = new String(Files.readAllBytes(dataFile.toPath()));
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            int loaded = 0;
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                try {
                    UUID playerId = UUID.fromString(entry.getKey());
                    TransporterData data = deserializeTransporterData(playerId, entry.getValue().getAsJsonObject());
                    cache.put(playerId, data);
                    loaded++;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load transporter data: " + e.getMessage());
                }
            }

            plugin.getLogger().info("Loaded " + loaded + " transporter profiles from disk");

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load transporter data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Save all transporter data to disk.
     */
    public void saveAll() {
        try {
            // Ensure data folder exists
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // Convert cache to JSON
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, TransporterData> entry : cache.entrySet()) {
                root.add(entry.getKey().toString(), serializeTransporterData(entry.getValue()));
            }

            // Write to file
            try (Writer writer = new FileWriter(dataFile)) {
                gson.toJson(root, writer);
            }

            if (cache.size() > 0) {
                plugin.getLogger().info("Saved " + cache.size() + " transporter profiles to disk");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save transporter data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Serialize transporter data to JSON.
     */
    private JsonObject serializeTransporterData(TransporterData data) {
        JsonObject json = new JsonObject();
        json.addProperty("level", data.getLevel());
        json.addProperty("xp", data.getXp());
        json.addProperty("completedContracts", data.getCompletedContracts());
        json.addProperty("failedContracts", data.getFailedContracts());
        json.addProperty("cancelledContracts", data.getCancelledContracts());
        json.addProperty("totalDistance", data.getTotalDistance());
        json.addProperty("totalEarnings", data.getTotalEarnings());
        json.addProperty("totalTravelTime", data.getTotalTravelTime());

        // Serialize region deliveries map
        JsonObject regionDeliveries = new JsonObject();
        for (Map.Entry<String, Integer> entry : data.getRegionDeliveries().entrySet()) {
            regionDeliveries.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("regionDeliveries", regionDeliveries);

        // Serialize regions visited map
        JsonObject regionsVisited = new JsonObject();
        for (Map.Entry<String, Integer> entry : data.getRegionsVisited().entrySet()) {
            regionsVisited.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("regionsVisited", regionsVisited);

        // Serialize recent history
        JsonArray history = new JsonArray();
        for (CompletedContractRecord record : data.getRecentHistory()) {
            JsonObject recordObj = new JsonObject();
            recordObj.addProperty("contractId", record.getContractId().toString());
            recordObj.addProperty("completedAt", record.getCompletedAt());
            recordObj.addProperty("originRegion", record.getOriginRegion());
            recordObj.addProperty("destinationRegion", record.getDestinationRegion());
            recordObj.addProperty("itemType", record.getItemType().name());
            recordObj.addProperty("quantity", record.getQuantity());
            recordObj.addProperty("bountyEarned", record.getBountyEarned());
            recordObj.addProperty("distance", record.getDistance());
            recordObj.addProperty("travelTime", record.getTravelTime());
            history.add(recordObj);
        }
        json.add("recentHistory", history);

        return json;
    }

    /**
     * Deserialize transporter data from JSON.
     */
    private TransporterData deserializeTransporterData(UUID playerId, JsonObject json) {
        TransporterData data = new TransporterData(playerId);
        data.setLevel(json.get("level").getAsInt());
        data.setXp(json.get("xp").getAsInt());
        data.setCompletedContracts(json.get("completedContracts").getAsInt());
        data.setTotalDistance(json.get("totalDistance").getAsDouble());
        data.setTotalEarnings(json.get("totalEarnings").getAsDouble());

        // Load new fields (with defaults for backward compatibility)
        if (json.has("failedContracts")) {
            data.setFailedContracts(json.get("failedContracts").getAsInt());
        }
        if (json.has("cancelledContracts")) {
            data.setCancelledContracts(json.get("cancelledContracts").getAsInt());
        }
        if (json.has("totalTravelTime")) {
            data.setTotalTravelTime(json.get("totalTravelTime").getAsLong());
        }

        // Load region deliveries map
        if (json.has("regionDeliveries")) {
            JsonObject regionDeliveries = json.getAsJsonObject("regionDeliveries");
            for (Map.Entry<String, JsonElement> entry : regionDeliveries.entrySet()) {
                for (int i = 0; i < entry.getValue().getAsInt(); i++) {
                    data.recordDelivery(entry.getKey());
                }
            }
        }

        // Load regions visited map
        if (json.has("regionsVisited")) {
            JsonObject regionsVisited = json.getAsJsonObject("regionsVisited");
            for (Map.Entry<String, JsonElement> entry : regionsVisited.entrySet()) {
                for (int i = 0; i < entry.getValue().getAsInt(); i++) {
                    data.recordRegionVisit(entry.getKey());
                }
            }
        }

        // Load recent history
        if (json.has("recentHistory")) {
            JsonArray history = json.getAsJsonArray("recentHistory");
            for (JsonElement element : history) {
                JsonObject recordObj = element.getAsJsonObject();
                CompletedContractRecord record = new CompletedContractRecord(
                        UUID.fromString(recordObj.get("contractId").getAsString()),
                        recordObj.get("completedAt").getAsLong(),
                        recordObj.get("originRegion").getAsString(),
                        recordObj.get("destinationRegion").getAsString(),
                        Material.valueOf(recordObj.get("itemType").getAsString()),
                        recordObj.get("quantity").getAsInt(),
                        recordObj.get("bountyEarned").getAsDouble(),
                        recordObj.get("distance").getAsDouble(),
                        recordObj.get("travelTime").getAsLong()
                );
                data.addToHistory(record);
            }
        }

        return data;
    }

    /**
     * Clear the cache (for testing).
     */
    public void clearCache() {
        cache.clear();
    }
}
