# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**SilkRoad** is a Paper Minecraft plugin (1.21.x, Java 17+) that integrates with BetterShop to enable cross-regional trading via player-driven delivery contracts. Players can order items from distant regions, and transporters earn bounties by delivering goods safely across dangerous territories.

This plugin runs on the **Stormcraft** Minecraft server, deployed on a VPS via Pterodactyl panel.

## Deployment Context

### Server Infrastructure
- **Server**: Stormcraft Minecraft server
- **Platform**: VPS with Pterodactyl panel
- **Pterodactyl Volume**: `/var/lib/pterodactyl/volumes/<server-id>/plugins/`
- **Build Target**: When building the JAR, output directly to the Pterodactyl plugins folder for immediate deployment

### Build Process
When compiling this plugin:
- Build the JAR using Maven: `mvn clean package`
- Output the JAR directly to the Pterodactyl volume plugins folder instead of `target/`
- This allows for immediate plugin reload/restart without manual file copying

Example build command:
```bash
mvn clean package && cp target/SilkRoad-*.jar /var/lib/pterodactyl/volumes/<server-id>/plugins/
```

Or configure Maven to output directly to the deployment location.

## Core Concept

Silk Road creates a three-party economic system:
- **Buyers** purchase from BetterShops in other regions (Silk Road enabled shops)
- **Contracts** are automatically created for delivery when remote purchases are made
- **Transporters** accept contracts and physically travel to deliver goods, earning bounties
- **Bounties** decay over time but scale with distance to encourage long journeys
- **Insurance** prevents exploitation while requiring transporter investment

## Architecture

### Core Components

- **ContractManager**: Handles contract creation, acceptance, completion, and cancellation
- **ContractRegistry**: Maintains active contracts and coordinates persistence
- **BountyCalculator**: Calculates initial bounty based on distance and item value
- **DecayManager**: Handles bounty decay over time with distance-based scaling
- **CargoItem**: Creates and validates soulbound cargo items for transporters
- **TradePostManager**: Manages trade post placement and interactions
- **TransporterManager**: Handles progression system (levels, XP, active contracts)
- **InsuranceManager**: Manages insurance payments to nation/town treasuries
- **BetterShopAPI**: Integration layer for BetterShop stock reservation and transactions

### Integration Points

**BetterShop Integration** (CRITICAL):
- Shop Silk Road toggle (`/shop silkroad enable/disable`)
- Stock reservation system (lock items during contract transit)
- Transaction completion on delivery
- Events: `ShopSilkRoadToggleEvent`, `ShopStockReserveEvent`, `ShopStockReleaseEvent`, `ShopSilkRoadTransactionEvent`

**Territory Integration**:
- **Towny**: Region detection, nation treasuries for insurance, trade post placement restrictions
- **Towns and Nations**: Alternative territory system with same functionality
- Insurance payments route to transporter's home nation/town treasury

**Economy Integration**:
- **Vault**: Required for all monetary transactions
- Server-paid bounties (configurable money creation)
- Insurance as economic sink to nation treasuries

**Stormcraft Integration** (Optional):
- Balance reference for essence farming rates
- Ensure transport earnings competitive with passive farming

### Package Structure

