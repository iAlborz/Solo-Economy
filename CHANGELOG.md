### New
- **The inventory shop now works for every player**, not just the host. The server sends each player their balance and the shop, and buys, sales and searches go back as network messages.
- **Joint accounts:** click **Joint** on the balance card to invite everyone else online. Each player who accepts has their balance added to one shared balance, and every sale, purchase and reward then changes everyone's balance. Click **Joint** again to leave; the shared balance is split evenly.
- **Send money** to another player from the balance card.
- **Quick sell:** in shop mode, hold Control or Command and click a stack in your inventory to sell it.
- **Worn tools and armor can be sold** for their price scaled by remaining durability, rounded up to the next dollar.
- **Search covers every category** instead of only the selected tab.
- About 435 items that used to be sell-only can now be bought (iron bars and chains, fences, doors, buttons, banners, candles, ores and more), priced from the shop's usual buy/sell pattern. Waystone items, lower-level enchanted books and potion variants stay sell-only.

### Removed
- The daily sell limit.
- The `/eco buy`, `/eco search` and `/eco instasell` commands (the inventory UI uses network messages instead). `/eco joint accept|decline` exists only for the chat invitation links.

### Breaking
- Fabric 26.2 only. NeoForge and Minecraft 26.1 / 1.21.x builds are removed.
- Architectury API is no longer required. The only dependency is Fabric API.
- Removed the optional Text Placeholder API integration (`%economycraft:...%` placeholders).

### Features
- Added optional dynamic shop pricing.
- The Sell UI can now sell shulker boxes.
- Added in-game transactions viewer.
- Orders and auction listings now expire after a configurable time.
- Added `max_active_orders_per_player` and `max_active_auctions_per_player` config options (`0` = unlimited), overridable per player from the admin Players menu.

### Improvements
- Paginated menus now use recipe-book style page controls in the gap above Inventory. They stay hidden when there is only one page.
- Menus with a Back action now use Minecraft's transferable-list unselect arrow next to the title instead of a barrier in the footer.
- The shop, auction house, orders, admin shop, item picker, and player picker GUIs now have a recipe-book style search field on the right of the title bar. Typing filters in place; `/eco search` with no query clears it.
- New orders now reserve payment upfront.

### Fixes
- Fixed a possible crash/corruption from the TAB balance placeholder.
- Fixed some player names getting permanently stuck as unresolved.