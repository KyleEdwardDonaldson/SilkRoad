package dev.ked.silkroad.contracts;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Contract {

    // Identity
    private UUID contractId;
    private long createdAt;
    private ContractState state;

    // Shop Integration
    private UUID shopId;
    private Location shopLocation;
    private String originRegion;

    // Delivery
    private Location deliveryLocation;
    private String destinationRegion;

    // Items & Pricing
    private ItemStack item;
    private int quantity;
    private double shopPrice;

    // Bounty System
    private double initialBounty;
    private double currentBounty;
    private double decayRate;
    private long expiresAt;

    // Parties
    private UUID shopOwner;
    private UUID buyer;
    private UUID transporter;

    // Journey Tracking
    private Map<String, Double> regionDistances;
    private int transporterLevel;

    // State tracking
    private boolean pickedUp;
    private long acceptedAt;
    private long deliveredAt;

    public Contract(UUID contractId) {
        this.contractId = contractId;
        this.createdAt = System.currentTimeMillis();
        this.state = ContractState.POSTED;
        this.regionDistances = new HashMap<>();
        this.pickedUp = false;
    }

    // Getters and Setters
    public UUID getContractId() {
        return contractId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public ContractState getState() {
        return state;
    }

    public void setState(ContractState state) {
        this.state = state;
    }

    public UUID getShopId() {
        return shopId;
    }

    public void setShopId(UUID shopId) {
        this.shopId = shopId;
    }

    public Location getShopLocation() {
        return shopLocation;
    }

    public void setShopLocation(Location shopLocation) {
        this.shopLocation = shopLocation;
    }

    public String getOriginRegion() {
        return originRegion;
    }

    public void setOriginRegion(String originRegion) {
        this.originRegion = originRegion;
    }

    public Location getDeliveryLocation() {
        return deliveryLocation;
    }

    public void setDeliveryLocation(Location deliveryLocation) {
        this.deliveryLocation = deliveryLocation;
    }

    public String getDestinationRegion() {
        return destinationRegion;
    }

    public void setDestinationRegion(String destinationRegion) {
        this.destinationRegion = destinationRegion;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getShopPrice() {
        return shopPrice;
    }

    public void setShopPrice(double shopPrice) {
        this.shopPrice = shopPrice;
    }

    public double getInitialBounty() {
        return initialBounty;
    }

    public void setInitialBounty(double initialBounty) {
        this.initialBounty = initialBounty;
        this.currentBounty = initialBounty;
    }

    public double getCurrentBounty() {
        return currentBounty;
    }

    public void setCurrentBounty(double currentBounty) {
        this.currentBounty = Math.max(0, currentBounty);
    }

    public double getDecayRate() {
        return decayRate;
    }

    public void setDecayRate(double decayRate) {
        this.decayRate = decayRate;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public UUID getShopOwner() {
        return shopOwner;
    }

    public void setShopOwner(UUID shopOwner) {
        this.shopOwner = shopOwner;
    }

    public UUID getBuyer() {
        return buyer;
    }

    public void setBuyer(UUID buyer) {
        this.buyer = buyer;
    }

    public UUID getTransporter() {
        return transporter;
    }

    public void setTransporter(UUID transporter) {
        this.transporter = transporter;
    }

    public Map<String, Double> getRegionDistances() {
        return regionDistances;
    }

    public void setRegionDistances(Map<String, Double> regionDistances) {
        this.regionDistances = regionDistances;
    }

    public int getTransporterLevel() {
        return transporterLevel;
    }

    public void setTransporterLevel(int transporterLevel) {
        this.transporterLevel = transporterLevel;
    }

    public boolean isPickedUp() {
        return pickedUp;
    }

    public void setPickedUp(boolean pickedUp) {
        this.pickedUp = pickedUp;
    }

    public long getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(long acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public long getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(long deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    // Utility methods
    public double getTotalDistance() {
        return regionDistances.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public long getTimeRemaining() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public double getTotalValue() {
        return shopPrice * quantity;
    }

    public boolean isExpired() {
        return currentBounty <= 0 || System.currentTimeMillis() >= expiresAt;
    }

    public boolean canBeAccepted() {
        return state == ContractState.POSTED && !isExpired();
    }

    public boolean canBePickedUp() {
        return state == ContractState.ACCEPTED && !pickedUp && !isExpired();
    }

    public boolean canBeDelivered() {
        return (state == ContractState.ACCEPTED || state == ContractState.IN_TRANSIT)
                && pickedUp && !isExpired();
    }
}