```
dev.ked.silkroad/
├── SilkRoadPlugin.java (main plugin class)
├── contracts/
│   ├── Contract.java (data model with UUID, state, parties, items, bounty)
│   ├── ContractManager.java (create, accept, complete, cancel logic)
│   ├── ContractRegistry.java (active contract tracking)
│   └── ContractState.java (enum: POSTED, ACCEPTED, IN_TRANSIT, DELIVERED, COMPLETED, CANCELLED, EXPIRED)
├── bounty/
│   ├── BountyCalculator.java (distance + value calculation)
│   ├── DecayManager.java (adaptive decay based on journey length)
│   └── RegionRateConfig.java (configurable rates per region)
├── cargo/
│   ├── CargoItem.java (create/validate soulbound bundle items)
│   └── CargoProtectionListener.java (prevent drop, trade, storage)
├── transport/
│   ├── TransporterManager.java (levels, XP, concurrent contracts)
│   ├── TransporterLevel.java (data model for progression)
│   └── InsuranceManager.java (charge insurance, route to treasuries)
├── tradeposts/
│   ├── TradePost.java (data model for trade post locations)
│   ├── TradePostManager.java (create, remove, track)
│   └── TradePostListener.java (interaction handling)
├── gui/
│   ├── TradePostMainGUI.java (27-slot main menu)
│   ├── ShopBrowserGUI.java (browse Silk Road shops, purchase items)
│   ├── ContractBrowserGUI.java (browse available contracts, accept)
│   ├── MyDeliveriesGUI.java (track active contracts)
│   └── OrderPickupGUI.java (claim delivered items)
├── integration/
│   ├── BetterShopAPI.java (hook into BetterShop for stock/transactions)
│   ├── TownyIntegration.java (region detection, treasury deposits)
│   ├── TownsAndNationsIntegration.java (alternative territory system)
│   └── VaultEconomy.java (economy wrapper)
├── storage/
│   ├── ContractDataManager.java (JSON persistence for contracts)
│   ├── TransporterDataManager.java (save XP, levels, stats)
│   └── TradePostDataManager.java (save trade post locations)
├── commands/
│   ├── SilkRoadCommand.java (main command: /silkroad)
│   └── TradePostCommand.java (admin tools for trade posts)
└── listeners/
    ├── CargoProtectionListener.java (prevent cargo item abuse)
    ├── TradePostProtectionListener.java (prevent breaking)
    └── ContractExpiryListener.java (handle decay and expiration)
```

## Key Implementation Details

### Contract Lifecycle

```
POSTED → ACCEPTED → IN_TRANSIT → DELIVERED → COMPLETED
    ↓         ↓          ↓
CANCELLED ← CANCELLED ← EXPIRED (bounty = 0)
```

1. **POSTED**: Buyer purchases from remote Silk Road shop, payment escrowed, stock reserved
2. **ACCEPTED**: Transporter accepts contract, pays insurance, receives cargo item
3. **IN_TRANSIT**: Transporter picks up from shop (virtual), travels to delivery location
4. **DELIVERED**: Transporter right-clicks Trade Post with cargo, receives bounty
5. **COMPLETED**: Buyer collects items from Trade Post, shop owner receives payment

**Expiration**: If bounty decays to $0 before delivery, contract expires, buyer refunded, stock released, transporter loses insurance.

### Bounty Calculation

```java
// Distance Component (primary)
distanceComponent = Σ (blocks_in_region × region_rate)

// Region rates (configurable):
// stormlands: $0.50/block (high risk)
// middle: $0.20/block (moderate)
// safe: $0.05/block (low risk)

// Value Component
valueComponent = (shopPrice × quantity) × 0.15  // 15% of item value

// Total
initialBounty = distanceComponent + valueComponent

// Adaptive Decay (encourages long journeys)
baseDecayRate = 0.10; // $/second
totalDistance = sum(regionDistances.values());
decayRate = baseDecayRate / (1 + (totalDistance / 1000.0));

// Minimum 10 minutes for any contract
if (initialBounty / decayRate < 600) {
    decayRate = initialBounty / 600.0;
}
```

**Example**:
- Diamond delivery: SafeZone → Stormlands
- Distance: 800 blocks (400 safe @ $0.05, 400 stormlands @ $0.50)
- Shop price: $500/diamond, quantity: 2
- Distance component: (400 × $0.05) + (400 × $0.50) = $220
- Value component: ($500 × 2) × 0.15 = $150
- **Initial bounty: $370**
- Decay rate: $0.10 / (1 + 800/1000) = $0.056/sec (~110 min duration)

### Cargo System

**Cargo Item** (BUNDLE material):
- Single inventory slot per contract (regardless of quantity)
- Stored contract UUID in PersistentDataContainer
- Soulbound: Cannot drop, trade, store in chests, or lose on death
- Live-updating lore showing current bounty, decay rate, time left
- Right-click interactions:
  - Shop chest: Pick up items (virtual transfer)
  - Trade Post: Deliver and claim bounty
  - Air: Show navigation info

**Protection**:
- Block PlayerDropItemEvent
- Block InventoryClickEvent in other player inventories
- Block InventoryMoveItemEvent to chests
- Keep in inventory on PlayerDeathEvent
- Persist through logout/login

### Insurance System

**Purpose**: Economic sink, prevent contract abandonment, add commitment

