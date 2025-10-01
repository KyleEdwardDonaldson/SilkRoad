# Silk Road - Regional Trading System

## Overview
A plugin that integrates with BetterShop to enable cross-regional trading via player-driven delivery contracts. Players can order items from distant regions, and transporters earn bounties by delivering goods safely across dangerous territories.

## Core Concept
- **Buyers** purchase from BetterShops in other regions (Silk Road enabled shops)
- **Contracts** are automatically created for delivery
- **Transporters** accept contracts and physically travel to deliver goods
- **Bounties** decay over time but scale with distance to encourage long journeys
- **Insurance** prevents item/money loss while requiring transporter investment

---

## BetterShop Integration

### Shop Silk Road Toggle

**Commands:**
```
/shop silkroad enable        # Enable for shop you're looking at
/shop silkroad disable       # Disable for shop you're looking at
/shop silkroad enable all    # Enable for all your shops
/shop silkroad disable all   # Disable for all your shops
/shop create buy <price> --silkroad   # Create with Silk Road enabled
/shop create sell <price> --silkroad  # Create with Silk Road enabled
```

**Mechanics:**
- Look at a shop (raycast to chest/sign) to target it for commands
- No shop ID needed - intuitive targeting
- Silk Road flag saved in shop data
- Persists across restarts

### Stock Management

**Reserved Stock System:**
- When contract accepted: items reserved (not available for local purchase)
- Shop sign/hologram shows: "Stock: 45 (3 in transit)"
- If contract cancelled/expired: reservation released
- If contract completed: stock permanently removed, earnings added to shop

**Transaction Flow:**
```
1. Contract accepted → Reserve stock in shop
2. Transporter picks up → Items moved to cargo (virtual)
3. Delivery complete → Shop transaction finalized
4. Shop owner gets payment → Can collect earnings normally
```

### API/Events for Silk Road

```java
// Events BetterShop fires
- ShopSilkRoadToggleEvent (when Silk Road enabled/disabled)
- ShopStockReserveEvent (contract accepted, stock locked)
- ShopStockReleaseEvent (contract cancelled/expired)
- ShopSilkRoadTransactionEvent (delivery completed)

// API Methods
public List<Shop> getSilkRoadShops(String regionName)
public List<Shop> getAllSilkRoadShops()
public boolean canReserveStock(Shop shop, int quantity)
public boolean reserveStock(Shop shop, int quantity, UUID contractId)
public void releaseReservation(UUID contractId)
public void completeTransaction(UUID contractId, UUID buyer)
```

---

## Contract System

### Contract Types

**Type 1: Direct Shop Purchase (Auto-Generated)**
- Buyer browses Trade Post GUI
- Sees BetterShop from another region (Silk Road enabled)
- Clicks "Purchase" → Pays shop price
- Contract auto-created and posted to job board
- Stock reserved immediately

**Type 2: Custom Delivery Request (Future feature?)**
- Player posts "Wanted: X item" contract
- Offers payment for delivery
- Any player can fulfill (not tied to BetterShop)

### Contract Lifecycle

```
POSTED → ACCEPTED → IN_TRANSIT → DELIVERED → COMPLETED
    ↓         ↓          ↓
CANCELLED ← CANCELLED ← EXPIRED (bounty = 0)
```

### Contract Data Model

```java
class DeliveryContract {
    // Identity
    UUID contractId;
    long createdAt;
    ContractState state;

    // Shop Integration
    UUID shopId;
    Location shopLocation;      // Where to pick up (shop chest)
    String originRegion;        // e.g., "SafeZone"

    // Delivery
    Location deliveryLocation;  // Trade Post in buyer's region
    String destinationRegion;   // e.g., "Stormlands"

    // Items & Pricing
    ItemStack item;
    int quantity;
    double shopPrice;           // What buyer paid (escrowed)

    // Bounty System
    double initialBounty;       // Starting transporter payment
    double currentBounty;       // Decaying value
    double decayRate;           // $/second
    long expiresAt;             // When bounty hits 0

    // Parties
    UUID shopOwner;
    UUID buyer;
    UUID transporter;           // null until accepted

    // Journey Tracking
    Map<String, Double> regionDistances; // Region -> blocks to travel
    int transporterLevel;       // Required level to accept
}
```

---

## Bounty & Decay System

### Bounty Calculation

```java
initialBounty = (distance-based) + (value-based)

// Distance Component (primary)
distanceComponent = Σ (blocks_in_region × region_rate)

// Region Rates (per block)
stormlands: $0.50    // High risk, high reward
middle:     $0.20    // Moderate
safe:       $0.05    // Low risk, low reward

// Value Component
valueComponent = (shopPrice × quantity) × 0.15  // 15% of item value

// No slot calculation (all cargo is 1 item regardless)
```

**Example:**
- Diamond delivery: SafeZone → Stormlands
- Distance: 800 blocks (400 safe, 400 stormlands)
- Shop price: $500 per diamond, quantity: 2
- Calculation:
  - Distance: (400 × $0.05) + (400 × $0.50) = $20 + $200 = $220
  - Value: ($500 × 2) × 0.15 = $150
  - **Total: $370 initial bounty**

