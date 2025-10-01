package dev.ked.silkroad.contracts;

import dev.ked.silkroad.SilkRoadPlugin;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for tracking and looking up active contracts.
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class ContractRegistry {

    private final SilkRoadPlugin plugin;

    // Primary storage: all active contracts by ID
    private final Map<UUID, Contract> contractsById;

    // Index by player UUID for quick lookups
    private final Map<UUID, Set<UUID>> contractsByBuyer;
    private final Map<UUID, Set<UUID>> contractsByTransporter;
    private final Map<UUID, Set<UUID>> contractsByShopOwner;

    // Index by region for filtering
    private final Map<String, Set<UUID>> contractsByOrigin;
    private final Map<String, Set<UUID>> contractsByDestination;

    // Index by state
    private final Map<ContractState, Set<UUID>> contractsByState;

    public ContractRegistry(SilkRoadPlugin plugin) {
        this.plugin = plugin;
        this.contractsById = new ConcurrentHashMap<>();
        this.contractsByBuyer = new ConcurrentHashMap<>();
        this.contractsByTransporter = new ConcurrentHashMap<>();
        this.contractsByShopOwner = new ConcurrentHashMap<>();
        this.contractsByOrigin = new ConcurrentHashMap<>();
        this.contractsByDestination = new ConcurrentHashMap<>();
        this.contractsByState = new ConcurrentHashMap<>();
    }

    /**
     * Register a new contract in the registry.
     */
    public void registerContract(Contract contract) {
        UUID contractId = contract.getContractId();

        // Add to primary storage
        contractsById.put(contractId, contract);

        // Index by buyer
        if (contract.getBuyer() != null) {
            contractsByBuyer.computeIfAbsent(contract.getBuyer(), k -> ConcurrentHashMap.newKeySet()).add(contractId);
        }

        // Index by transporter (may be null initially)
        if (contract.getTransporter() != null) {
            contractsByTransporter.computeIfAbsent(contract.getTransporter(), k -> ConcurrentHashMap.newKeySet()).add(contractId);
        }

        // Index by shop owner
        if (contract.getShopOwner() != null) {
            contractsByShopOwner.computeIfAbsent(contract.getShopOwner(), k -> ConcurrentHashMap.newKeySet()).add(contractId);
        }

        // Index by region
        if (contract.getOriginRegion() != null) {
            contractsByOrigin.computeIfAbsent(contract.getOriginRegion(), k -> ConcurrentHashMap.newKeySet()).add(contractId);
        }
        if (contract.getDestinationRegion() != null) {
            contractsByDestination.computeIfAbsent(contract.getDestinationRegion(), k -> ConcurrentHashMap.newKeySet()).add(contractId);
        }

        // Index by state
        contractsByState.computeIfAbsent(contract.getState(), k -> ConcurrentHashMap.newKeySet()).add(contractId);
    }

    /**
     * Unregister a contract from the registry.
     */
    public void unregisterContract(UUID contractId) {
        Contract contract = contractsById.remove(contractId);
        if (contract == null) {
            return;
        }

        // Remove from all indexes
        removeFromIndex(contractsByBuyer, contract.getBuyer(), contractId);
        removeFromIndex(contractsByTransporter, contract.getTransporter(), contractId);
        removeFromIndex(contractsByShopOwner, contract.getShopOwner(), contractId);
        removeFromIndex(contractsByOrigin, contract.getOriginRegion(), contractId);
        removeFromIndex(contractsByDestination, contract.getDestinationRegion(), contractId);
        removeFromIndex(contractsByState, contract.getState(), contractId);
    }

    /**
     * Update contract indexes when state or transporter changes.
     */
    public void updateContractIndexes(Contract contract, ContractState oldState, UUID oldTransporter) {
        UUID contractId = contract.getContractId();

        // Update state index
        if (oldState != null && oldState != contract.getState()) {
            removeFromIndex(contractsByState, oldState, contractId);
            contractsByState.computeIfAbsent(contract.getState(), k -> ConcurrentHashMap.newKeySet()).add(contractId);
        }

        // Update transporter index
        if (oldTransporter != contract.getTransporter()) {
            if (oldTransporter != null) {
                removeFromIndex(contractsByTransporter, oldTransporter, contractId);
            }
            if (contract.getTransporter() != null) {
                contractsByTransporter.computeIfAbsent(contract.getTransporter(), k -> ConcurrentHashMap.newKeySet()).add(contractId);
            }
        }
    }

    // Lookup methods

    public Contract getContract(UUID contractId) {
        return contractsById.get(contractId);
    }

    public Collection<Contract> getAllContracts() {
        return new ArrayList<>(contractsById.values());
    }

    public List<Contract> getContractsByBuyer(UUID buyerId) {
        Set<UUID> ids = contractsByBuyer.get(buyerId);
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids.stream()
                .map(contractsById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Contract> getContractsByTransporter(UUID transporterId) {
        Set<UUID> ids = contractsByTransporter.get(transporterId);
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids.stream()
                .map(contractsById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Contract> getContractsByShopOwner(UUID shopOwnerId) {
        Set<UUID> ids = contractsByShopOwner.get(shopOwnerId);
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids.stream()
                .map(contractsById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Contract> getContractsByOrigin(String region) {
        Set<UUID> ids = contractsByOrigin.get(region);
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids.stream()
                .map(contractsById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Contract> getContractsByDestination(String region) {
        Set<UUID> ids = contractsByDestination.get(region);
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids.stream()
                .map(contractsById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Contract> getContractsByState(ContractState state) {
        Set<UUID> ids = contractsByState.get(state);
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids.stream()
                .map(contractsById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get all active contracts that a transporter is currently working on.
     */
    public List<Contract> getActiveContractsForTransporter(UUID transporterId) {
        return getContractsByTransporter(transporterId).stream()
                .filter(c -> c.getState() == ContractState.ACCEPTED ||
                           c.getState() == ContractState.IN_TRANSIT)
                .collect(Collectors.toList());
    }

    /**
     * Get all available contracts that can be accepted (POSTED and not expired).
     */
    public List<Contract> getAvailableContracts() {
        return getContractsByState(ContractState.POSTED).stream()
                .filter(Contract::canBeAccepted)
                .collect(Collectors.toList());
    }

    /**
     * Get delivered contracts waiting for buyer pickup in a region.
     */
    public List<Contract> getDeliveredContractsInRegion(String region) {
        return getContractsByDestination(region).stream()
                .filter(c -> c.getState() == ContractState.DELIVERED)
                .collect(Collectors.toList());
    }

    /**
     * Get delivered contracts for a specific buyer.
     */
    public List<Contract> getDeliveredContractsForBuyer(UUID buyerId) {
        return getContractsByBuyer(buyerId).stream()
                .filter(c -> c.getState() == ContractState.DELIVERED)
                .collect(Collectors.toList());
    }

    /**
     * Get contract by shop ID.
     */
    public Contract getContractByShopId(UUID shopId) {
        return contractsById.values().stream()
                .filter(c -> shopId.equals(c.getShopId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get the total count of active contracts.
     */
    public int getActiveContractCount() {
        return (int) contractsById.values().stream()
                .filter(c -> c.getState() != ContractState.COMPLETED &&
                           c.getState() != ContractState.CANCELLED &&
                           c.getState() != ContractState.EXPIRED)
                .count();
    }

    /**
     * Clear all contracts (for testing/debugging).
     */
    public void clear() {
        contractsById.clear();
        contractsByBuyer.clear();
        contractsByTransporter.clear();
        contractsByShopOwner.clear();
        contractsByOrigin.clear();
        contractsByDestination.clear();
        contractsByState.clear();
    }

    // Utility methods

    private <K> void removeFromIndex(Map<K, Set<UUID>> index, K key, UUID contractId) {
        if (key == null) {
            return;
        }
        Set<UUID> set = index.get(key);
        if (set != null) {
            set.remove(contractId);
            if (set.isEmpty()) {
                index.remove(key);
            }
        }
    }
}