**Mechanics**:
- Transporter pays 10% of initial bounty upfront to accept contract
- Payment goes to transporter's home nation/town treasury (Towny/TaN integration)
- On successful delivery: transporter earns full bounty (net profit = bounty - insurance)
- On contract failure/expiry: insurance NOT refunded (sunk cost)

**Towny Integration**:
```java
Town town = TownyAPI.getInstance().getTown(transporter);
Nation nation = town.getNation();
if (nation != null) {
    nation.getAccount().deposit(insurance, "Silk Road Insurance");
} else {
    town.getAccount().deposit(insurance, "Silk Road Insurance");
}
```

**Towns and Nations Integration**: Similar API for territory treasury deposits

### Trade Post System

**Block Type**: LECTERN (book = contracts theme) or BARREL (storage theme), configurable

**Placement Requirements**:
- Permission: `silkroad.tradepost.create`
- Must be within town/nation claim (configurable)
- One trade post per town (configurable)
- Protected from destruction

**GUI Navigation**:
- **Main Menu** (27 slots): Info, Browse Shops, Delivery Jobs, My Deliveries, Pickup Orders, Stats
- **Shop Browser** (54 slots): Filter by region/item/price, purchase items (creates contracts)
- **Contract Browser** (54 slots): Filter by origin/destination/bounty, accept contracts
- **My Deliveries** (27 slots): Track active contracts, view live bounty updates
- **Order Pickup** (27 slots): Claim items delivered to your region

### Transporter Progression

**XP Earning**:
- +10 XP per 100 blocks traveled with cargo
- +50 XP per contract completed
- +100 XP for high-value deliveries (>$1000 shop value)
- +25 XP per region crossed

**Levels**:
- Level 1 (Novice): 0 XP, 1 contract max, $500 value limit
- Level 2 (Courier): 500 XP, 2 contracts max, $1000 value limit
- Level 3 (Trader): 1500 XP, 3 contracts max, $5000 value limit
- Level 4 (Merchant): 3500 XP, 4 contracts max, unlimited value, 5% insurance discount
- Level 5 (Caravan Master): 7000 XP, 5 contracts max, unlimited value, 10% insurance discount

**Benefits**: Multiple concurrent contracts, high-value access, insurance discounts, priority on new contracts

## BetterShop Integration (CRITICAL)

### Required API Methods

BetterShop must expose these methods for Silk Road integration:

```java
// Query Silk Road enabled shops
public List<Shop> getSilkRoadShops(String regionName);
public List<Shop> getAllSilkRoadShops();

// Stock reservation system
public boolean canReserveStock(Shop shop, int quantity);
public boolean reserveStock(Shop shop, int quantity, UUID contractId);
public void releaseReservation(UUID contractId);

// Transaction completion
public void completeTransaction(UUID contractId, UUID buyer);
```

### Required Events

BetterShop should fire these events for Silk Road to listen to:

```java
- ShopSilkRoadToggleEvent (when Silk Road enabled/disabled)
- ShopStockReserveEvent (contract accepted, stock locked)
- ShopStockReleaseEvent (contract cancelled/expired)
- ShopSilkRoadTransactionEvent (delivery completed)
```

### Shop Data Extension

BetterShop Shop class needs:
- `boolean silkRoadEnabled` - flag for Silk Road participation
- `Map<UUID, Integer> reservedStock` - contract UUID → quantity reserved
- Methods to reserve/release stock atomically

### Commands to Add to BetterShop

```
/shop silkroad enable        # Enable for shop you're looking at
/shop silkroad disable       # Disable for shop you're looking at
/shop silkroad enable all    # Enable for all your shops
/shop silkroad disable all   # Disable for all your shops
/shop create buy <price> --silkroad   # Create with Silk Road enabled
/shop create sell <price> --silkroad  # Create with Silk Road enabled
```

## Persistence

**Storage Format**: JSON files (simple, upgradeable to SQLite if needed)

**Files**:
- `contracts.json` - Active contracts with full state
- `transporters.json` - Player XP, levels, lifetime stats
- `tradeposts.json` - Trade post locations by town/region
- `pending_orders.json` - Delivered items waiting for buyer pickup
- `history.json` - Completed contract history (last 50 per player)

**Save Triggers**:
- Contract state changes (accept, deliver, cancel, expire)
- Every 30 seconds (auto-save task)
- Server shutdown (graceful save)

**Restoration on Startup**:
- Restore all active contracts
- Regenerate cargo items to transporter inventories
- Restore transporter XP and levels
- Restore trade post locations

