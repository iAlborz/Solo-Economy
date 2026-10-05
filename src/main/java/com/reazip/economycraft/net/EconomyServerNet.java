package com.reazip.economycraft.net;

import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.bank.JointAccounts;
import com.reazip.economycraft.bank.SendMoneyUi;
import com.reazip.economycraft.net.EconomyPackets.BalanceSync;
import com.reazip.economycraft.net.EconomyPackets.Buy;
import com.reazip.economycraft.net.EconomyPackets.CatalogSync;
import com.reazip.economycraft.net.EconomyPackets.CategoryInfo;
import com.reazip.economycraft.net.EconomyPackets.ItemInfo;
import com.reazip.economycraft.net.EconomyPackets.OpenMenu;
import com.reazip.economycraft.net.EconomyPackets.RequestCatalog;
import com.reazip.economycraft.net.EconomyPackets.SearchQuery;
import com.reazip.economycraft.net.EconomyPackets.SellInfo;
import com.reazip.economycraft.shop.ShopDisplay;
import com.reazip.economycraft.shop.ShopUi;
import com.reazip.economycraft.util.LiveSearchable;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server half of the inventory shop: keeps each client's balance and catalog current and runs its actions. */
public final class EconomyServerNet {
    private static final int BALANCE_CHECK_TICKS = 5;
    private static final Map<UUID, BalanceSync> LAST_SENT = new ConcurrentHashMap<>();

    private EconomyServerNet() {}

    public static void register() {
        EconomyPackets.register();

        ServerPlayNetworking.registerGlobalReceiver(RequestCatalog.TYPE, (packet, ctx) ->
                ctx.server().execute(() -> sendCatalog(ctx.player(), packet.knownHash())));
        ServerPlayNetworking.registerGlobalReceiver(Buy.TYPE, (packet, ctx) ->
                ctx.server().execute(() -> ShopUi.buy(ctx.player(), packet.key(), packet.bulk() ? 2 : 1)));
        ServerPlayNetworking.registerGlobalReceiver(EconomyPackets.InstaSell.TYPE, (packet, ctx) ->
                ctx.server().execute(() -> com.reazip.economycraft.sell.InstaSell.sellCarried(ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(EconomyPackets.QuickSell.TYPE, (packet, ctx) ->
                ctx.server().execute(() -> com.reazip.economycraft.sell.InstaSell.sellSlot(ctx.player(), packet.slot())));
        ServerPlayNetworking.registerGlobalReceiver(SearchQuery.TYPE, (packet, ctx) ->
                ctx.server().execute(() -> LiveSearchable.apply(ctx.player(), packet.query())));
        ServerPlayNetworking.registerGlobalReceiver(OpenMenu.TYPE, (packet, ctx) -> ctx.server().execute(() -> {
            switch (packet.menu()) {
                case SEND -> SendMoneyUi.open(ctx.player());
                case JOINT -> JointAccounts.onButton(ctx.player());
            }
        }));

        ServerTickEvents.END_SERVER_TICK.register(EconomyServerNet::syncBalances);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> LAST_SENT.remove(handler.getPlayer().getUUID()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_SENT.remove(handler.getPlayer().getUUID()));
    }

    private static void syncBalances(MinecraftServer server) {
        if (server.getTickCount() % BALANCE_CHECK_TICKS != 0) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        EconomyManager eco = EconomyCraft.getManager(server);
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            BalanceSync now = new BalanceSync(eco.getBalance(id, true), JointAccounts.partnerNames(eco, id));
            if (!now.equals(LAST_SENT.put(id, now))) {
                ServerPlayNetworking.send(player, now);
            }
        }
    }

    private static void sendCatalog(ServerPlayer player, int knownHash) {
        EconomyManager eco = EconomyCraft.getManager(player.level().getServer());
        PriceRegistry prices = eco.getPrices();
        EconomyConfig config = EconomyConfig.get();

        List<CategoryInfo> categories = new ArrayList<>();
        List<ItemInfo> items = new ArrayList<>();
        int hash = 17;
        for (String category : ShopDisplay.displayCategories(prices)) {
            String categoryName = ShopDisplay.getCategoryName(prices, category, category);
            categories.add(new CategoryInfo(category, categoryName,
                    ShopDisplay.createCategoryIcon(category, ShopDisplay.primarySource(category), prices, player, true)));
            hash = 31 * hash + category.hashCode();
            hash = 31 * hash + categoryName.hashCode();

            Map<String, PriceRegistry.PriceEntry> inCategory = new LinkedHashMap<>();
            for (PriceRegistry.PriceEntry entry : ShopDisplay.buyableForDisplay(prices, category)) {
                inCategory.putIfAbsent(entry.key(), entry);
            }
            for (PriceRegistry.PriceEntry entry : ShopDisplay.inVanillaOrder(new ArrayList<>(inCategory.values()))) {
                ItemStack stack = ShopDisplay.createDisplayStack(entry, player);
                if (stack.isEmpty()) continue;
                String name = ShopDisplay.shopItemName(entry, stack);
                long buy = eco.getEffectiveBuyPrice(entry);
                items.add(new ItemInfo(entry.key(), entry.id().asString(), category, name, stack, buy));
                hash = 31 * hash + entry.key().hashCode();
                hash = 31 * hash + Long.hashCode(buy);
            }
        }

        List<SellInfo> sells = new ArrayList<>();
        for (PriceRegistry.PriceEntry entry : prices.allEntries()) {
            if (entry.customItem() != null || entry.unitSell() <= 0 || !prices.isCategoryEnabled(entry.category())) continue;
            sells.add(new SellInfo(entry.id().asString(), entry.unitSell()));
            hash = 31 * hash + entry.id().asString().hashCode();
            hash = 31 * hash + Long.hashCode(entry.unitSell());
        }
        hash = 31 * hash + (config.shopEnabled ? 1 : 0);
        hash = 31 * hash + (config.sellEnabled ? 1 : 0);

        if (hash == knownHash) {
            ServerPlayNetworking.send(player, CatalogSync.unchanged(hash));
            return;
        }
        ServerPlayNetworking.send(player, new CatalogSync(hash, false, config.shopEnabled, config.sellEnabled,
                categories, items, sells));
    }
}
