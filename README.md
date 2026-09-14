<p align="center">
  <img src="common/src/main/resources/assets/economycraft/logo.png" width="128" alt="Solo Economy">
</p>

[Solo Economy](README.md) · [Prices](Prices.md) · [Config](Config.md)

# Solo Economy

A solo survival shop and money system for Fabric (and NeoForge).
Requires Architectury API.

Based on [EconomyCraft](https://github.com/PhilipB06/EconomyCraft) by ReaZip.

---

## Setup

1. Put the jar in your instance `mods` folder with Fabric API and Architectury API.
2. Open inventory or a crafting table, open the recipe book, and click the yellow bag next to Search.
3. Operators can run `/eco admin` to edit the shop, settings, and balances. See [Config](Config.md).

Default configuration works without manual changes. Default buy and sell values are on [Prices](Prices.md).

---

## The shop

Open inventory or a crafting table, then open the recipe book. Next to Search are **check** (craftable only), **X** (all recipes), and the **yellow bag** (shop). Shop mode shows buyable items in the recipe grid; the category card sits to the left of the book. The card on the right is balance plus **Insta Sell**.

| Control           | Description                                                                                                                                                                                  |
|-------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Insta Sell**    | Drop a stack on the slot to sell it at the shop sell price.                                                                                                                                 |
| **Shop**          | Recipe-book grid of buyable items. Type in the book's search box to filter. Left click buys 1, shift-click buys a stack. Categories are on the left card. |

Balance is always on the right card. There is no `/eco`, `/shop`, `/bal`, `/sell`, or `/worth` command.
