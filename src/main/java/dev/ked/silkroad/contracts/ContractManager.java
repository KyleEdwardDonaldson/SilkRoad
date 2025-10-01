package dev.ked.silkroad.contracts;

import dev.ked.silkroad.SilkRoadPlugin;
import dev.ked.silkroad.bounty.BountyCalculator;
import dev.ked.silkroad.cargo.CargoItem;
import dev.ked.silkroad.integration.BetterShopAPI;
import dev.ked.silkroad.transport.InsuranceManager;
import dev.ked.silkroad.transport.TransporterManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * Manages the lifecycle of delivery contracts: creation, acceptance, delivery, and cancellation.
 */
public class ContractManager {

    private final SilkRoadPlugin plugin;
    private final ContractRegistry registry;
    private final BountyCalculator bountyCalculator;
    private final TransporterManager transporterManager;
    private final InsuranceManager insuranceManager;
    private final BetterShopAPI betterShopAPI;
    private final MiniMessage miniMessage;

    public ContractManager(SilkRoadPlugin plugin, ContractRegistry registry, BountyCalculator bountyCalculator,
                          TransporterManager transporterManager, InsuranceManager insuranceManager,
                          BetterShopAPI betterShopAPI) {
        this.plugin = plugin;
        this.registry = registry;
        this.bountyCalculator = bountyCalculator;
        this.transporterManager = transporterManager;
        this.insuranceManager = insuranceManager;
        this.betterShopAPI = betterShopAPI;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Create a new delivery contract from a shop purchase.
     */
    public Contract createContract(UUID buyerId, UUID shopId, Location shopLocation, String originRegion,
                                   Location deliveryLocation, String destinationRegion,
                                   ItemStack item, int quantity, double shopPrice, UUID shopOwnerId,
                                   Map<String, Double> regionDistances) {

        // Create contract
        UUID contractId = UUID.randomUUID();
        Contract contract = new Contract(contractId);

        // Set basic info
        contract.setShopId(shopId);
        contract.setShopLocation(shopLocation);
        contract.setOriginRegion(originRegion);
        contract.setDeliveryLocation(deliveryLocation);
        contract.setDestinationRegion(destinationRegion);
        contract.setItem(item.clone());
        contract.setQuantity(quantity);
        contract.setShopPrice(shopPrice);
        contract.setBuyer(buyerId);
        contract.setShopOwner(shopOwnerId);
        contract.setRegionDistances(regionDistances);

        // Calculate bounty
        double initialBounty = bountyCalculator.calculateBounty(contract);
        contract.setInitialBounty(initialBounty);

        // Calculate decay rate
        double decayRate = bountyCalculator.calculateDecayRate(contract);
        contract.setDecayRate(decayRate);

        // Calculate expiration time
        long expiresAt = System.currentTimeMillis() + (long) ((initialBounty / decayRate) * 1000);
        contract.setExpiresAt(expiresAt);

        // Determine required transporter level
        double totalValue = shopPrice * quantity;
        int requiredLevel = bountyCalculator.calculateRequiredLevel(totalValue);
        contract.setTransporterLevel(requiredLevel);

        // Register contract
        registry.registerContract(contract);

        // Reserve stock in shop (if BetterShop integration is available)
        if (betterShopAPI != null && betterShopAPI.isEnabled()) {
            betterShopAPI.reserveStock(shopId, quantity, contractId);
        }

        // Notify buyer
        Player buyer = Bukkit.getPlayer(buyerId);
        if (buyer != null) {
            String message = plugin.getConfig().getString("messages.buyer.orderPlaced", "Order placed!");
            buyer.sendMessage(miniMessage.deserialize(message));
        }

        plugin.getLogger().info("Contract created: " + contractId + " for " + quantity + "x " + item.getType() +
                " from " + originRegion + " to " + destinationRegion + " (bounty: $" + initialBounty + ")");

        return contract;
    }

    /**
     * Accept a contract as a transporter.
     */
    public boolean acceptContract(Player transporter, UUID contractId) {
        Contract contract = registry.getContract(contractId);
        if (contract == null) {
            return false;
        }

        // Validate contract can be accepted
        if (!contract.canBeAccepted()) {
            transporter.sendMessage(miniMessage.deserialize("<red>This contract is no longer available!</red>"));
            return false;
        }

        // Check transporter level requirement
        int transporterLevel = transporterManager.getLevel(transporter.getUniqueId());
        if (transporterLevel < contract.getTransporterLevel()) {
            String message = plugin.getConfig().getString("messages.contract.levelTooLow",
                    "This contract requires level {level}!")
                    .replace("{level}", String.valueOf(contract.getTransporterLevel()));
            transporter.sendMessage(miniMessage.deserialize(message));
            return false;
        }

        // Check active contract limit
        int activeContracts = registry.getActiveContractsForTransporter(transporter.getUniqueId()).size();
        int maxContracts = transporterManager.getMaxContracts(transporter.getUniqueId());
        if (activeContracts >= maxContracts) {
            String message = plugin.getConfig().getString("messages.contract.maxContractsReached",
                    "Too many active contracts ({current}/{max})!")
                    .replace("{current}", String.valueOf(activeContracts))
                    .replace("{max}", String.valueOf(maxContracts));
            transporter.sendMessage(miniMessage.deserialize(message));
            return false;
        }

        // Charge insurance
        double insuranceCost = insuranceManager.calculateInsurance(contract, transporter.getUniqueId());
        if (!insuranceManager.chargeInsurance(transporter, contract, insuranceCost)) {
            String message = plugin.getConfig().getString("messages.contract.insufficientInsurance",
                    "Insufficient funds for insurance: ${insurance}")
                    .replace("{insurance}", String.format("%.2f", insuranceCost));
            transporter.sendMessage(miniMessage.deserialize(message));
            return false;
        }

        // Update contract state
        ContractState oldState = contract.getState();
        contract.setState(ContractState.ACCEPTED);
        contract.setTransporter(transporter.getUniqueId());
        contract.setAcceptedAt(System.currentTimeMillis());
        registry.updateContractIndexes(contract, oldState, null);

        // Give cargo item to transporter
        ItemStack cargoItem = CargoItem.createCargoItem(plugin, contract);
        transporter.getInventory().addItem(cargoItem);

        // Notify transporter
        String message = plugin.getConfig().getString("messages.contract.accepted",
                "Contract accepted! Deliver to {destination}")
                .replace("{destination}", contract.getDestinationRegion());
        transporter.sendMessage(miniMessage.deserialize(message));

        plugin.getLogger().info("Contract " + contractId + " accepted by " + transporter.getName());

        return true;
    }

    /**
     * Pick up cargo from shop (marks contract as IN_TRANSIT).
     */
    public boolean pickupCargo(Player transporter, UUID contractId) {
        Contract contract = registry.getContract(contractId);
        if (contract == null) {
            return false;
        }

        // Validate
        if (!contract.getTransporter().equals(transporter.getUniqueId())) {
            transporter.sendMessage(miniMessage.deserialize("<red>This is not your contract!</red>"));
            return false;
        }

        if (!contract.canBePickedUp()) {
            transporter.sendMessage(miniMessage.deserialize("<red>Cargo already picked up or contract expired!</red>"));
            return false;
        }

        // Mark as picked up and in transit
        ContractState oldState = contract.getState();
        contract.setPickedUp(true);
        contract.setState(ContractState.IN_TRANSIT);
        registry.updateContractIndexes(contract, oldState, contract.getTransporter());

        // Notify transporter
        String message = plugin.getConfig().getString("messages.cargo.pickedUp",
                "Cargo picked up! Deliver to {destination}")
                .replace("{destination}", contract.getDestinationRegion());
        transporter.sendMessage(miniMessage.deserialize(message));

        plugin.getLogger().info("Contract " + contractId + " cargo picked up by " + transporter.getName());

        return true;
    }

    /**
     * Deliver cargo and complete contract.
     */
    public boolean deliverContract(Player transporter, UUID contractId) {
        Contract contract = registry.getContract(contractId);
        if (contract == null) {
            return false;
        }

        // Validate
        if (!contract.getTransporter().equals(transporter.getUniqueId())) {
            transporter.sendMessage(miniMessage.deserialize("<red>This is not your contract!</red>"));
            return false;
        }

        if (!contract.canBeDelivered()) {
            transporter.sendMessage(miniMessage.deserialize("<red>Cannot deliver this contract!</red>"));
            return false;
        }

        // Update contract state
        ContractState oldState = contract.getState();
        contract.setState(ContractState.DELIVERED);
        contract.setDeliveredAt(System.currentTimeMillis());
        registry.updateContractIndexes(contract, oldState, contract.getTransporter());

        // Pay bounty to transporter
        double bounty = contract.getCurrentBounty();
        plugin.getEconomy().deposit(transporter.getUniqueId(), bounty);

        // Award XP
        transporterManager.awardCompletionXP(transporter.getUniqueId(), contract);

        // Complete shop transaction (if BetterShop integration available)
        if (betterShopAPI != null && betterShopAPI.isEnabled()) {
            betterShopAPI.completeTransaction(contract.getShopId(), contract.getBuyer(),
                    contract.getShopPrice() * contract.getQuantity());
        }

        // Notify transporter
        String message = plugin.getConfig().getString("messages.contract.delivered",
                "Contract completed! You earned ${bounty}")
                .replace("{bounty}", String.format("%.2f", bounty));
        transporter.sendMessage(miniMessage.deserialize(message));

        // Notify buyer
        Player buyer = Bukkit.getPlayer(contract.getBuyer());
        if (buyer != null) {
            String buyerMessage = plugin.getConfig().getString("messages.buyer.orderDelivered",
                    "Your order has arrived! Visit a Trade Post to pick up.");
            buyer.sendMessage(miniMessage.deserialize(buyerMessage));
        }

        plugin.getLogger().info("Contract " + contractId + " delivered by " + transporter.getName() +
                " (bounty: $" + bounty + ")");

        return true;
    }

    /**
     * Cancel a contract (refunds buyer, releases stock, removes cargo item).
     */
    public void cancelContract(UUID contractId, String reason) {
        Contract contract = registry.getContract(contractId);
        if (contract == null) {
            return;
        }

        ContractState oldState = contract.getState();
        contract.setState(ContractState.CANCELLED);
        registry.updateContractIndexes(contract, oldState, contract.getTransporter());

        // Release stock reservation
        if (betterShopAPI != null && betterShopAPI.isEnabled()) {
            betterShopAPI.releaseReservation(contract.getShopId(), contractId);
        }

        // Refund buyer (if payment was escrowed)
        if (contract.getBuyer() != null) {
            double refundAmount = contract.getShopPrice() * contract.getQuantity();
            plugin.getEconomy().deposit(contract.getBuyer(), refundAmount);

            Player buyer = Bukkit.getPlayer(contract.getBuyer());
            if (buyer != null) {
                String message = plugin.getConfig().getString("messages.buyer.orderRefunded",
                        "Order cancelled. Refunded ${amount}")
                        .replace("{amount}", String.format("%.2f", refundAmount));
                buyer.sendMessage(miniMessage.deserialize(message));
            }
        }

        // Remove cargo item from transporter
        if (contract.getTransporter() != null) {
            Player transporter = Bukkit.getPlayer(contract.getTransporter());
            if (transporter != null) {
                CargoItem.removeCargo(transporter, contractId);
                String message = plugin.getConfig().getString("messages.contract.cancelled",
                        "Contract cancelled: " + reason);
                transporter.sendMessage(miniMessage.deserialize(message));
            }
        }

        plugin.getLogger().info("Contract " + contractId + " cancelled: " + reason);

        // Unregister after a delay (keep in history)
        Bukkit.getScheduler().runTaskLater(plugin, () -> registry.unregisterContract(contractId), 100L);
    }

    /**
     * Expire a contract when bounty reaches $0.
     */
    public void expireContract(UUID contractId) {
        Contract contract = registry.getContract(contractId);
        if (contract == null) {
            return;
        }

        ContractState oldState = contract.getState();
        contract.setState(ContractState.EXPIRED);
        registry.updateContractIndexes(contract, oldState, contract.getTransporter());

        // Release stock
        if (betterShopAPI != null && betterShopAPI.isEnabled()) {
            betterShopAPI.releaseReservation(contract.getShopId(), contractId);
        }

        // Refund buyer
        if (contract.getBuyer() != null) {
            double refundAmount = contract.getShopPrice() * contract.getQuantity();
            plugin.getEconomy().deposit(contract.getBuyer(), refundAmount);

            Player buyer = Bukkit.getPlayer(contract.getBuyer());
            if (buyer != null) {
                String message = plugin.getConfig().getString("messages.buyer.orderRefunded",
                        "Order expired. Refunded ${amount}")
                        .replace("{amount}", String.format("%.2f", refundAmount));
                buyer.sendMessage(miniMessage.deserialize(message));
            }
        }

        // Notify transporter (insurance not refunded)
        if (contract.getTransporter() != null) {
            Player transporter = Bukkit.getPlayer(contract.getTransporter());
            if (transporter != null) {
                CargoItem.removeCargo(transporter, contractId);
                String message = plugin.getConfig().getString("messages.contract.expired",
                        "Contract expired - bounty reached $0");
                transporter.sendMessage(miniMessage.deserialize(message));
            }
        }

        plugin.getLogger().info("Contract " + contractId + " expired (bounty reached $0)");

        // Unregister after a delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> registry.unregisterContract(contractId), 100L);
    }

    /**
     * Complete a contract when buyer picks up items.
     */
    public void completeContract(UUID contractId) {
        Contract contract = registry.getContract(contractId);
        if (contract == null) {
            return;
        }

        ContractState oldState = contract.getState();
        contract.setState(ContractState.COMPLETED);
        registry.updateContractIndexes(contract, oldState, contract.getTransporter());

        plugin.getLogger().info("Contract " + contractId + " completed");

        // Unregister after a delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> registry.unregisterContract(contractId), 100L);
    }

    /**
     * Get delivered contracts waiting for pickup by a player at a trade post.
     */
    public java.util.List<Contract> getPendingOrdersForPlayer(UUID playerId, dev.ked.silkroad.tradeposts.TradePost tradePost) {
        return registry.getDeliveredContractsForBuyer(playerId).stream()
                .filter(c -> c.getDestinationRegion().equals(tradePost.getRegionName()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get all active contracts for a transporter.
     */
    public java.util.List<Contract> getActiveContractsForTransporter(UUID transporterId) {
        return registry.getActiveContractsForTransporter(transporterId);
    }
}
