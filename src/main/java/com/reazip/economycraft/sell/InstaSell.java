package com.reazip.economycraft.sell;

import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.EconomySources;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.SellService;
import com.reazip.economycraft.util.EconomySounds;
import com.reazip.economycraft.util.MenuUiSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class InstaSell {
    private InstaSell() {}

    public static void sellCarried(ServerPlayer player) {
        AbstractContainerMenu menu = inventoryMenu(player);
        if (menu == null) return;

        ItemStack stack = menu.getCarried();
        if (stack.isEmpty()) return;

        sellStack(player, stack);

        if (stack.isEmpty()) menu.setCarried(ItemStack.EMPTY);
        menu.broadcastFullState();
    }

    /** Sells the whole stack in one of the player's own inventory or hotbar slots (control/command-click). */
    public static void sellSlot(ServerPlayer player, int slotIndex) {
        AbstractContainerMenu menu = inventoryMenu(player);
        if (menu == null || slotIndex < 0 || slotIndex >= menu.slots.size()) return;

        Slot slot = menu.slots.get(slotIndex);
        if (slot.container != player.getInventory() || slot.getContainerSlot() >= Inventory.INVENTORY_SIZE) return;

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;

        sellStack(player, stack);

        slot.setChanged();
        menu.broadcastFullState();
    }

    private static @Nullable AbstractContainerMenu inventoryMenu(ServerPlayer player) {
        if (!EconomyConfig.get().sellEnabled) {
            EconomySounds.failure(player);
            player.sendSystemMessage(MenuUiSupport.line("Selling is disabled.", ChatFormatting.RED));
            return null;
        }
        AbstractContainerMenu menu = player.containerMenu;
        return menu instanceof InventoryMenu || menu instanceof CraftingMenu ? menu : null;
    }

    /** Sells {@code stack} in place: whatever is sold is removed from it. */
    private static void sellStack(ServerPlayer player, ItemStack stack) {
        EconomyManager eco = EconomyCraft.getManager(player.level().getServer());
        PriceRegistry prices = eco.getPrices();
        if (SellService.sellableResolved(prices, stack) == null) {
            EconomySounds.failure(player);
            player.sendSystemMessage(MenuUiSupport.line("This item cannot be sold.", ChatFormatting.RED));
            return;
        }

        Long unitSell = prices.getUnitSell(stack);
        if (unitSell == null) {
            EconomySounds.failure(player);
            player.sendSystemMessage(MenuUiSupport.line("This item cannot be sold.", ChatFormatting.RED));
            return;
        }

        // Worn items go straight to the shop at their reduced price; open orders expect items in full condition.
        boolean worn = stack.isDamageableItem() && stack.getDamageValue() > 0;
        SellService.SaleSplit split = worn
                ? new SellService.SaleSplit(0, 0, stack.getCount())
                : SellService.sellHandWithRouting(eco, player, stack, stack.getCount(), unitSell);
        int sold = split.orderGiven();
        long payout = split.orderPayout();
        boolean balanceBlocked = false;

        if (split.serverRemaining() > 0) {
            Long potential = safeMultiply(unitSell, split.serverRemaining());
            if (potential == null) {
                balanceBlocked = true;
            } else {
                String detail = EconomyCraft.describeItem(split.serverRemaining(), stack.getHoverName().getString());
                var result = eco.addMoney(player.getUUID(), potential, EconomySources.SHOP_SALE, detail);
                if (result.successful()) {
                    sold += split.serverRemaining();
                    payout += potential;
                    stack.shrink(split.serverRemaining());
                } else {
                    balanceBlocked = true;
                }
            }
        }

        if (sold > 0) {
            EconomySounds.success(player);
            player.sendSystemMessage(Component.literal("Sold " + sold + " item" + (sold == 1 ? "" : "s")
                            + " for " + EconomyCraft.formatMoney(payout)
                            + (split.orderGiven() > 0 ? " (" + split.orderGiven() + " to open orders for a better price)" : "")
                            + ".")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }

        EconomySounds.failure(player);
        if (balanceBlocked) {
            player.sendSystemMessage(MenuUiSupport.line("Your balance is too high to receive the payout.", ChatFormatting.RED));
        }
    }

    private static Long safeMultiply(long value, int count) {
        try {
            return Math.multiplyExact(value, count);
        } catch (ArithmeticException ex) {
            return null;
        }
    }
}
