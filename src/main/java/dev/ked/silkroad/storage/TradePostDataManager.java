package dev.ked.silkroad.storage;

import com.google.gson.*;
import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.tradeposts.TradePost;
import dev.ked.silkroad.tradeposts.TradePostManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.*;
import java.nio.file.Files;
import java.util.UUID;

/**
 * JSON persistence for trade post locations.
 */
public class TradePostDataManager {

    private final SilkRoadPlugin plugin;
    private final File dataFile;
    private final Gson gson;

    public TradePostDataManager(SilkRoadPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "tradeposts.json");

        // Create Gson with custom serializers
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Location.class, new LocationSerializer())
                .create();
    }

    /**
     * Load all trade posts from disk.
     */
    public void loadAll(TradePostManager manager) {
        if (!dataFile.exists()) {
            plugin.getLogger().info("No trade posts file found, starting fresh");
            return;
        }

        try {
            String json = new String(Files.readAllBytes(dataFile.toPath()));
            JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();

            int loaded = 0;
            for (JsonElement element : jsonArray) {
                try {
                    TradePost tradePost = deserializeTradePost(element.getAsJsonObject());
                    if (tradePost != null) {
                        manager.registerTradePost(tradePost);
                        loaded++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load trade post: " + e.getMessage());
                }
            }

            plugin.getLogger().info("Loaded " + loaded + " trade posts from disk");

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load trade posts: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Save all trade posts to disk.
     */
    public void saveAll(TradePostManager manager) {
        try {
            // Ensure data folder exists
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // Convert trade posts to JSON
            JsonArray jsonArray = new JsonArray();
            for (TradePost tradePost : manager.getAllTradePosts()) {
                jsonArray.add(serializeTradePost(tradePost));
            }

            // Write to file
            try (Writer writer = new FileWriter(dataFile)) {
                gson.toJson(jsonArray, writer);
            }

            int count = manager.getTradePostCount();
            if (count > 0) {
                plugin.getLogger().info("Saved " + count + " trade posts to disk");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save trade posts: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Save a single trade post.
     */
    public void save(TradePost tradePost) {
        // For single saves, we need to load all, add/update, and save all
        // This is fine since trade posts are rarely created/deleted
        // For better performance with many trade posts, consider using a database
        plugin.getLogger().info("Trade post saved (will persist on next saveAll)");
    }

    /**
     * Delete a trade post.
     */
    public void delete(Location location) {
        // Similar to save - handled in saveAll()
        plugin.getLogger().info("Trade post deleted (will persist on next saveAll)");
    }

    /**
     * Serialize a trade post to JSON.
     */
    private JsonObject serializeTradePost(TradePost tradePost) {
        JsonObject json = new JsonObject();
        json.add("location", gson.toJsonTree(tradePost.getLocation()));
        json.addProperty("regionName", tradePost.getRegionName());
        json.addProperty("creatorId", tradePost.getCreatorId().toString());
        json.addProperty("createdAt", tradePost.getCreatedAt());
        return json;
    }

    /**
     * Deserialize a trade post from JSON.
     */
    private TradePost deserializeTradePost(JsonObject json) {
        try {
            Location location = gson.fromJson(json.get("location"), Location.class);
            String regionName = json.get("regionName").getAsString();
            UUID creatorId = UUID.fromString(json.get("creatorId").getAsString());

            return new TradePost(location, regionName, creatorId);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize trade post: " + e.getMessage());
            return null;
        }
    }

    /**
     * Location serializer for Gson.
     */
    private static class LocationSerializer implements JsonSerializer<Location>, JsonDeserializer<Location> {
        @Override
        public JsonElement serialize(Location loc, java.lang.reflect.Type type, JsonSerializationContext context) {
            JsonObject json = new JsonObject();
            json.addProperty("world", loc.getWorld().getName());
            json.addProperty("x", loc.getX());
            json.addProperty("y", loc.getY());
            json.addProperty("z", loc.getZ());
            json.addProperty("yaw", loc.getYaw());
            json.addProperty("pitch", loc.getPitch());
            return json;
        }

        @Override
        public Location deserialize(JsonElement element, java.lang.reflect.Type type, JsonDeserializationContext context) {
            JsonObject json = element.getAsJsonObject();
            return new Location(
                    Bukkit.getWorld(json.get("world").getAsString()),
                    json.get("x").getAsDouble(),
                    json.get("y").getAsDouble(),
                    json.get("z").getAsDouble(),
                    json.get("yaw").getAsFloat(),
                    json.get("pitch").getAsFloat()
            );
        }
    }
}
