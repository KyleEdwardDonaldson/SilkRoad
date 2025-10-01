package dev.ked.silkroad;

import dev.ked.silkroad.commands.SilkRoadCommand;
import dev.ked.silkroad.commands.TradePostCommand;
import dev.ked.silkroad.contracts.ContractManager;
import dev.ked.silkroad.contracts.ContractRegistry;
import dev.ked.silkroad.bounty.BountyCalculator;
import dev.ked.silkroad.bounty.DecayManager;
import dev.ked.silkroad.cargo.CargoProtectionListener;
import dev.ked.silkroad.cargo.CargoUpdateTask;
import dev.ked.silkroad.contracts.ContractExpiryWarningTask;
import dev.ked.silkroad.transport.TransporterManager;
import dev.ked.silkroad.transport.InsuranceManager;
import dev.ked.silkroad.transport.MovementTracker;
import dev.ked.silkroad.tradeposts.TradePostManager;
import dev.ked.silkroad.tradeposts.TradePostListener;
import dev.ked.silkroad.integration.BetterShopAPI;
import dev.ked.silkroad.integration.TownyIntegration;
import dev.ked.silkroad.integration.VaultEconomy;
import dev.ked.silkroad.storage.ContractDataManager;
import dev.ked.silkroad.storage.TransporterDataManager;
import dev.ked.silkroad.storage.TradePostDataManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class SilkRoadPlugin extends JavaPlugin {

    private static SilkRoadPlugin instance;

    // Core managers
    private ContractManager contractManager;
    private ContractRegistry contractRegistry;
    private BountyCalculator bountyCalculator;
    private DecayManager decayManager;
    private TransporterManager transporterManager;
    private InsuranceManager insuranceManager;
    private TradePostManager tradePostManager;
    private MovementTracker movementTracker;
    private CargoUpdateTask cargoUpdateTask;
    private ContractExpiryWarningTask expiryWarningTask;

    // Integration
    private VaultEconomy economy;
    private BetterShopAPI betterShopAPI;
    private TownyIntegration townyIntegration;

    // Storage
    private ContractDataManager contractDataManager;
    private TransporterDataManager transporterDataManager;
    private TradePostDataManager tradePostDataManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Initialize economy
        if (!setupEconomy()) {
            getLogger().severe("Vault not found! Disabling SilkRoad...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize BetterShop integration
        if (!setupBetterShop()) {
            getLogger().warning("BetterShop not found! Some features may not work.");
        }

        // Initialize Towny integration (optional)
        setupTowny();

        // Initialize storage
        contractDataManager = new ContractDataManager(this);
        transporterDataManager = new TransporterDataManager(this);
        tradePostDataManager = new TradePostDataManager(this);

        // Initialize managers
        bountyCalculator = new BountyCalculator(this);
        contractRegistry = new ContractRegistry(this);
        decayManager = new DecayManager(this, contractRegistry);
        transporterManager = new TransporterManager(this, transporterDataManager);
        insuranceManager = new InsuranceManager(this, economy, townyIntegration);
        tradePostManager = new TradePostManager(this, tradePostDataManager);
        contractManager = new ContractManager(this, contractRegistry, bountyCalculator,
                transporterManager, insuranceManager, betterShopAPI);
        movementTracker = new MovementTracker(this, transporterManager);

        // Load data
        contractDataManager.loadContracts(contractRegistry);
        transporterDataManager.loadAll();
        tradePostDataManager.loadAll(tradePostManager);

        // Register listeners
        registerListeners();

        // Register commands
        registerCommands();

        // Start decay task
        decayManager.start();

        // Start cargo update task
        cargoUpdateTask = new CargoUpdateTask(this, contractRegistry);
        cargoUpdateTask.start();

        // Start contract expiry warning task (runs every 10 seconds)
        expiryWarningTask = new ContractExpiryWarningTask(this);
        expiryWarningTask.runTaskTimer(this, 200L, 200L); // 10 seconds = 200 ticks

        // Start auto-save task
        startAutoSave();

        getLogger().info("SilkRoad enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Stop tasks
        if (decayManager != null) {
            decayManager.stop();
        }
        if (cargoUpdateTask != null) {
            cargoUpdateTask.cancel();
        }
        if (expiryWarningTask != null) {
            expiryWarningTask.cancel();
        }

        // Save all data
        if (contractDataManager != null) {
            contractDataManager.saveContracts(contractRegistry);
        }
        if (transporterDataManager != null) {
            transporterDataManager.saveAll();
        }
        if (tradePostDataManager != null) {
            tradePostDataManager.saveAll(tradePostManager);
        }

        getLogger().info("SilkRoad disabled successfully!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = new VaultEconomy(rsp.getProvider());
        return true;
    }

    private boolean setupBetterShop() {
        if (getServer().getPluginManager().getPlugin("BetterShop") == null) {
            return false;
        }

        betterShopAPI = new BetterShopAPI(this);
        return betterShopAPI.isEnabled();
    }

    private void setupTowny() {
        if (getServer().getPluginManager().getPlugin("Towny") != null) {
            townyIntegration = new TownyIntegration(this);
            getLogger().info("Towny integration enabled!");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new CargoProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new TradePostListener(this, tradePostManager, contractManager), this);
        getServer().getPluginManager().registerEvents(new dev.ked.silkroad.gui.GUIListener(this), this);
        getServer().getPluginManager().registerEvents(movementTracker, this);
    }

    private void registerCommands() {
        getCommand("silkroad").setExecutor(new SilkRoadCommand(this, contractManager, transporterManager));
        getCommand("tradepost").setExecutor(new TradePostCommand(this, tradePostManager));
    }

    private void startAutoSave() {
        int saveInterval = getConfig().getInt("performance.contractSaveInterval", 30) * 20; // Convert to ticks

        getServer().getScheduler().runTaskTimer(this, () -> {
            contractDataManager.saveContracts(contractRegistry);
            transporterDataManager.saveAll();
            tradePostDataManager.saveAll(tradePostManager);
        }, saveInterval, saveInterval);
    }

    // Getters
    public static SilkRoadPlugin getInstance() {
        return instance;
    }

    public ContractManager getContractManager() {
        return contractManager;
    }

    public ContractRegistry getContractRegistry() {
        return contractRegistry;
    }

    public BountyCalculator getBountyCalculator() {
        return bountyCalculator;
    }

    public DecayManager getDecayManager() {
        return decayManager;
    }

    public TransporterManager getTransporterManager() {
        return transporterManager;
    }

    public InsuranceManager getInsuranceManager() {
        return insuranceManager;
    }

    public TradePostManager getTradePostManager() {
        return tradePostManager;
    }

    public VaultEconomy getEconomy() {
        return economy;
    }

    public BetterShopAPI getBetterShopAPI() {
        return betterShopAPI;
    }

    public TownyIntegration getTownyIntegration() {
        return townyIntegration;
    }

    public ContractDataManager getContractDataManager() {
        return contractDataManager;
    }

    public TransporterDataManager getTransporterDataManager() {
        return transporterDataManager;
    }

    public TradePostDataManager getTradePostDataManager() {
        return tradePostDataManager;
    }
}