### Adaptive Decay System

**Problem:** Fixed decay rate discourages long journeys

**Solution:** Scale decay rate inversely with journey length

```java
// Decay formula
baseDecayRate = 0.10; // $/second
totalDistance = sum(regionDistances.values());

// Longer journeys decay slower
decayRate = baseDecayRate / (1 + (totalDistance / 1000.0));

// Example:
// 500 blocks:  decay = 0.10 / 1.5  = $0.067/sec  (bounty lasts ~92 min)
// 2000 blocks: decay = 0.10 / 3.0  = $0.033/sec  (bounty lasts ~187 min)
// 5000 blocks: decay = 0.10 / 6.0  = $0.017/sec  (bounty lasts ~364 min)

// Minimum duration: 10 minutes even for short trips
if (initialBounty / decayRate < 600) {
    decayRate = initialBounty / 600.0;
}
```

**Result:** Long journeys remain profitable longer, encouraging ambitious routes

### Decay Tick Rate
- Update every 5 seconds (not every second)
- Reduces server load
- Bounty display shows real-time estimate

---

## Insurance System

### Transporter Insurance

**Purpose:** Prevent abuse, create economic sink, add realism

**Mechanics:**
- Transporters pay insurance upfront to accept contract
- Insurance cost: 10% of initial bounty
- Paid to transporter's home region/nation treasury (Towny integration)
- If delivery successful: transporter gets full bounty
- If contract fails (expired): insurance NOT refunded
- Insurance ensures commitment to delivery

**Example:**
- Contract bounty: $370
- Insurance cost: $37
- Transporter pays $37 to their nation
- On success: earns $370 (net +$333)
- On failure: loses $37

### Insurance Payment Flow

```java
void acceptContract(Player transporter, Contract contract) {
    double insurance = contract.initialBounty * 0.10;

    // Determine transporter's home region
    Town town = TownyAPI.getInstance().getTown(transporter);
    Nation nation = town.getNation();

    // Withdraw insurance from transporter
    if (!vault.has(transporter, insurance)) {
        transporter.sendMessage("Insufficient funds for insurance!");
        return;
    }

    vault.withdrawPlayer(transporter, insurance);

    // Deposit to nation treasury
    if (nation != null) {
        nation.getAccount().deposit(insurance, "Silk Road Insurance");
    } else {
        // Fallback: town treasury or server sink
        town.getAccount().deposit(insurance, "Silk Road Insurance");
    }

    // Proceed with contract acceptance
    contract.transporter = transporter.getUniqueId();
    contract.state = ContractState.ACCEPTED;
    giveCargoItem(transporter, contract);
}
```

### Buyer/Shop Owner Protection

**No insurance needed** - fully protected by system:
- Buyer: Payment escrowed until delivery complete
- Shop Owner: Stock reserved, payment guaranteed on completion
- If contract fails: buyer refunded, stock released back to shop
- No risk of loss

---

## Transporter Progression System

### Transporter Levels

**Earning XP:**
- +10 XP per 100 blocks traveled with cargo
- +50 XP bonus per contract completed
- +100 XP bonus for high-value deliveries (>$1000 shop value)
- +25 XP bonus per region crossed

**Level Thresholds:**
```
Level 1 (Novice):      0 XP   - 1 contract at a time
Level 2 (Courier):   500 XP   - 2 contracts at a time
Level 3 (Trader):   1500 XP   - 3 contracts, access to high-value contracts (>$500)
Level 4 (Merchant): 3500 XP   - 4 contracts, 5% insurance discount
Level 5 (Caravan):  7000 XP   - 5 contracts, 10% insurance discount, priority on new contracts
```

**Level Benefits:**
1. **Multiple Contracts:** Carry multiple cargo items, efficient routes
2. **High-Value Access:** Prevent new players from abandoning expensive contracts
3. **Insurance Discount:** Reward experienced transporters
4. **Priority Access:** See new contracts before lower-level transporters

### Trust-Based Restrictions

```java
boolean canAcceptContract(Player transporter, Contract contract) {
    int level = getTransporterLevel(transporter);

    // Check active contracts limit
    int activeContracts = getActiveContracts(transporter).size();
    int maxContracts = level;  // Level 3 = 3 concurrent contracts
    if (activeContracts >= maxContracts) {
        return false;
    }

    // Check value restrictions
    double totalValue = contract.shopPrice * contract.quantity;
    if (level < 3 && totalValue > 500) {
        return false;  // High-value requires level 3+
    }

    return true;
}
```

---

## Cargo System

### Cargo Items

**Implementation:**
- Single inventory slot per contract (regardless of item quantity)
- Special item (BUNDLE or custom texture)
- Contains contract metadata (not actual items - virtual)
- Soulbound: Cannot drop, trade, store in chests, or lose on death

