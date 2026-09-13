package com.reazip.economycraft.shop;

import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.EconomySources;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.SellService;
import com.reazip.economycraft.util.EconomySounds;
import com.reazip.economycraft.util.LiveSearchable;
import com.reazip.economycraft.util.MenuUiSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopUi {
    public static final int SEARCH_QUERY_MAX_LENGTH = MenuUiSupport.SEARCH_QUERY_MAX_LENGTH;
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private ShopUi() {}

    public record Pending(@Nullable String category, @Nullable String query) {}

    public static void requestOpen(ServerPlayer player, @Nullable String category) {
        requestOpen(player, category, null);
    }

    public static void requestOpen(ServerPlayer player, @Nullable String category, @Nullable String query) {
        PENDING.put(player.getUUID(), new Pending(category, query));
        if (player.containerMenu != player.inventoryMenu && !(player.containerMenu instanceof CraftingMenu)) {
            player.closeContainer();
        }
    }

    public static @Nullable Pending consumeOpen(UUID playerId) {
        return PENDING.remove(playerId);
    }

    public static void open(ServerPlayer player, EconomyManager eco) {
        requestOpen(player, null);
    }

    public static void open(ServerPlayer player, EconomyManager eco, @Nullable String category) {
        requestOpen(player, category);
    }

    public static void applyLiveSearch(ServerPlayer player, EconomyManager eco, String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() > SEARCH_QUERY_MAX_LENGTH) {
            trimmed = trimmed.substring(0, SEARCH_QUERY_MAX_LENGTH);
        }
        if (LiveSearchable.apply(player, trimmed)) return;
        if (trimmed.isEmpty()) {
            requestOpen(player, null);
            return;
        }
        requestOpen(player, null, trimmed);
    }

    public static boolean clearLiveSearch(ServerPlayer player) {
        return LiveSearchable.apply(player, "");
    }

    public static int buy(ServerPlayer player, String id, int amount) {
        if (!EconomyConfig.get().shopEnabled) {
            player.sendSystemMessage(Component.literal("Shop is disabled.").withStyle(ChatFormatting.RED));
            return 0;
        }
        EconomyManager eco = EconomyCraft.getManager(player.level().getServer());
        PriceRegistry prices = eco.getPrices();
        PriceRegistry.PriceEntry entry = prices.findBuyable(id);
        if (entry == null) {
            EconomySounds.failure(player);
            player.sendSystemMessage(Component.literal("That item is not in the shop.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        int qty = amount > 1 ? Math.max(1, entry.stack()) : 1;
        handlePurchase(entry, qty, eco, prices, player);
        return 1;
    }

    private static void handlePurchase(PriceRegistry.PriceEntry entry, int amount, EconomyManager eco,
                                       PriceRegistry prices, ServerPlayer viewer) {
        if (entry.unitBuy() <= 0 || !prices.isCategoryEnabled(entry.category())) {
            EconomySounds.failure(viewer);
            viewer.sendSystemMessage(Component.literal("This item cannot be purchased.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        ItemStack base = ShopDisplay.createDisplayStack(entry, viewer);
        if (base.isEmpty()) {
            EconomySounds.failure(viewer);
            viewer.sendSystemMessage(Component.literal("Item unavailable.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        long unitPrice = eco.getEffectiveBuyPrice(entry);
        Long total = ShopDisplay.safeMultiply(unitPrice, amount);
        if (total == null) {
            EconomySounds.failure(viewer);
            viewer.sendSystemMessage(Component.literal("Price too large.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        long balance = eco.getBalance(viewer.getUUID(), true);
        if (balance < total) {
            EconomySounds.failure(viewer);
            viewer.sendSystemMessage(Component.literal("Not enough balance.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        if (!canFit(viewer, base, amount)) {
            EconomySounds.failure(viewer);
            viewer.sendSystemMessage(Component.literal("Not enough inventory space.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        String detail = EconomyCraft.describeItem(amount, base.getHoverName().getString());
        if (!eco.removeMoney(viewer.getUUID(), total, EconomySources.SHOP_PURCHASE, detail).successful()) {
            EconomySounds.failure(viewer);
            viewer.sendSystemMessage(Component.literal("Not enough balance.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        giveToPlayer(viewer, base, amount);

        EconomySounds.success(viewer);
        viewer.sendSystemMessage(Component.literal(
                        "Purchased " + amount + "x " + base.getHoverName().getString() +
                                " for " + EconomyCraft.formatMoney(total))
                .withStyle(ChatFormatting.GREEN));
    }

    private static boolean canFit(ServerPlayer viewer, ItemStack base, int amount) {
        int remaining = amount;
        int max = base.getMaxStackSize();
        Inventory inv = viewer.getInventory();
        for (int i = 0; i < SellService.MAIN_INVENTORY_SLOTS && remaining > 0; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) {
                remaining -= max;
            } else if (ItemStack.isSameItemSameComponents(slot, base)) {
                remaining -= Math.max(0, Math.min(max, slot.getMaxStackSize()) - slot.getCount());
            }
        }
        return remaining <= 0;
    }

    private static void giveToPlayer(ServerPlayer viewer, ItemStack base, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(base.getMaxStackSize(), remaining);
            ItemStack stack = base.copyWithCount(give);
            if (!viewer.getInventory().add(stack) && !stack.isEmpty()) {
                viewer.drop(stack, false);
            }
            remaining -= give;
        }
    }
}
