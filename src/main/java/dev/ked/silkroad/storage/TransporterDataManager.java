package dev.ked.silkroad.storage;

import com.google.gson.*;
import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.transport.TransporterManager.TransporterData;

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

            plugin.getLogger().info("Saved " + cache.size() + " transporter profiles to disk");

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
        json.addProperty("totalDistance", data.getTotalDistance());
        json.addProperty("totalEarnings", data.getTotalEarnings());
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
        return data;
    }

    /**
     * Clear the cache (for testing).
     */
    public void clearCache() {
        cache.clear();
    }
}
