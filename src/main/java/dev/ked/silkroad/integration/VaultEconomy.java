package dev.ked.silkroad.integration;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * Wrapper for Vault economy operations.
 * Provides a clean interface for all monetary transactions in SilkRoad.
 */
public class VaultEconomy {

    private final Economy economy;

    public VaultEconomy(Economy economy) {
        this.economy = economy;
    }

    /**
     * Check if a player has enough money.
     */
    public boolean has(UUID playerId, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return economy.has(player, amount);
    }

    /**
     * Deposit money to a player's account.
     */
    public boolean deposit(UUID playerId, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    /**
     * Withdraw money from a player's account.
     */
    public boolean withdraw(UUID playerId, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * Get a player's balance.
     */
    public double getBalance(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return economy.getBalance(player);
    }

    /**
     * Format an amount as currency string.
     */
    public String format(double amount) {
        return economy.format(amount);
    }

    /**
     * Get the currency name (singular).
     */
    public String getCurrencyName() {
        return economy.currencyNameSingular();
    }

    /**
     * Get the currency name (plural).
     */
    public String getCurrencyNamePlural() {
        return economy.currencyNamePlural();
    }

    /**
     * Check if the economy system is enabled.
     */
    public boolean isEnabled() {
        return economy.isEnabled();
    }

    /**
     * Transfer money from one player to another.
     */
    public boolean transfer(UUID fromPlayerId, UUID toPlayerId, double amount) {
        if (!has(fromPlayerId, amount)) {
            return false;
        }

        if (!withdraw(fromPlayerId, amount)) {
            return false;
        }

        if (!deposit(toPlayerId, amount)) {
            // Rollback: refund the withdrawn amount
            deposit(fromPlayerId, amount);
            return false;
        }

        return true;
    }

    /**
     * Create money (server-paid bounty).
     * Simply deposits to player without withdrawing from anywhere.
     */
    public boolean createMoney(UUID playerId, double amount) {
        return deposit(playerId, amount);
    }

    /**
     * Destroy money (economic sink).
     * Simply withdraws from player without depositing anywhere.
     */
    public boolean destroyMoney(UUID playerId, double amount) {
        return withdraw(playerId, amount);
    }
}