## Anti-Abuse & Edge Cases

### Preventing Exploits

1. **Cargo Duplication**: Cargo items have unique contract UUID in PDC, validated on delivery
2. **Insurance Fraud**: No refunds on contract cancellation/expiry
3. **Stock Manipulation**: Stock reserved atomically, shop owner can't remove reserved items
4. **Multiple Pickups**: Track pickup state per contract, prevent duplicate pickups
5. **Fake Deliveries**: Validate contract ID, pickup state, and stock reservation on delivery

### Critical Edge Cases

**Shop Deleted While Contract Active**:
- Auto-cancel contract
- Refund transporter insurance (exceptional case)
- Refund buyer payment
- Remove cargo item

**Transporter Offline/Banned**:
- Contract continues to decay
- Expires naturally
- Insurance already paid (sunk)
- Contract becomes available again

**Buyer Offline at Delivery**:
- Items stored in "pending pickups" (persisted)
- Buyer can claim anytime via Trade Post

**Server Crash**:
- Contracts saved every 30 seconds
- On restart: restore all state
- Regenerate cargo items to transporter inventories

## Configuration Files

### config.yml

```yaml
economy:
  serverPaidBounty: true
  insurance:
    enabled: true
    rate: 0.10
    refundable: false

bounty:
  regionRates:  # $ per block traveled
    stormlands: 0.50
    middle: 0.20
    safe: 0.05
  valueMultiplier: 0.15
  decay:
    baseRate: 0.10
    minimumDuration: 600
    tickInterval: 5

cargo:
  material: BUNDLE
  preventDrop: true
  preventTrade: true
  preventStorage: true
  persistThroughDeath: true
  movementSlowness: 1
  loreUpdateInterval: 30

progression:
  enabled: true
  xpPerBlock: 0.1
  xpPerCompletion: 50
  xpPerRegionCrossed: 25
  xpHighValueBonus: 100
  levels:
    1: {name: "Novice", xpRequired: 0, maxContracts: 1, maxValue: 500, insuranceDiscount: 0.0}
    2: {name: "Courier", xpRequired: 500, maxContracts: 2, maxValue: 1000, insuranceDiscount: 0.0}
    3: {name: "Trader", xpRequired: 1500, maxContracts: 3, maxValue: 5000, insuranceDiscount: 0.0}
    4: {name: "Merchant", xpRequired: 3500, maxContracts: 4, maxValue: 999999, insuranceDiscount: 0.05}
    5: {name: "Caravan Master", xpRequired: 7000, maxContracts: 5, maxValue: 999999, insuranceDiscount: 0.10}

tradePosts:
  blockType: LECTERN
  onePerTown: true
  requiresClaim: true
  protectFromBreaking: true

integration:
  betterShop:
    enabled: true
    showTransitStock: true
  towny:
    enabled: true
    useNationTreasury: true
  townsandnations:
    enabled: false
  stormcraft:
    enabled: true
    essenceFarmingRate: 50

performance:
  contractBrowseLimit: 100
  contractSaveInterval: 30
  maxActiveContracts: 500
```

### messages.yml

All messages using MiniMessage format with placeholders:
- `{bounty}`, `{insurance}`, `{destination}`, `{region}`
- `{quantity}`, `{item}`, `{amount}`, `{level}`

## Commands & Permissions

### Commands

```
/silkroad - Main command (alias: /sr)
/silkroad help - Show help
/silkroad stats - View your transporter stats
/silkroad contracts - List your active contracts
/silkroad admin reload - Reload config
/silkroad admin contracts - View all active contracts
/silkroad admin cancel <contractId> - Cancel any contract

/tradepost create - Create a trade post (requires lectern in hand)
/tradepost remove - Remove a trade post (look at it)
/tradepost info - View trade post info
```

### Permissions

```
silkroad.use                  # Access Trade Posts and browse contracts
silkroad.transport            # Accept and complete contracts
silkroad.shop.enable          # Enable Silk Road on own shops
silkroad.tradepost.create     # Create trade posts
silkroad.tradepost.use        # Interact with trade posts
silkroad.admin                # All admin commands
silkroad.admin.reload         # Reload config
silkroad.admin.bypass         # Bypass level/insurance requirements
```

## Development Phases

