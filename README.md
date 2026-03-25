# Bazaar Helper — Hypixel Skyblock Fabric Mod

A Minecraft **1.21.11 Fabric mod** that overlays a smart GUI panel on top of the Hypixel Skyblock
Bazaar order screens, showing your active buy orders and letting you **Pickup & Sell** with one
click by automating the Booster Cookie sell flow.

---

## Features

- **Auto-detected overlay** — opens automatically when you view `/bz` → *Your Bazaar Orders* or
  *Manage Orders*.
- **Order cards** — each card shows:
  - Item name
  - Filled / Total quantity (e.g. `4,200 / 5,000`) with a **MAX** badge when fully filled
  - Total value of filled items in coins
- **Pickup & Sell button** — one click per order:
  1. Claims all items from the selected buy order slot
  2. Opens the SkyBlock Menu → Booster Cookie
  3. Sells all items via the Cookie's sell menu
  4. Repeats until the order is empty
  5. Prints a fancy client-side completion banner in chat
- **Live refresh** — order list re-parsed every second while the screen is open
- **No server-side interaction** — all actions are standard client → server GUI clicks, identical
  to what a player does manually. No macros, no packet injection beyond vanilla slot clicks.

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | ≥ 0.16.5 |
| Fabric API | 0.102.0+1.21.1 |
| Java | 21+ |

---

## Build

```bash
# Clone / download the project
cd skyblock-bazaar-mod

# Build the mod jar
./gradlew build

# Output jar is at:
# build/libs/bazaar-helper-1.0.0.jar
```

Drop the jar into your `.minecraft/mods/` folder alongside Fabric API.

---

## How to Use

1. Join Hypixel and load a Skyblock profile.
2. Open the Bazaar via the NPC or `/bz`.
3. Navigate to **Your Bazaar Orders** or **Manage Orders**.
4. The **Bazaar Helper** panel appears to the right of the chest GUI.
5. Each buy order with items ready to claim shows a **⬆ Pickup & Sell** button.
6. Click the button — the automation runs:
   - Claims items from that order
   - Opens your SkyBlock Menu
   - Clicks the Booster Cookie
   - Sells all claimed items
   - Repeats if more items remain
7. When the order is emptied, a completion banner prints in chat.

---

## Project Structure

```
src/main/java/com/bazaarhelper/
├── client/
│   └── BazaarHelperClient.java       ← Mod entry point, event registration
├── gui/
│   ├── BazaarOrderPanel.java         ← Renders the overlay panel & buttons
│   └── BazaarOverlayManager.java     ← Attaches overlay to the correct screens
├── mixin/
│   └── HandledScreenMixin.java       ← Mixin for screen open/close hooks
└── util/
    ├── BazaarOrder.java              ← Data model for a single buy order
    ├── BazaarOrderParser.java        ← Parses chest GUI slots → BazaarOrder list
    ├── BazaarOrderTracker.java       ← Shared state singleton
    └── PickupSellAutomation.java     ← State machine for the pickup→sell flow
```

---

## Important Notes

- **Hypixel ToS** — This mod performs only standard GUI interactions (slot clicks) that replicate
  what a player does manually. It does NOT: inject packets outside vanilla slot-click flow, read
  memory, or automate movement. Always review Hypixel's rules before using automation.
- **Booster Cookie slot detection** — The sell slot name is matched against `"Sell Inventory Now"`,
  `"Sell All"`, or `"Sell"`. If Hypixel changes the slot name, update `PickupSellAutomation.java`
  `handleClickingSell()`.
- **Order slot layout** — Bazaar "Manage Order" sub-menus use slot 13 for the Claim button by
  default. If this changes, update `handleClickingClaim()` fallback names.

---

## Configuration (planned)

- Toggle overlay visibility with a keybind
- Adjust automation speed (tick delays)
- Filter orders below a minimum fill amount

---

## License

MIT — free to use, modify, and distribute.
