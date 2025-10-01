package dev.ked.silkroad.storage;

import com.google.gson.*;
import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.contracts.Contract;
import dev.ked.silkroad.contracts.ContractRegistry;
import dev.ked.silkroad.contracts.ContractState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * JSON persistence for contracts.
 * Saves and loads contracts to/from disk.
 */
public class ContractDataManager {

    private final SilkRoadPlugin plugin;
    private final File dataFile;
    private final Gson gson;

    public ContractDataManager(SilkRoadPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "contracts.json");

        // Create Gson with custom serializers
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Location.class, new LocationSerializer())
                .registerTypeAdapter(ItemStack.class, new ItemStackSerializer())
                .create();
    }

    /**
     * Save all contracts to disk.
     */
    public void saveContracts(ContractRegistry registry) {
        try {
            // Ensure data folder exists
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // Convert contracts to JSON
            Collection<Contract> contracts = registry.getAllContracts();
            JsonArray jsonArray = new JsonArray();

            for (Contract contract : contracts) {
                jsonArray.add(serializeContract(contract));
            }

            // Write to file
            try (Writer writer = new FileWriter(dataFile)) {
                gson.toJson(jsonArray, writer);
            }

            if (contracts.size() > 0) {
                plugin.getLogger().info("Saved " + contracts.size() + " contracts to disk");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save contracts: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load all contracts from disk.
     */
    public void loadContracts(ContractRegistry registry) {
        if (!dataFile.exists()) {
            plugin.getLogger().info("No contracts file found, starting fresh");
            return;
        }

        try {
            // Read file
            String json = new String(Files.readAllBytes(dataFile.toPath()));
            JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();

            int loaded = 0;
            for (JsonElement element : jsonArray) {
                try {
                    Contract contract = deserializeContract(element.getAsJsonObject());
                    if (contract != null) {
                        registry.registerContract(contract);
                        loaded++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load contract: " + e.getMessage());
                }
            }

            plugin.getLogger().info("Loaded " + loaded + " contracts from disk");

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load contracts: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Serialize a contract to JSON.
     */
    private JsonObject serializeContract(Contract contract) {
        JsonObject json = new JsonObject();

        // Identity
        json.addProperty("contractId", contract.getContractId().toString());
        json.addProperty("createdAt", contract.getCreatedAt());
        json.addProperty("state", contract.getState().name());

        // Shop integration
        if (contract.getShopId() != null) {
            json.addProperty("shopId", contract.getShopId().toString());
        }
        json.add("shopLocation", gson.toJsonTree(contract.getShopLocation()));
        json.addProperty("originRegion", contract.getOriginRegion());

        // Delivery
        json.add("deliveryLocation", gson.toJsonTree(contract.getDeliveryLocation()));
        json.addProperty("destinationRegion", contract.getDestinationRegion());

        // Items & pricing
        json.add("item", gson.toJsonTree(contract.getItem()));
        json.addProperty("quantity", contract.getQuantity());
        json.addProperty("shopPrice", contract.getShopPrice());

        // Bounty system
        json.addProperty("initialBounty", contract.getInitialBounty());
        json.addProperty("currentBounty", contract.getCurrentBounty());
        json.addProperty("decayRate", contract.getDecayRate());
        json.addProperty("expiresAt", contract.getExpiresAt());

        // Parties
        if (contract.getShopOwner() != null) {
            json.addProperty("shopOwner", contract.getShopOwner().toString());
        }
        if (contract.getBuyer() != null) {
            json.addProperty("buyer", contract.getBuyer().toString());
        }
        if (contract.getTransporter() != null) {
            json.addProperty("transporter", contract.getTransporter().toString());
        }

        // Journey tracking
        JsonObject regionDistances = new JsonObject();
        for (Map.Entry<String, Double> entry : contract.getRegionDistances().entrySet()) {
            regionDistances.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("regionDistances", regionDistances);
        json.addProperty("transporterLevel", contract.getTransporterLevel());

        // State tracking
        json.addProperty("pickedUp", contract.isPickedUp());
        json.addProperty("acceptedAt", contract.getAcceptedAt());
        json.addProperty("deliveredAt", contract.getDeliveredAt());

        return json;
    }

    /**
     * Deserialize a contract from JSON.
     */
    private Contract deserializeContract(JsonObject json) {
        try {
            UUID contractId = UUID.fromString(json.get("contractId").getAsString());
            Contract contract = new Contract(contractId);

            // State
            ContractState state = ContractState.valueOf(json.get("state").getAsString());
            contract.setState(state);

            // Shop integration
            if (json.has("shopId")) {
                contract.setShopId(UUID.fromString(json.get("shopId").getAsString()));
            }
            contract.setShopLocation(gson.fromJson(json.get("shopLocation"), Location.class));
            contract.setOriginRegion(json.get("originRegion").getAsString());

            // Delivery
            contract.setDeliveryLocation(gson.fromJson(json.get("deliveryLocation"), Location.class));
            contract.setDestinationRegion(json.get("destinationRegion").getAsString());

            // Items & pricing
            contract.setItem(gson.fromJson(json.get("item"), ItemStack.class));
            contract.setQuantity(json.get("quantity").getAsInt());
            contract.setShopPrice(json.get("shopPrice").getAsDouble());

            // Bounty system
            contract.setInitialBounty(json.get("initialBounty").getAsDouble());
            contract.setCurrentBounty(json.get("currentBounty").getAsDouble());
            contract.setDecayRate(json.get("decayRate").getAsDouble());
            contract.setExpiresAt(json.get("expiresAt").getAsLong());

            // Parties
            if (json.has("shopOwner")) {
                contract.setShopOwner(UUID.fromString(json.get("shopOwner").getAsString()));
            }
            if (json.has("buyer")) {
                contract.setBuyer(UUID.fromString(json.get("buyer").getAsString()));
            }
            if (json.has("transporter")) {
                contract.setTransporter(UUID.fromString(json.get("transporter").getAsString()));
            }

            // Journey tracking
            Map<String, Double> regionDistances = new HashMap<>();
            JsonObject distancesJson = json.getAsJsonObject("regionDistances");
            for (Map.Entry<String, JsonElement> entry : distancesJson.entrySet()) {
                regionDistances.put(entry.getKey(), entry.getValue().getAsDouble());
            }
            contract.setRegionDistances(regionDistances);
            contract.setTransporterLevel(json.get("transporterLevel").getAsInt());

            // State tracking
            contract.setPickedUp(json.get("pickedUp").getAsBoolean());
            if (json.has("acceptedAt")) {
                contract.setAcceptedAt(json.get("acceptedAt").getAsLong());
            }
            if (json.has("deliveredAt")) {
                contract.setDeliveredAt(json.get("deliveredAt").getAsLong());
            }

            return contract;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize contract: " + e.getMessage());
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

    /**
     * ItemStack serializer for Gson (uses base64 encoding).
     */
    private static class ItemStackSerializer implements JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {
        @Override
        public JsonElement serialize(ItemStack item, java.lang.reflect.Type type, JsonSerializationContext context) {
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                org.bukkit.util.io.BukkitObjectOutputStream dataOutput = new org.bukkit.util.io.BukkitObjectOutputStream(outputStream);
                dataOutput.writeObject(item);
                dataOutput.close();
                return new JsonPrimitive(Base64.getEncoder().encodeToString(outputStream.toByteArray()));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize ItemStack", e);
            }
        }

        @Override
        public ItemStack deserialize(JsonElement element, java.lang.reflect.Type type, JsonDeserializationContext context) {
            try {
                ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(element.getAsString()));
                org.bukkit.util.io.BukkitObjectInputStream dataInput = new org.bukkit.util.io.BukkitObjectInputStream(inputStream);
                ItemStack item = (ItemStack) dataInput.readObject();
                dataInput.close();
                return item;
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize ItemStack", e);
            }
        }
    }
}