**Cargo Item Properties:**
```java
ItemStack createCargoItem(Contract contract) {
    ItemStack cargo = new ItemStack(Material.BUNDLE);
    ItemMeta meta = cargo.getItemMeta();

    meta.setDisplayName("§6📦 Cargo: " + formatItemName(contract.item));
    meta.setLore(Arrays.asList(
        "§7Quantity: §f" + contract.quantity,
        "§7From: §e" + contract.originRegion,
        "§7To: §e" + contract.destinationRegion,
        "§7Current Bounty: §a$" + String.format("%.2f", contract.currentBounty),
        "",
        "§cSoulbound - Cannot drop or trade",
        "§7Right-click Trade Post to deliver"
    ));

    // Store contract ID in PDC
    meta.getPersistentDataContainer().set(
        new NamespacedKey(plugin, "contract_id"),
        PersistentDataType.STRING,
        contract.contractId.toString()
    );

    cargo.setItemMeta(meta);
    return cargo;
}
```

**Cargo Protection:**
- Block drop event (PlayerDropItemEvent)
- Block trade/give (InventoryClickEvent in other player's inventory)
- Block chest storage (InventoryMoveItemEvent)
- Persist through death (PlayerDeathEvent - keep cargo in inventory)
- Persist through logout (save contract state)

**Cargo Movement Effect:**
- Optional: Slowness I while carrying cargo (configurable)
- Makes journey feel weighty, adds challenge
- Can disable in config for fast-paced gameplay

---

## Trade Post System

### Trade Post Block

**Block Type:** Lectern (writable book = contracts) or Barrel (storage theme)

**Placement:**
- Requires permission: `silkroad.tradepost.create`
- Must be within town/nation claims
- One trade post per town (or configurable limit)
- Protected from destruction (like shop chests)

**Functionality:**
1. **Browse Contracts** - View all available delivery jobs
2. **Accept Contract** - Become transporter, receive cargo
3. **Deliver Cargo** - Complete delivery, earn bounty
4. **Pickup Orders** - Collect items delivered to you

### Trade Post GUI - Detailed Design

#### Main Menu (27 Slots)

**Layout:**
```
Row 1: [  -] [  -] [  -] [  -] [INFO] [  -] [  -] [  -] [  -]
Row 2: [SHOP] [  -] [JOBS] [  -] [DELI] [  -] [PICK] [  -] [  -]
Row 3: [STAT] [  -] [  -] [  -] [  -] [  -] [  -] [  -] [CLOS]
```

**Key Items:**

**Slot 4 - Info Panel** (Book)
```
§6§l📜 Silk Road Trade Post
§7Region: §e{regionName}

§a✓ Available Contracts: §f47
§e⚡ Your Active Deliveries: §f2/3
§b📦 Pending Pickups: §f1
```

**Slot 9 - Browse Shops** (Chest)
- View all Silk Road-enabled shops across regions
- Purchase items for delivery to your location
- Click to open regional shop browser

**Slot 11 - Delivery Jobs** (Map)
- Browse available delivery contracts
- Accept contracts to earn bounties
- Shows your transporter level and XP

**Slot 13 - My Deliveries** (Ender Chest)
- Track active contracts you're transporting
- View live bounty updates
- Navigate to pickup/delivery locations

**Slot 15 - Pickup Orders** (Hopper)
- Collect items delivered to your region
- Glows when items are waiting
- Click to claim orders

**Slot 18 - Your Stats** (Player Head)
- Transporter level and XP progress
- Lifetime statistics (deliveries, distance, earnings)
- Click for leaderboard

---

#### Browse Regional Shops GUI (54 Slots)

**Purpose:** Players buy items from distant BetterShops (triggers contract creation)

**Layout:**
```
Row 1: [TITL] [  -] [REG1] [REG2] [REG3] [REG4] [REG5] [  -] [  -]
Row 2: [TYPE] [  -] [ITM1] [ITM2] [ITM3] [  -] [SORT] [  -] [  -]
Rows 3-5: Shop listings (27 slots, scrollable)
Row 6: [BACK] [  -] [ UP ] [DOWN] [  -] [REFR] [  -] [  -] [SEAR]
```

**Filters:**
- **Region Filter (Slots 2-6):** Toggle regions on/off (green=included, red=excluded)
- **Shop Type Filter (Slot 9):** BUY/SELL/All shops
- **Item Filter (Slots 11-13):** Filter by item category
- **Sort Options (Slot 15):** Distance, price, stock, recently listed

**Shop Display (Each listing):**
```
§6§l🏪 Diamond Shop
§7Owner: §fSteve
§7Location: §eSafeZone §8(420m away)

§aPrice: §f$500 §7per item
§bStock: §f24 available §7(§e3 in transit§7)

§7Delivery Bounty: §a~$285
§7Estimated Time: §f8-15 minutes

§e§lClick to purchase!
```

**Purchase Confirmation Screen (27 slots):**
- Item preview with quantity selectors (-16, -8, -1, +1, +8, +16, custom)
- Purchase summary (total price, delivery info, estimated bounty)
- Confirm/Cancel buttons
- Real-time balance checking

---

#### Delivery Jobs Browser GUI (54 Slots)

**Purpose:** Transporters find and accept delivery contracts

**Layout:**
```
Row 1: [TITL] [  -] [LEVL] [  -] [ACTV] [  -] [INSU] [  -] [  -]
Row 2: [FROM] [FROM] [ TO ] [ TO ] [  -] [DIST] [BOUN] [SORT] [  -]
Rows 3-5: Contract listings (27 slots, scrollable)
Row 6: [BACK] [  -] [ UP ] [DOWN] [  -] [REFR] [  -] [  -] [FILT]
```

**Header Info:**
- **Your Level (Slot 2):** Shows transporter level, XP progress, perks
- **Active Contracts (Slot 4):** Current/max contracts, slots available
- **Insurance Info (Slot 6):** Insurance rate, discount, destination

**Filters:**
- **Origin Region (Slots 9-10):** Filter contracts from specific regions
- **Destination Region (Slots 11-12):** Filter by delivery destination
- **Distance Range (Slot 14):** Short/Medium/Long journeys
- **Bounty Range (Slot 15):** Minimum bounty filters
- **Sort Options (Slot 16):** Bounty, distance, time left, recently posted

**Contract Display (Each listing):**
```
§6§l📦 Diamond Delivery

§7From: §eSafeZone §8(Shop: Steve's Gems)
§7To: §bStormlands Trade Post
§7Distance: §f840m §8(§e400 §7safe, §c440 §7storm)

§7Item: §fDiamond §7x§f2
§7Value: §a$1,000

§a§lBounty: §f$372 §8(decaying)
§7Insurance: §e$33.48 §8(-10% discount)
§7Time Left: §f2h 45m

§7Requires: §aLevel 3+ §8(High Value)

§e§lClick to accept!
```

**Visual Indicators:**
- Green glow: High bounty (>$400)
- Yellow glow: Medium ($200-400)
- Red border: Expiring soon (<30 min)

**Contract Acceptance Screen (27 slots):**
- Journey route display (colored glass panes per region)
- Item preview and contract details
- Live bounty calculation with decay rate
- Insurance cost breakdown
- Accept/Cancel buttons

---

#### My Active Deliveries GUI (27-54 Slots)

**Purpose:** Track contracts you're currently transporting

**Layout:**
```
Row 1: [TITL] [  -] [  -] [STAT] [  -] [  -] [  -] [  -] [  -]
Row 2: [CON1] [CON2] [CON3] [CON4] [CON5] [  -] [  -] [  -] [  -]
Rows 3-5: (empty or additional contracts)
Row 6: [  -] [  -] [  -] [  -] [  -] [  -] [  -] [  -] [BACK]
```

**Summary Stats (Slot 3):**
```
§6§lYour Deliveries

§7Active Contracts: §f{active}§7/§f{max}
§7Total Potential: §a${totalBounty}
§7Total Insurance Paid: §e${insurance}

§eComplete deliveries quickly!
§7Bounties decay over time.
```

**Active Contract Display:**
```
§6§l📦 Active: Diamond Delivery

§7Status: §eIn Transit

§7From: §fSafeZone
§7To: §bStormlands Trade Post
§7Distance Remaining: §f612m

§a§lCurrent Bounty: §f$358.24
§cDecaying at: §f-$0.074/sec
§7Time Until $0: §f1h 21m

§7Accepted: §f12 minutes ago
§7Insurance Paid: §e$33.48

§8Next Step: §7Travel to pickup location
§e§lClick for details!
```

**Status Colors:**
- §e Yellow: "Awaiting Pickup" (not picked up from shop yet)
- §a Green: "In Transit" (picked up, traveling to delivery)
- §c Red: "Urgent" (<30 minutes until bounty = $0)

**Contract Detail View (27 slots) - Opened by clicking contract:**
- **Item Preview:** Shows cargo item and current bounty
- **Pickup Waypoint:** Shop location with coordinates
- **Delivery Waypoint:** Trade Post location with coordinates
- **Live Info:** Real-time bounty decay tracker (updates every 5s)
- **Progress Tracker:** Journey checklist with next steps
- **Navigation Compass:** Direction to next objective
- **Abandon Button:** Cancel contract (double-click confirmation, forfeits insurance)

---

#### Order Pickup GUI (27-54 Slots)

**Purpose:** Collect items that were delivered to you

**Layout:**
```
Row 1: [TITL] [  -] [  -] [INFO] [  -] [  -] [  -] [  -] [  -]
Rows 2-4: Order listings (18+ slots)
Rows 5-6: (expansion if many orders)
Row 6: [BACK] [  -] [  -] [  -] [  -] [  -] [  -] [CLAL] [  -]
```

**Info Panel (Slot 3):**
```
§b§lPending Orders

§7Waiting for Pickup: §f{count}
§7Total Items: §f{totalItems}

§eClick orders to collect items
§7Or click "Claim All" below
```

**Order Display (Each order shows the actual item):**
```
§6§lDiamond Delivery

§7Quantity: §f2
§7From Shop: §fSteve's Gems
§7Origin: §eSafeZone
§7Delivered: §f3 minutes ago

§7Shop Price Paid: §a$1,000
§7Transporter: §fBob_TheBuilder
§7Delivery Time: §f14m 32s

§a§lClick to claim!
```

**Claim All Button (Slot 52):**
```
§a§lClaim All Orders

§7Will attempt to claim all {count} orders
§7Total items: §f{totalItems}
§7Required slots: §f{slotsNeeded}
§7Available slots: §f{freeSlots}

§e§lClick to claim all!
```
- Red/disabled if insufficient inventory space

---

### Interactive Workflows

#### Delivery Completion (Transporter)
When transporter right-clicks Trade Post with cargo:
1. Cargo item removed from inventory
2. Bounty paid instantly
3. Particle effects + sound (firework burst, level up sound)
4. Chat message with earnings breakdown
5. XP awarded (+level up notification if applicable)
6. Title display: "§a§l✓ DELIVERED" / "§7+$358.24"

#### Shop Pickup (Transporter)
When transporter right-clicks shop chest with cargo:
1. Validate cargo matches shop contract
2. Transfer stock (virtually) to cargo item
3. Update cargo lore: "§a✓ Picked Up"
4. Chat message with delivery instructions
5. Navigation prompt to Trade Post

#### Cargo Item (In-Inventory)
**Live updating lore** (refreshes every 30 seconds):
```
§6§l📦 Cargo: Diamond Delivery

§7From: §eSafeZone §7→ §bStormlands
§7Quantity: §f2x Diamond

§a§lCurrent Bounty: §f$358.24
§7Started: §a$372.00
§cDecay: §f-$0.074/sec

§7Time Left: §f1h 18m
§7Status: §a✓ In Transit

§e§lDeliver at Trade Post!
§7Right-click Trade Post to complete

§cCannot drop, trade, or store
```

**Actionable:**
- Right-click in air: Navigation message
- Right-click shop: Pickup (if awaiting pickup)
- Right-click Trade Post: Delivery (if in transit)

---

### Real-Time Features

**Bounty Updates:**
- GUIs update every 5 seconds (tick task)
- Color-coded by amount (green >$200, yellow $100-200, red <$100)
- Countdown timers for expiration

**Notifications (Action Bar):**
While carrying cargo:
```
§6Cargo: §fDiamond x2 §7| §a$358.24 §7| §f1h 18m left
```
- Updates every 30 seconds

**Expiry Warnings:**
- **30 min:** Warning message with current bounty
- **10 min:** Urgent message
- **Expired:** Contract cancelled notification

**Sounds & Particles:**
- Browse shops: Chest open
- Accept contract: Villager yes
- Pickup cargo: Item pickup
- Bounty decay warning: Note block (pling)
- Delivery complete: Level up sound + firework particles
- Level up: Totem particles
- Contract expired: Villager no

**Visual Polish:**
- Glowing items for high-value contracts
- Progress bars for XP (▮▮▮▯▯)
- Consistent color coding (green=profit, red=cost, yellow=warning)
- Stack sizes reflect quantities when possible

---

## Economy Balancing

### Stormcraft Essence Farming Comparison

**Stormcraft Plugin:** Players earn "essence" by standing in the storm
- Assume rate: ~50 essence/minute in storm
- Conversion: 100 essence = $10 (example, adjust based on actual rates)
- Effective rate: ~$5/minute passively

**Silk Road Target:** Make transportation competitive

**Example Journey (competitive with AFK farming):**
- SafeZone → Stormlands (800 blocks, ~10 minutes travel)
- Bounty: $370
- Insurance: $37
- Net earnings: $333
- Rate: $333 / 10 min = **$33.30/minute**

**Balancing Factors:**
- Transportation is active gameplay (not AFK)
- Requires initial capital (insurance)
- Has risk (time pressure, decay)
- Provides social/economic gameplay
- Should pay **2-5x** passive farming to incentivize

**Recommendation:** Adjust region rates to ensure:
```
Average transport earnings/min = 3x essence farming rate
```

If essence farming = $5/min:
- Target transport rate: $15/min (for medium journeys)
- Adjust `stormlands_rate`, `middle_rate`, `safe_rate` accordingly

### Money Flow

**Money Creation:**
- Transporter bounty: Created by server (inflation)
- Justification: Rewards active gameplay, drives economy

**Money Sinks:**
- Insurance payments: Go to nation treasuries (economic sink)
- Optional: Transaction tax on shop sales (separate from Silk Road)

**Shop Owner Revenue:**
- Normal shop prices (buyer pays)
- No change from regular BetterShop sales
- Silk Road expands market reach (more sales potential)

---

## Workflow Examples

### Example 1: Buyer Initiates (Direct Purchase)

1. **Player A** in Stormlands wants diamonds
2. Opens Trade Post, browses contracts
3. Sees BetterShop in SafeZone selling diamonds (Silk Road enabled)
4. Clicks "Purchase 2 diamonds for $1000"
5. Payment escrowed ($1000 withdrawn from Player A)
6. Contract created automatically:
   - Pickup: SafeZone shop
   - Delivery: Stormlands Trade Post
   - Bounty: $370 (calculated from distance + value)
7. Stock reserved in SafeZone shop (2 diamonds locked)
8. Contract posted to global job board

9. **Player B** (Level 3 Transporter) in Middle region browses jobs
10. Sees contract, clicks "Accept"
11. Pays $37 insurance to Middle nation treasury
12. Receives cargo item in inventory
13. Travels to SafeZone shop
14. Right-clicks shop chest with cargo item → Picks up (virtual)
15. Travels to Stormlands (takes 12 minutes)
16. Arrives at Stormlands Trade Post
17. Right-clicks Trade Post with cargo item → Delivers
18. Receives $370 bounty (net +$333 profit)
19. Shop owner receives $1000 (added to shop earnings)
20. Player A notified: "Your diamonds have arrived!"
21. Player A visits Trade Post, clicks "Pickup Orders", receives 2 diamonds

### Example 2: Shop Owner Sets Up Silk Road Shop

1. **Player C** in Stormlands creates BUY shop
2. `/shop create buy 500 --silkroad` (holding diamond)
3. Places shop chest, adds diamonds to stock
4. Shop is now visible in Trade Post GUIs across all regions

5. **Player D** in SafeZone sees shop in Trade Post GUI
6. Clicks "Purchase 1 diamond for $500"
7. Contract auto-created (same flow as Example 1)

8. **Alternate:** Local player buys from shop directly (before contract accepted)
   - Normal BetterShop transaction
   - Contract still available for remote buyers
   - First come, first served (local or Silk Road)

### Example 3: Contract Expires

1. **Player E** accepts contract (bounty: $200, insurance: $20)
2. Gets distracted, doesn't deliver in time
3. Bounty decays to $0 after 33 minutes
4. Contract auto-cancelled:
   - Cargo item removed from inventory
   - Stock reservation released in shop
   - Buyer refunded ($1000 returned)
   - Player E loses $20 insurance (gone to nation)
   - Contract re-posted as new job (if buyer still wants it)

---

## Anti-Abuse & Edge Cases

### Preventing Exploits

1. **Cargo Duplication:**
   - Cargo items stored with contract UUID in PDC
   - Contract can only be completed once
   - Cargo item deleted on delivery

2. **Insurance Fraud:**
   - Can't cancel contract and get insurance back
   - Can't "abandon" and reclaim insurance
   - Lost insurance goes to nation (not refundable)

3. **Stock Manipulation:**
   - Stock reserved atomically with contract creation
   - Shop owner can't remove reserved items
   - Can still sell other stock locally

4. **Multiple Pickups:**
   - Cargo pickup tracked per contract
   - Can only pick up once from shop
   - Subsequent attempts blocked

5. **Fake Deliveries:**
   - Delivery requires matching contract ID in cargo item
   - Can't deliver without picking up from shop first
   - Shop validates stock was actually reserved

### Edge Cases

**Shop Deleted While Contract Active:**
- Contract auto-cancelled
- Transporter refunded insurance
- Buyer refunded payment
- Cargo item removed

**Transporter Quits/Banned:**
- Contract expires naturally via decay
- Insurance already paid (sunk cost)
- Contract becomes available again

**Buyer Offline at Delivery:**
- Items stored in Trade Post "pending pickups"
- Safe until claimed (persisted to disk)
- Can claim anytime

**Region/Nation Deleted:**
- Existing contracts complete normally
- New contracts can't be created from/to that region
- Trade Posts in deleted regions disabled

**Server Crash During Delivery:**
- Contracts saved to disk every 30 seconds
- On restart: restore contract state
- Cargo items regenerated to transporter's inventory
- Stock reservations persist

---

## Configuration

### config.yml

```yaml
# Silk Road Configuration

# Economy
economy:
  serverPaidBounty: true  # Server creates money for bounties (vs. taxing buyer)
  insurance:
    enabled: true
    rate: 0.10  # 10% of bounty
    refundable: false

# Bounty Calculation
bounty:
  regionRates:  # $ per block traveled
    stormlands: 0.50
    middle: 0.20
    safe: 0.05
    # Add more regions as needed

  valueMultiplier: 0.15  # 15% of (shop price × quantity)

  decay:
    baseRate: 0.10  # $/second (before distance scaling)
    minimumDuration: 600  # 10 minutes minimum
    tickInterval: 5  # Update bounty every 5 seconds

# Cargo
cargo:
  material: BUNDLE  # Item type for cargo
  preventDrop: true
  preventTrade: true
  preventStorage: true
  persistThroughDeath: true
  movementSlowness: 1  # Slowness level (0 to disable)
  loreUpdateInterval: 30  # Update bounty in lore every 30 seconds

# Transporter Levels
progression:
  enabled: true
  xpPerBlock: 0.1  # 10 XP per 100 blocks
  xpPerCompletion: 50
  xpPerRegionCrossed: 25
  xpHighValueBonus: 100  # For >$1000 shop value

  levels:
    1:
      name: "Novice"
      xpRequired: 0
      maxContracts: 1
      maxValue: 500
      insuranceDiscount: 0.0
    2:
      name: "Courier"
      xpRequired: 500
      maxContracts: 2
      maxValue: 1000
      insuranceDiscount: 0.0
    3:
      name: "Trader"
      xpRequired: 1500
      maxContracts: 3
      maxValue: 5000
      insuranceDiscount: 0.0
    4:
      name: "Merchant"
      xpRequired: 3500
      maxContracts: 4
      maxValue: 999999
      insuranceDiscount: 0.05
    5:
      name: "Caravan Master"
      xpRequired: 7000
      maxContracts: 5
      maxValue: 999999
      insuranceDiscount: 0.10

# Trade Posts
tradePosts:
  blockType: LECTERN  # or BARREL
  onePerTown: true
  requiresClaim: true
  protectFromBreaking: true

# Integration
integration:
  betterShop:
    enabled: true
    showTransitStock: true  # Display "X in transit" on signs
  towny:
    enabled: true
    useNationTreasury: true  # Insurance goes to nation vs. town
  stormcraft:
    enabled: true
    essenceFarmingRate: 50  # Essence/min for balancing reference

# Performance
performance:
  contractBrowseLimit: 100  # Max contracts shown in GUI at once
  contractSaveInterval: 30  # Save to disk every 30 seconds
  maxActiveContracts: 500  # Global limit

# Misc
misc:
  contractHistorySize: 50  # Track last N contracts per player
  enableMetrics: true
```

### messages.yml

```yaml
# Contract Messages
contract:
  created: "<green>Contract created! Delivery bounty: <gold>${bounty}</gold>"
  accepted: "<green>Contract accepted! Deliver to <yellow>{destination}</yellow>"
  delivered: "<green>Contract completed! You earned <gold>${bounty}</gold>"
  expired: "<red>Contract expired - bounty reached $0"
  cancelled: "<yellow>Contract cancelled"

  insufficientInsurance: "<red>You need <gold>${insurance}</gold> for insurance!"
  levelTooLow: "<red>This contract requires transporter level {level}"
  maxContractsReached: "<red>You have too many active contracts ({current}/{max})"

# Buyer Messages
buyer:
  orderPlaced: "<green>Order placed! A transporter will deliver your items."
  orderDelivered: "<green>Your order has arrived! Visit a Trade Post to pick up."
  orderRefunded: "<yellow>Your order was cancelled. Refunded <gold>${amount}</gold>"

# Shop Owner Messages
shop:
  silkRoadEnabled: "<green>Silk Road enabled for this shop!"
  silkRoadDisabled: "<yellow>Silk Road disabled for this shop"
  stockReserved: "<yellow>{quantity}x {item} reserved for Silk Road delivery"
  saleCompleted: "<green>Silk Road sale completed! <gold>${amount}</gold> added to earnings"

# Trade Post Messages
tradePost:
  created: "<green>Trade Post created for {region}!"
  noItemsToPickup: "<yellow>No items waiting for pickup"
  itemsReceived: "<green>Received {items} from deliveries"

# Progression Messages
progression:
  levelUp: "<gold><bold>LEVEL UP!</bold> You are now a {level} transporter!"
  xpGained: "<gray>+{xp} Transport XP"
```

---

## Permissions

```yaml
# Base permission
silkroad.use  # Access to Trade Posts and browsing contracts

# Transporter
silkroad.transport  # Accept and complete contracts
silkroad.transport.multiple  # Accept multiple contracts (if level allows)

# Shop Owner
silkroad.shop.enable  # Enable Silk Road on own shops
silkroad.shop.toggle  # Use /shop silkroad commands

# Trade Post
silkroad.tradepost.create  # Place trade posts
silkroad.tradepost.use  # Interact with trade posts

# Admin
silkroad.admin  # All admin commands
silkroad.admin.contracts  # View/cancel any contract
silkroad.admin.tradepost  # Create/remove any trade post
silkroad.admin.reload  # Reload config
silkroad.admin.bypass  # Bypass level/insurance requirements
```

---

## Future Considerations

### Potential Features (Phase 2)

1. **Random Events:**
   - "Bandit attack" - small bounty reduction mid-transit
   - "Favorable winds" - bounty boost
   - "Toll roads" - fee required to enter certain regions

2. **Guilds/Companies:**
   - Players form transport companies
   - Shared contracts, pooled insurance
   - Company reputation system

3. **Express Delivery:**
   - Buyer pays extra for faster delivery (higher initial bounty)
   - Premium contracts

4. **Bulk Shipping:**
   - Combine multiple small contracts into one mega-contract
   - More efficient for transporters

5. **Route Planning:**
   - GUI shows suggested path
   - Integration with Dynmap for waypoints

6. **Leaderboards:**
   - Top transporters by deliveries, distance, earnings
   - Regional rankings

7. **Achievements:**
   - "First Delivery", "Cross-Continent Trader", "Speed Runner"
   - Cosmetic rewards, titles

---

## Technical Implementation Notes

### Plugin Structure

```
dev.ked.silkroad/
├── SilkRoadPlugin.java (main class)
├── contracts/
│   ├── Contract.java (data model)
│   ├── ContractManager.java (create, cancel, complete)
│   ├── ContractRegistry.java (active contracts tracking)
│   └── ContractState.java (enum)
├── bounty/
│   ├── BountyCalculator.java (calculate initial bounty)
│   ├── DecayManager.java (handle decay tick)
│   └── RegionRateConfig.java (region-specific rates)
├── cargo/
│   ├── CargoItem.java (create/validate cargo items)
│   └── CargoProtectionListener.java (prevent drop/trade)
├── transport/
│   ├── TransporterManager.java (levels, XP, active contracts)
│   ├── TransporterLevel.java (data model)
│   └── InsuranceManager.java (charge/refund insurance)
├── tradeposts/
│   ├── TradePost.java (data model)
│   ├── TradePostManager.java (create, remove, track)
│   └── TradePostListener.java (interaction handling)
├── gui/
│   ├── ContractBrowserGUI.java (browse available contracts)
│   ├── MyDeliveriesGUI.java (active contracts)
│   ├── OrderPickupGUI.java (claim delivered items)
│   └── FilterMenu.java (contract filtering)
├── integration/
│   ├── BetterShopAPI.java (hook into BetterShop)
│   ├── TownyIntegration.java (region detection, treasury)
│   └── StormcraftIntegration.java (balance checking)
├── storage/
│   ├── ContractDataManager.java (save/load contracts)
│   ├── TransporterDataManager.java (save XP, levels)
│   └── TradePostDataManager.java (save trade post locations)
├── commands/
│   ├── SilkRoadCommand.java (main command)
│   └── AdminCommand.java (admin tools)
└── listeners/
    ├── ShopInteractListener.java (detect shop targeting)
    └── ProtectionListener.java (cargo protection, trade post protection)
```

### Dependencies

- **BetterShop** (soft dependency - enhance if present)
- **Vault** (hard dependency - economy)
- **Towny** or **Towns & Nations** (soft dependency - region detection)
- **Stormcraft** (soft dependency - balance reference)
- **PlaceholderAPI** (soft dependency - placeholders)

### Database/Storage

**Option 1: JSON Files (Simple)**
- `contracts.json` - Active contracts
- `transporters.json` - XP, levels, stats
- `tradeposts.json` - Trade post locations
- `history.json` - Completed contract history

**Option 2: SQLite (Scalable)**
- Better for large servers with many concurrent contracts
- Faster queries for contract browsing
- Transaction safety

**Recommendation:** Start with JSON, migrate to SQLite if needed

---

## Success Metrics

### Player Engagement
- Number of contracts completed per day
- Average transporter level across server
- Percentage of BetterShops with Silk Road enabled

### Economic Health
- Total bounty paid out vs. insurance collected (inflation tracking)
- Average contract bounty
- Contract completion rate (% not expired)

### Balance Indicators
- Average $/min for transporters vs. essence farming
- Contract acceptance time (how long before someone accepts)
- Region popularity (which routes are most common)

---

## Development Phases

### Phase 1: Core System (MVP)
- [ ] BetterShop integration (Silk Road toggle, stock reservation)
- [ ] Contract system (create, accept, deliver)
- [ ] Basic bounty calculation (distance + value)
- [ ] Cargo items (soulbound, single slot)
- [ ] Trade Posts (place, interact, GUI)
- [ ] Simple decay (fixed rate)
- [ ] Insurance system (pay to accept)

### Phase 2: Progression & Polish
- [ ] Adaptive decay (scale with distance)
- [ ] Transporter levels & XP
- [ ] Multiple concurrent contracts
- [ ] Insurance discounts
- [ ] Contract filtering/sorting in GUI
- [ ] Enhanced messaging & notifications

### Phase 3: Advanced Features
- [ ] Towny integration (nation treasuries, region rates)
- [ ] Stormcraft balancing (compare to essence farming)
- [ ] Contract history & statistics
- [ ] Leaderboards
- [ ] Admin tools (view all contracts, force cancel, etc.)

### Phase 4: Expansion (Future)
- [ ] Random events
- [ ] Guilds/companies
- [ ] Express delivery
- [ ] Bulk shipping
- [ ] Route planning UI

---

## Open Questions

1. **Should buyers be able to cancel contracts?**
   - Pro: Gives buyers control if they change mind
   - Con: Wastes transporter time if accepted already
   - **Suggestion:** Allow cancel if not yet accepted; charge small fee if accepted

2. **Should there be a "tip" system?**
   - Buyers can add bonus to bounty to incentivize faster delivery
   - Creates dynamic pricing

3. **Should transporters see buyer/shop owner names?**
   - Anonymous contracts might reduce social pressure
   - Named contracts add community flavor

4. **How to handle multi-world servers?**
   - Limit Silk Road to specific worlds?
   - Calculate distance across worlds (Nether = 8x overworld)?

5. **Should there be NPC transporters (automated)?**
   - Fallback if no players accept contract
   - Much lower bounty, longer delivery time
   - Keeps economy moving but less interesting

---

## Conclusion

Silk Road creates a rich player-driven economy with:
- **Buyers:** Access to goods from any region
- **Shop Owners:** Expanded market reach, more sales
- **Transporters:** Profitable active gameplay, progression system
- **Server:** Dynamic economy, regional trade networks, emergent gameplay

The system is balanced to compete with passive farming (Stormcraft essence) while providing engaging, risk-reward gameplay that encourages exploration and regional interaction.
