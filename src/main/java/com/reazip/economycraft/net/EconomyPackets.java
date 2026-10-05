package com.reazip.economycraft.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Every message between the client UI and the server. Registered on both sides. */
public final class EconomyPackets {
    private EconomyPackets() {}

    private static final int CATALOG_MAX_BYTES = 4 * 1024 * 1024;

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("economycraft", path));
    }

    public static void register() {
        var s2c = PayloadTypeRegistry.clientboundPlay();
        s2c.register(BalanceSync.TYPE, BalanceSync.CODEC);
        s2c.registerLarge(CatalogSync.TYPE, CatalogSync.CODEC, CATALOG_MAX_BYTES);
        s2c.register(OpenShop.TYPE, OpenShop.CODEC);

        var c2s = PayloadTypeRegistry.serverboundPlay();
        c2s.register(RequestCatalog.TYPE, RequestCatalog.CODEC);
        c2s.register(Buy.TYPE, Buy.CODEC);
        c2s.register(InstaSell.TYPE, InstaSell.CODEC);
        c2s.register(QuickSell.TYPE, QuickSell.CODEC);
        c2s.register(SearchQuery.TYPE, SearchQuery.CODEC);
        c2s.register(OpenMenu.TYPE, OpenMenu.CODEC);
    }

    // ---- server -> client

    /** {@code partners} is empty, or the names of the players you share finances with. */
    public record BalanceSync(long balance, String partners) implements CustomPacketPayload {
        public static final Type<BalanceSync> TYPE = typeOf("balance");
        public static final StreamCodec<RegistryFriendlyByteBuf, BalanceSync> CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeVarLong(v.balance());
                    buf.writeUtf(v.partners());
                },
                buf -> new BalanceSync(buf.readVarLong(), buf.readUtf()));

        @Override
        public Type<BalanceSync> type() {
            return TYPE;
        }
    }

    public record CategoryInfo(String key, String name, ItemStack icon) {}

    public record ItemInfo(String key, String itemId, String category, String name, ItemStack stack, long buy) {}

    public record SellInfo(String itemId, long unitSell) {}

    /**
     * The shop as one player sees it. When {@code unchanged} is set the client already holds this
     * version (same {@code hash}) and the lists are empty.
     */
    public record CatalogSync(int hash, boolean unchanged, boolean shopEnabled, boolean sellEnabled,
                              List<CategoryInfo> categories, List<ItemInfo> items,
                              List<SellInfo> sells) implements CustomPacketPayload {
        public static final Type<CatalogSync> TYPE = typeOf("catalog");
        public static final StreamCodec<RegistryFriendlyByteBuf, CatalogSync> CODEC = StreamCodec.of(
                CatalogSync::write, CatalogSync::read);

        public static CatalogSync unchanged(int hash) {
            return new CatalogSync(hash, true, true, true, List.of(), List.of(), List.of());
        }

        private static void write(RegistryFriendlyByteBuf buf, CatalogSync v) {
            buf.writeInt(v.hash());
            buf.writeBoolean(v.unchanged());
            if (v.unchanged()) return;
            buf.writeBoolean(v.shopEnabled());
            buf.writeBoolean(v.sellEnabled());
            buf.writeVarInt(v.categories().size());
            for (CategoryInfo c : v.categories()) {
                buf.writeUtf(c.key());
                buf.writeUtf(c.name());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, c.icon());
            }
            buf.writeVarInt(v.items().size());
            for (ItemInfo i : v.items()) {
                buf.writeUtf(i.key());
                buf.writeUtf(i.itemId());
                buf.writeUtf(i.category());
                buf.writeUtf(i.name());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, i.stack());
                buf.writeVarLong(i.buy());
            }
            buf.writeVarInt(v.sells().size());
            for (SellInfo s : v.sells()) {
                buf.writeUtf(s.itemId());
                buf.writeVarLong(s.unitSell());
            }
        }

        private static CatalogSync read(RegistryFriendlyByteBuf buf) {
            int hash = buf.readInt();
            if (buf.readBoolean()) return unchanged(hash);
            boolean shop = buf.readBoolean();
            boolean sell = buf.readBoolean();
            int categoryCount = buf.readVarInt();
            List<CategoryInfo> categories = new ArrayList<>(categoryCount);
            for (int n = 0; n < categoryCount; n++) {
                categories.add(new CategoryInfo(buf.readUtf(), buf.readUtf(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));
            }
            int itemCount = buf.readVarInt();
            List<ItemInfo> items = new ArrayList<>(itemCount);
            for (int n = 0; n < itemCount; n++) {
                items.add(new ItemInfo(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarLong()));
            }
            int sellCount = buf.readVarInt();
            List<SellInfo> sells = new ArrayList<>(sellCount);
            for (int n = 0; n < sellCount; n++) {
                sells.add(new SellInfo(buf.readUtf(), buf.readVarLong()));
            }
            return new CatalogSync(hash, false, shop, sell, categories, items, sells);
        }

        @Override
        public Type<CatalogSync> type() {
            return TYPE;
        }
    }

    /** Asks the client to open the inventory in shop mode, optionally on a category or search. */
    public record OpenShop(String category, String query) implements CustomPacketPayload {
        public static final Type<OpenShop> TYPE = typeOf("open_shop");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenShop> CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeUtf(v.category());
                    buf.writeUtf(v.query());
                },
                buf -> new OpenShop(buf.readUtf(), buf.readUtf()));

        @Override
        public Type<OpenShop> type() {
            return TYPE;
        }
    }

    // ---- client -> server

    public record RequestCatalog(int knownHash) implements CustomPacketPayload {
        public static final Type<RequestCatalog> TYPE = typeOf("request_catalog");
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestCatalog> CODEC = StreamCodec.of(
                (buf, v) -> buf.writeInt(v.knownHash()),
                buf -> new RequestCatalog(buf.readInt()));

        @Override
        public Type<RequestCatalog> type() {
            return TYPE;
        }
    }

    public record Buy(String key, boolean bulk) implements CustomPacketPayload {
        public static final Type<Buy> TYPE = typeOf("buy");
        public static final StreamCodec<RegistryFriendlyByteBuf, Buy> CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeUtf(v.key());
                    buf.writeBoolean(v.bulk());
                },
                buf -> new Buy(buf.readUtf(), buf.readBoolean()));

        @Override
        public Type<Buy> type() {
            return TYPE;
        }
    }

    /** Sells whatever the player is carrying on the cursor. */
    public record InstaSell() implements CustomPacketPayload {
        public static final Type<InstaSell> TYPE = typeOf("insta_sell");
        public static final StreamCodec<RegistryFriendlyByteBuf, InstaSell> CODEC = StreamCodec.unit(new InstaSell());

        @Override
        public Type<InstaSell> type() {
            return TYPE;
        }
    }

    /** Sells the whole stack in one inventory slot ({@code slot} is the index in the open menu). */
    public record QuickSell(int slot) implements CustomPacketPayload {
        public static final Type<QuickSell> TYPE = typeOf("quick_sell");
        public static final StreamCodec<RegistryFriendlyByteBuf, QuickSell> CODEC = StreamCodec.of(
                (buf, v) -> buf.writeVarInt(v.slot()),
                buf -> new QuickSell(buf.readVarInt()));

        @Override
        public Type<QuickSell> type() {
            return TYPE;
        }
    }

    public record SearchQuery(String query) implements CustomPacketPayload {
        public static final Type<SearchQuery> TYPE = typeOf("search");
        public static final StreamCodec<RegistryFriendlyByteBuf, SearchQuery> CODEC = StreamCodec.of(
                (buf, v) -> buf.writeUtf(v.query()),
                buf -> new SearchQuery(buf.readUtf()));

        @Override
        public Type<SearchQuery> type() {
            return TYPE;
        }
    }

    /** Opens one of the server-driven menus: {@code SEND} or {@code JOINT}. */
    public record OpenMenu(Menu menu) implements CustomPacketPayload {
        public enum Menu { SEND, JOINT }

        public static final Type<OpenMenu> TYPE = typeOf("open_menu");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenMenu> CODEC = StreamCodec.of(
                (buf, v) -> buf.writeVarInt(v.menu().ordinal()),
                buf -> new OpenMenu(Menu.values()[Math.clamp(buf.readVarInt(), 0, Menu.values().length - 1)]));

        @Override
        public Type<OpenMenu> type() {
            return TYPE;
        }
    }
}