### Phase 1: Core System (MVP)
- [x] BetterShop integration (Silk Road toggle, stock reservation)
- [x] Contract system (create, accept, deliver)
- [x] Basic bounty calculation (distance + value)
- [x] Cargo items (soulbound, single slot)
- [x] Trade Posts (place, interact, GUI)
- [x] Simple decay (fixed rate)
- [x] Insurance system (pay to accept)

### Phase 2: Progression & Polish
- [ ] Adaptive decay (scale with distance)
- [ ] Transporter levels & XP
- [ ] Multiple concurrent contracts
- [ ] Insurance discounts
- [ ] Contract filtering/sorting in GUI
- [ ] Enhanced messaging & notifications

### Phase 3: Advanced Features
- [ ] Towny integration (nation treasuries, region rates)
- [ ] Towns and Nations integration
- [ ] Stormcraft balancing (compare to essence farming)
- [ ] Contract history & statistics
- [ ] Leaderboards
- [ ] Admin tools (view all contracts, force cancel)

### Phase 4: Expansion (Future)
- [ ] Random events (bandit attacks, favorable winds)
- [ ] Guilds/companies
- [ ] Express delivery
- [ ] Bulk shipping
- [ ] Route planning UI with Dynmap

## Technology Stack

- Paper API 1.21.3
- Vault API (economy) - **Required**
- BetterShop API - **Required** (soft dependency)
- Towny API - Optional (soft dependency)
- Towns and Nations API - Optional (soft dependency)
- Java 17
- Build tool: Maven
- Data storage: JSON (Gson library)

## Dependencies (pom.xml)

- **BetterShop**: Soft dependency, must be present for Silk Road to function
- **Vault**: Hard dependency for economy
- **Towny** OR **Towns and Nations**: Soft dependency for territory integration
- **Gson**: Shaded for JSON persistence

## Performance Considerations

- Contract lookups by region for O(1) filtering
- Bounty decay updates every 5 seconds (not every tick)
- GUI updates on player interaction only
- Cargo item lore updates every 30 seconds (not per tick)
- Contract saves every 30 seconds (batch operation)
- Maximum active contracts limit (configurable, default 500)

## Development Notes

### Working with BetterShop Integration

When implementing the BetterShop API integration:
1. Check if BetterShop is loaded: `getServer().getPluginManager().getPlugin("BetterShop")`
2. Register event listeners for BetterShop events
3. Hook into BetterShop's Shop class for stock reservation
4. Validate stock availability before contract creation
5. Handle shop deletion gracefully (cancel related contracts)

### Territory Detection

**Towny**:
```java
TownyAPI api = TownyAPI.getInstance();
Town town = api.getTown(location);
Nation nation = town.getNation();
String regionName = nation != null ? nation.getName() : town.getName();
```

**Towns and Nations**:
```java
// Use TaN API to get territory at location
// Get town/region name for contract routing
```

### Region Distance Calculation

For bounty calculation, need to:
1. Get straight-line distance between shop and trade post
2. Determine which regions the path crosses (approximate)
3. Estimate blocks traveled through each region
4. Apply per-region rates

**Simple approach**: Divide distance proportionally based on region locations
**Advanced approach**: Actual pathfinding or player-specified route

## Success Metrics

Track these for balancing:
- Contracts completed per day
- Average contract bounty
- Contract completion rate (% not expired)
- Average transporter earnings/minute vs. essence farming
- Contract acceptance time (how long before someone accepts)
- Regional trade volume (which routes are popular)

## Economy Balancing

**Target**: Make transport earnings ~3x passive farming (Stormcraft essence)

If essence farming = $5/min:
- Target transport rate: $15/min for medium journeys
- Adjust region rates accordingly

**Example balanced journey**:
- 800 blocks, 10 minutes travel time
- Bounty: $150-$200 after insurance
- Rate: $15-$20/min ✓

## Edge Case Testing Checklist

- [ ] Shop deleted during active contract
- [ ] Transporter goes offline with cargo
- [ ] Buyer goes offline before claiming delivery
- [ ] Server crash during delivery
- [ ] Multiple contracts to same shop
- [ ] Inventory full when claiming delivery
- [ ] Trade post destroyed with pending orders
- [ ] Region/nation deleted during active contract
- [ ] Transporter banned/kicked during delivery
- [ ] Contract expires exactly at server restart
