package dev.ked.silkroad.contracts;

import dev.ked.silkroad.SilkRoadPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Periodically warns players about contracts expiring soon.
 */
public class ContractExpiryWarningTask extends BukkitRunnable {
    private final SilkRoadPlugin plugin;
    private final Set<UUID> warnedContracts = new HashSet<>();

    // Warning thresholds in milliseconds
    private static final long WARNING_THRESHOLD_5MIN = 5 * 60 * 1000;
    private static final long WARNING_THRESHOLD_2MIN = 2 * 60 * 1000;
    private static final long WARNING_THRESHOLD_1MIN = 60 * 1000;
    private static final long WARNING_THRESHOLD_30SEC = 30 * 1000;

    public ContractExpiryWarningTask(SilkRoadPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayerContracts(player);
        }
    }

    private void checkPlayerContracts(Player player) {
        List<Contract> activeContracts = plugin.getContractRegistry()
                .getActiveContractsForTransporter(player.getUniqueId());

        for (Contract contract : activeContracts) {
            long timeRemaining = contract.getTimeRemaining();
            UUID contractId = contract.getContractId();

            // Check if bounty is critically low
            if (contract.getCurrentBounty() < 50 && !warnedContracts.contains(contractId)) {
                sendLowBountyWarning(player, contract);
                warnedContracts.add(contractId);
                continue;
            }

            // Check expiry warnings
            if (timeRemaining <= WARNING_THRESHOLD_30SEC && !warnedContracts.contains(contractId)) {
                sendExpiryWarning(player, contract, "30 SECONDS", NamedTextColor.DARK_RED, true);
                warnedContracts.add(contractId);
            } else if (timeRemaining <= WARNING_THRESHOLD_1MIN && !warnedContracts.contains(getWarningKey(contractId, 1))) {
                sendExpiryWarning(player, contract, "1 MINUTE", NamedTextColor.RED, true);
                warnedContracts.add(getWarningKey(contractId, 1));
            } else if (timeRemaining <= WARNING_THRESHOLD_2MIN && !warnedContracts.contains(getWarningKey(contractId, 2))) {
                sendExpiryWarning(player, contract, "2 minutes", NamedTextColor.GOLD, false);
                warnedContracts.add(getWarningKey(contractId, 2));
            } else if (timeRemaining <= WARNING_THRESHOLD_5MIN && !warnedContracts.contains(getWarningKey(contractId, 5))) {
                sendExpiryWarning(player, contract, "5 minutes", NamedTextColor.YELLOW, false);
                warnedContracts.add(getWarningKey(contractId, 5));
            }

            // Remove from warned set if contract is completed or expired
            if (timeRemaining <= 0 || contract.getState() != ContractState.ACCEPTED) {
                removeWarningsForContract(contractId);
            }
        }
    }

    private void sendExpiryWarning(Player player, Contract contract, String timeStr, NamedTextColor color, boolean urgent) {
        Component message = Component.text()
                .append(Component.text("⚠ CONTRACT EXPIRING: ", color, TextDecoration.BOLD))
                .append(Component.text(contract.getDestinationRegion(), NamedTextColor.WHITE))
                .append(Component.text(" in ", color))
                .append(Component.text(timeStr, color, TextDecoration.BOLD))
                .append(Component.text("!", color))
                .build();

        player.sendMessage(message);

        // Play sound
        if (urgent) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.7f);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
        }

        // Send actionbar reminder
        Component actionBar = Component.text()
                .append(Component.text("⚠ ", color))
                .append(Component.text("Contract to ", NamedTextColor.GRAY))
                .append(Component.text(contract.getDestinationRegion(), NamedTextColor.WHITE))
                .append(Component.text(" expires in ", NamedTextColor.GRAY))
                .append(Component.text(timeStr, color))
                .build();
        player.sendActionBar(actionBar);
    }

    private void sendLowBountyWarning(Player player, Contract contract) {
        Component message = Component.text()
                .append(Component.text("⚠ LOW BOUNTY: ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("Contract to ", NamedTextColor.GRAY))
                .append(Component.text(contract.getDestinationRegion(), NamedTextColor.WHITE))
                .append(Component.text(" bounty below ", NamedTextColor.GRAY))
                .append(Component.text("$50", NamedTextColor.RED))
                .append(Component.text("!", NamedTextColor.RED))
                .build();

        player.sendMessage(message);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

        // Actionbar
        Component actionBar = Component.text()
                .append(Component.text("⚠ ", NamedTextColor.RED))
                .append(Component.text("Bounty: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("$%.2f", contract.getCurrentBounty()), NamedTextColor.RED))
                .append(Component.text(" | Deliver quickly!", NamedTextColor.GRAY))
                .build();
        player.sendActionBar(actionBar);
    }

    private UUID getWarningKey(UUID contractId, int minuteThreshold) {
        // Create unique key for each warning threshold
        return UUID.nameUUIDFromBytes((contractId.toString() + "_" + minuteThreshold).getBytes());
    }

    private void removeWarningsForContract(UUID contractId) {
        warnedContracts.remove(contractId);
        warnedContracts.remove(getWarningKey(contractId, 5));
        warnedContracts.remove(getWarningKey(contractId, 2));
        warnedContracts.remove(getWarningKey(contractId, 1));
    }
}
