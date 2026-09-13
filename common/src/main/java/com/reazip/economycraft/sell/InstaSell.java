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
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

public final class InstaSell {
    private InstaSell() {}

    public static void sellCarried(ServerPlayer player) {
        if (!EconomyConfig.get().sellEnabled) {
            EconomySounds.failure(player);
            player.sendSystemMessage(MenuUiSupport.line("Selling is disabled.", ChatFormatting.RED));
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (!(menu instanceof InventoryMenu) && !(menu instanceof CraftingMenu)) return;

        ItemStack stack = menu.getCarried();
        if (stack.isEmpty()) return;

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

        SellService.SaleSplit split = SellService.sellHandWithRouting(eco, player, stack, stack.getCount(), unitSell);
        int sold = split.orderGiven();
        long payout = split.orderPayout();
        boolean limitBlocked = false;
        boolean balanceBlocked = false;

        if (split.serverRemaining() > 0) {
            Long potential = safeMultiply(unitSell, split.serverRemaining());
            if (potential == null) {
                balanceBlocked = true;
            } else if (EconomyConfig.get().dailySellLimit > 0
                    && potential > eco.getDailySellRemaining(player.getUUID())) {
                limitBlocked = true;
            } else {
                String detail = EconomyCraft.describeItem(split.serverRemaining(), stack.getHoverName().getString());
                var result = eco.addMoney(player.getUUID(), potential, EconomySources.SHOP_SALE, detail);
                if (result.successful()) {
                    if (EconomyConfig.get().dailySellLimit > 0) {
                        eco.tryRecordDailySell(player.getUUID(), potential);
                    }
                    sold += split.serverRemaining();
                    payout += potential;
                    stack.shrink(split.serverRemaining());
                } else {
                    balanceBlocked = true;
                }
            }
        }

        if (stack.isEmpty()) menu.setCarried(ItemStack.EMPTY);
        menu.broadcastFullState();

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
        if (limitBlocked) {
            player.sendSystemMessage(MenuUiSupport.line("Daily sell limit reached.", ChatFormatting.RED));
        } else if (balanceBlocked) {
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
