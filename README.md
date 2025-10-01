# 🛤️ Silk Road

A cross-regional trading system for Minecraft Paper 1.21.3+ that enables player-driven delivery contracts. Players can order items from distant regions, and transporters earn bounties by delivering goods safely across dangerous territories.

## Overview

Silk Road integrates with **BetterShop** to create a three-party economic system:
- **Buyers** purchase from BetterShops in other regions (Silk Road enabled shops)
- **Contracts** are automatically created for delivery when remote purchases are made
- **Transporters** accept contracts and physically travel to deliver goods, earning bounties
- **Bounties** decay over time but scale with distance to encourage long journeys
- **Insurance** prevents exploitation while requiring transporter investment

## Features

### 🚚 Player-Driven Delivery System
- Accept delivery contracts to earn bounties
- Transport cargo across regions with soulbound items (cannot be lost or stolen)
- Bounty system with adaptive decay (longer journeys decay slower)
- Insurance requirement creates economic commitment

### 📈 Transporter Progression
- **5 Levels**: Novice → Courier → Trader → Merchant → Caravan Master
- Earn XP from distance traveled, contracts completed, and high-value deliveries
- Unlock multiple concurrent contracts and insurance discounts
- Level-based value restrictions prevent new players from abandoning expensive contracts

### 🏪 Trade Post System
- Place trade posts in towns/territories to enable cross-regional trading
- Browse Silk Road-enabled shops from any region
- Accept delivery contracts from a global job board
- Track active deliveries with live bounty updates
- Claim delivered items when they arrive

### 💰 Dynamic Economy
- Server-paid bounties reward active gameplay (configurable)
- Insurance payments go to nation/town treasuries (Towny integration)
- Adaptive bounty calculation based on distance and item value
- Configurable region rates (high-risk regions pay more per block)

### 🛡️ Cargo Protection
- Soulbound cargo items persist through death
- Cannot drop, trade, or store cargo in chests
- Live-updating lore shows current bounty and time remaining
- Virtual item transfer (cargo is just a metadata carrier)

### 🌍 Territory Integration
- **Towny**: Region detection, nation treasury deposits for insurance
- **Towns and Nations**: Alternative territory system support
- Trade posts must be placed in claimed territory (configurable)
- One trade post per town (configurable)

## Installation

