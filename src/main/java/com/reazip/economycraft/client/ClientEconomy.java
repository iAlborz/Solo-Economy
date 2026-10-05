package com.reazip.economycraft.client;

import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.net.EconomyPackets;
import com.reazip.economycraft.net.EconomyPackets.BalanceSync;
import com.reazip.economycraft.net.EconomyPackets.CatalogSync;
import com.reazip.economycraft.net.EconomyPackets.CategoryInfo;
import com.reazip.economycraft.net.EconomyPackets.ItemInfo;
import com.reazip.economycraft.net.EconomyPackets.OpenShop;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What the server last told this client about money and the shop. Works the same on a LAN guest as on the host. */
@Environment(EnvType.CLIENT)
public final class ClientEconomy {
    public record PendingOpen(@Nullable String category, @Nullable String query) {}

    private static volatile long balance;
    private static volatile String partners = "";

    private static int catalogHash;
    private static int version;
    private static boolean haveCatalog;
    private static boolean shopEnabled = true;
    private static boolean sellEnabled = true;
    private static List<CategoryInfo> categories = List.of();
    private static List<ItemInfo> items = List.of();
    private static Map<String, Long> sells = Map.of();
    private static @Nullable PendingOpen pendingOpen;

    private ClientEconomy() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(BalanceSync.TYPE, (packet, ctx) -> {
            balance = packet.balance();
            partners = packet.partners();
        });
        ClientPlayNetworking.registerGlobalReceiver(CatalogSync.TYPE, (packet, ctx) -> {
            catalogHash = packet.hash();
            if (packet.unchanged()) return;
            shopEnabled = packet.shopEnabled();
            sellEnabled = packet.sellEnabled();
            categories = packet.categories();
            items = packet.items();
            Map<String, Long> sellMap = new HashMap<>();
            packet.sells().forEach(s -> sellMap.put(s.itemId(), s.unitSell()));
            sells = sellMap;
            haveCatalog = true;
            version++;
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenShop.TYPE, (packet, ctx) ->
                pendingOpen = new PendingOpen(blankToNull(packet.category()), blankToNull(packet.query())));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> requestCatalog());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void reset() {
        balance = 0;
        partners = "";
        catalogHash = 0;
        haveCatalog = false;
        version++;
        categories = List.of();
        items = List.of();
        sells = Map.of();
        pendingOpen = null;
    }

    public static void requestCatalog() {
        send(new EconomyPackets.RequestCatalog(haveCatalog ? catalogHash : 0));
    }

    public static void send(CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload);
    }

    public static long balance() { return balance; }
    /** Names of the players you share finances with, or empty when the balance is yours alone. */
    public static String partners() { return partners; }
    /** Changes whenever the catalog does, so screens know to rebuild. */
    public static int version() { return version; }
    public static boolean hasCatalog() { return haveCatalog; }
    public static boolean shopEnabled() { return shopEnabled; }
    public static boolean sellEnabled() { return sellEnabled; }
    public static List<CategoryInfo> categories() { return categories; }
    public static List<ItemInfo> items() { return items; }

    /** What one of the carried item sells for, or null if it can't be sold. */
    public static @Nullable Long unitSell(ItemStack stack) {
        if (!sellEnabled || stack.isEmpty()) return null;
        Long full = sells.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        return full == null ? null : PriceRegistry.wornValue(full, stack);
    }

    public static @Nullable PendingOpen consumePendingOpen() {
        PendingOpen open = pendingOpen;
        pendingOpen = null;
        return open;
    }

    private static @Nullable String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