### Requirements
- **Paper/Spigot** 1.21.3+
- **Java** 17+
- **Vault** (required)
- **[BetterShop](https://github.com/KyleEdwardDonaldson/BetterShop)** (required - with Silk Road integration)
- An economy plugin (EssentialsX, etc.)
- **Towny** or **Towns and Nations** (optional, for territory features)

### Building from Source (Windows)

1. **Clone or download this repository**
   ```powershell
   cd C:\path\to\repos
   git clone https://github.com/KyleEdwardDonaldson/SilkRoad.git
   cd SilkRoad
   ```

2. **Build with Maven**
   ```powershell
   mvn clean package
   ```
   The compiled JAR will be in `target\silkroad-0.1.0.jar`

3. **Place in plugins folder**
   ```powershell
   copy target\silkroad-0.1.0.jar C:\path\to\server\plugins\
   ```

4. **Restart server**

### BetterShop Integration

Silk Road requires **BetterShop v0.1.0+** with integrated support.

Shop owners can enable Silk Road on their shops using:
```
/shop silkroad enable
```

This allows remote purchases and automatically creates delivery contracts!

## Usage

### Creating a Trade Post

Trade posts are the hub for all Silk Road activities:

```
/tradepost create
```

Look at a **Lectern** block (or Barrel, if configured) and run the command. Requirements:
- Must be in claimed territory (Towny/TaN) unless configured otherwise
- Only one trade post per town by default
- Requires permission: `silkroad.tradepost.create`

### For Shop Owners

Enable Silk Road on your BetterShop to make it available for remote purchase:

```
/shop silkroad enable        # Enable for shop you're looking at
/shop silkroad disable       # Disable for shop you're looking at
/shop silkroad enable all    # Enable for all your shops
/shop silkroad disable all   # Disable for all your shops
```

When a remote buyer purchases from your shop:
- Stock is reserved automatically
- Items are marked "in transit"
- When delivered, payment is added to your shop earnings
- Collect earnings with `/shop collect` as normal

### For Buyers

1. **Visit a Trade Post** (right-click the Lectern/Barrel)
2. **Browse Shops** from other regions
3. **Purchase items** - payment is escrowed until delivery
4. **Wait for delivery** - a transporter will accept the contract
5. **Claim items** at the Trade Post when delivery is complete

### For Transporters

1. **Visit a Trade Post** and browse available delivery jobs
2. **Accept a contract**:
   - Pay insurance (10% of bounty, goes to your nation treasury)
   - Receive a soulbound cargo item in your inventory
3. **Travel to the shop** and pick up the items (right-click shop chest with cargo)
4. **Travel to the delivery location** (destination trade post)
5. **Deliver cargo** (right-click trade post with cargo item)
6. **Receive bounty** and XP instantly!

**Tips for Transporters:**
- Longer journeys pay more and decay slower
- High-risk regions (like Stormlands) pay more per block
- Level up to carry multiple contracts at once
- Higher levels unlock insurance discounts and high-value contracts

### Checking Your Stats

```
/silkroad stats
```

View your transporter level, XP progress, active contracts, and lifetime earnings.

```
/silkroad contracts
```

List all your active delivery contracts.

## Commands

### Player Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/tradepost create` | Create a trade post | `silkroad.tradepost.create` |
| `/tradepost remove` | Remove a trade post | `silkroad.tradepost.use` |
| `/tradepost info` | View trade post info | `silkroad.tradepost.use` |
| `/tradepost list` | List all trade posts | `silkroad.tradepost.use` |
| `/silkroad help` | Show help | `silkroad.use` |
| `/silkroad stats` | View transporter stats | `silkroad.use` |
| `/silkroad contracts` | List active contracts | `silkroad.transport` |

### Admin Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/silkroad admin reload` | Reload configuration | `silkroad.admin.reload` |
| `/silkroad admin contracts` | View all active contracts | `silkroad.admin.contracts` |
| `/silkroad admin cancel <id>` | Cancel a contract | `silkroad.admin.contracts` |

## Configuration

### config.yml

Configure all aspects of the plugin:

**Economy Settings:**
- Server-paid bounties vs. buyer-funded
- Insurance rate (default 10%)
- Insurance refundability

**Bounty Calculation:**
- Region rates ($/block traveled)
- Value multiplier (% of item value)
- Decay rate and minimum duration

**Cargo Settings:**
- Material type (BUNDLE default)
- Protection settings (drop, trade, storage, death)
- Movement slowness effect

**Progression:**
- XP rates for different actions
- Level thresholds and benefits
- Max contracts per level
- Insurance discounts

**Trade Posts:**
- Block type (Lectern or Barrel)
- One per town limit
- Claim requirement
- Protection from breaking

**Integration:**
- BetterShop, Towny, Towns and Nations, Stormcraft
- Treasury preferences

### messages.yml

All player-facing messages using MiniMessage format. Fully customizable!

### Example Region Configuration

```yaml
bounty:
  regionRates:
    stormlands: 0.50    # $0.50 per block (high risk)
    middle: 0.20        # $0.20 per block (moderate)
    safe: 0.05          # $0.05 per block (low risk)
    desert: 0.30
    ice: 0.40
```

## Permissions

### Player Permissions
- `silkroad.use` - Access Trade Posts and browse contracts (default: true)
- `silkroad.transport` - Accept and complete contracts (default: true)
- `silkroad.shop.enable` - Enable Silk Road on own shops (default: true)
- `silkroad.tradepost.create` - Create trade posts (default: true)
- `silkroad.tradepost.use` - Interact with trade posts (default: true)

### Admin Permissions
- `silkroad.admin` - All admin permissions (default: op)
- `silkroad.admin.reload` - Reload configuration (default: op)
- `silkroad.admin.contracts` - View/manage all contracts (default: op)
- `silkroad.admin.bypass` - Bypass level/insurance requirements (default: op)

## How It Works

### Contract Lifecycle

```
1. POSTED      → Contract created, waiting for transporter
2. ACCEPTED    → Transporter accepted, pays insurance, gets cargo
3. IN_TRANSIT  → Cargo picked up from shop, traveling to destination
4. DELIVERED   → Delivered to trade post, bounty paid
5. COMPLETED   → Buyer claimed items
```

**Expiration:** If bounty decays to $0, contract expires, buyer refunded, stock released.

### Bounty Calculation

**Distance Component:**
```
distanceComponent = Σ (blocks_in_region × region_rate)
```

**Value Component:**
```
valueComponent = (shopPrice × quantity) × 0.15
```

**Total Initial Bounty:**
```
initialBounty = distanceComponent + valueComponent
```

**Adaptive Decay:**
```
decayRate = baseDecayRate / (1 + (totalDistance / 1000.0))
```

**Result:** Longer journeys decay slower, encouraging ambitious routes!

### Example Calculation

**Journey:** SafeZone → Stormlands (800 blocks)
- 400 blocks in SafeZone @ $0.05/block = $20
- 400 blocks in Stormlands @ $0.50/block = $200
- Item value: 2 diamonds @ $500 each = $1000
- Value component: $1000 × 0.15 = $150
- **Initial bounty: $370**
- Insurance cost: $37 (10%)
- Decay rate: ~$0.056/sec (bounty lasts ~110 minutes)
- Net transporter profit: $333

### Transporter Levels

| Level | Name | XP Required | Max Contracts | Max Value | Insurance Discount |
|-------|------|-------------|---------------|-----------|-------------------|
| 1 | Novice | 0 | 1 | $500 | 0% |
| 2 | Courier | 500 | 2 | $1,000 | 0% |
| 3 | Trader | 1,500 | 3 | $5,000 | 0% |
| 4 | Merchant | 3,500 | 4 | Unlimited | 5% |
| 5 | Caravan Master | 7,000 | 5 | Unlimited | 10% |

**Earning XP:**
- +10 XP per 100 blocks traveled with cargo
- +50 XP per contract completed
- +25 XP per region crossed
- +100 XP for high-value deliveries (>$1000)

## Anti-Abuse Measures

- **Cargo duplication**: Contract UUID in PersistentDataContainer, single-use validation
- **Insurance fraud**: Non-refundable (configurable)
- **Stock manipulation**: Atomic reservation, shop owner can't remove reserved items
- **Multiple pickups**: Pickup state tracking per contract
- **Fake deliveries**: Validates contract ID, pickup state, stock reservation

## Data Persistence

All data saved to JSON files with auto-save every 30 seconds:
- `contracts.json` - Active contracts with full state
- `transporters.json` - Player XP, levels, stats
- `tradeposts.json` - Trade post locations
- `pending_orders.json` - Delivered items awaiting buyer pickup

Graceful restoration on server restart with contract state preservation.

## Economy Balancing

Silk Road is designed to compete with passive farming (like Stormcraft essence farming):

**Target:** Make transport earnings ~3x passive farming rate

Example:
- Essence farming: $5/minute (passive)
- Medium Silk Road journey: $15-20/minute (active gameplay)

Adjust region rates in config.yml to balance for your server's economy.

## Integration with Other Plugins

### BetterShop (Required)
- Silk Road toggle per shop
- Stock reservation during transit
- Transaction completion on delivery
- Shops visible across all regions

### Towny (Optional)
- Region detection for contract routing
- Nation/town treasury for insurance deposits
- Trade post placement restrictions
- One trade post per town

### Towns and Nations (Optional)
- Alternative to Towny
- Same functionality for territories
- Treasury integration

### Vault (Required)
- All monetary transactions
- Economy wrapper
- Balance checking

### Stormcraft (Optional)
- Economic balancing reference
- Essence farming rate comparison

## Troubleshooting

**"BetterShop plugin not found!"**
- Ensure BetterShop is installed and enabled
- BetterShop must be updated with Silk Road integration (see BetterShopAPI.java)

**"Vault plugin not found!"**
- Install Vault and an economy plugin (EssentialsX, etc.)

**"Trade Posts must be placed in claimed territory!"**
- Place trade posts within Towny/TaN claims
- Or disable `tradePosts.requiresClaim` in config.yml

**Contracts not saving**
- Check console for JSON errors
- Ensure `plugins/SilkRoad/` directory has write permissions
- Auto-save runs every 30 seconds

**Bounty decaying too fast/slow**
- Adjust `bounty.decay.baseRate` in config.yml
- Default: 0.10 $/second
- Adaptive formula scales with distance automatically

## Development

### Building from Source
```bash
git clone <repository-url>
cd SilkRoad
mvn clean package
```

### Dependencies
- Paper API 1.21.3
- Vault API
- Towny API (optional)
- Gson (shaded)

### Project Structure
```
src/main/java/dev/ked/silkroad/
├── SilkRoadPlugin.java           # Main plugin class
├── contracts/                    # Contract system
├── bounty/                       # Bounty calculation & decay
├── cargo/                        # Cargo items & protection
├── transport/                    # Transporter progression
├── tradeposts/                   # Trade post system
├── gui/                          # User interfaces (future)
├── integration/                  # Plugin integrations
├── storage/                      # JSON persistence
├── commands/                     # Command handlers
└── listeners/                    # Event listeners
```

## Roadmap

### Phase 1: Core System (✅ Complete)
- [x] Contract system
- [x] Bounty calculation with adaptive decay
- [x] Cargo items (soulbound)
- [x] Trade posts
- [x] Transporter progression
- [x] Insurance system
- [x] Data persistence

### Phase 2: Polish (In Progress)
- [ ] Trade Post GUIs (shop browser, contract browser, deliveries, pickup)
- [ ] Movement tracking for XP per 100 blocks
- [ ] Cargo lore auto-update task
- [ ] Enhanced messaging & notifications
- [ ] Contract filtering/sorting

### Phase 3: Advanced Features
- [ ] Contract history & statistics
- [ ] Leaderboards (top transporters)
- [ ] Admin tools GUI
- [ ] Dynamic bounty adjustments
- [ ] Express delivery option

### Phase 4: Expansion (Future)
- [ ] Random events (bandit attacks, favorable winds)
- [ ] Transport guilds/companies
- [ ] Bulk shipping
- [ ] Route planning UI
- [ ] Dynmap integration

## Support

- **Issues**: Report bugs or request features on GitHub
- **Documentation**: See `CLAUDE.md` for developer documentation
- **Planning**: See `SILK_ROAD_PLAN.md` for detailed design specification

## License

This plugin is part of the Stormcraft server ecosystem.

## Credits

Created for the **Stormcraft** Minecraft server.

Integrates with:
- **BetterShop** - Player shop system
- **Towny** / **Towns and Nations** - Territory management
- **Vault** - Economy integration
- **Stormcraft** - Server ecosystem

---

**Experience the thrill of cross-regional trade!** 🛤️✨
