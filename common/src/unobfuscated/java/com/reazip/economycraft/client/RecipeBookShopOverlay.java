package com.reazip.economycraft.client;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.shop.ShopDisplay;
import com.reazip.economycraft.shop.ShopUi;
import com.reazip.economycraft.util.MenuUiSupport;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientScreenInputEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.hooks.client.screen.ScreenAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class RecipeBookShopOverlay {
    private static final int GRID_X = 11;
    private static final int GRID_Y = 31;
    private static final int CELL = 25;
    private static final int ARROW_W = 12;
    private static final int ARROW_H = 17;
    private static final int PAGE_COLOR = 0xFFFFFFFF;
    private static final int SELECTED_BG = 0xFF8B8B8B;
    private static final int BTN = 16;
    private static final int BTN_GAP = 1;
    private static final int BAR_W = BTN * 3 + BTN_GAP * 2;
    private static final Identifier RECIPE_SLOT = Identifier.withDefaultNamespace("recipe_book/slot_craftable");
    private static final Identifier CHECK = Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "check");
    private static final Identifier CROSS = Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "cross");
    private static final Identifier BAG = Identifier.fromNamespaceAndPath(EconomyCraft.MOD_ID, "insta_sell");
    private static final WidgetSprites PAGE_FORWARD = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/page_forward"),
            Identifier.withDefaultNamespace("recipe_book/page_forward_highlighted"));
    private static final WidgetSprites PAGE_BACK = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/page_backward"),
            Identifier.withDefaultNamespace("recipe_book/page_backward_highlighted"));
    private static final Component CRAFTABLE = Component.literal("Craftable only");
    private static final Component ALL = Component.literal("All recipes");
    private static final Component SHOP = Component.literal("Shop");
    private static final Component SHOP_SEARCH_HINT =
            Component.literal("Search Shop").withStyle(EditBox.SEARCH_HINT_STYLE);
    private static final Component RECIPE_SEARCH_HINT =
            Component.translatable("gui.recipebook.search_hint").withStyle(EditBox.SEARCH_HINT_STYLE);

    private static boolean registered;
    private static @Nullable Screen bound;
    private static @Nullable ModeBar modeBar;
    private static @Nullable ShopPage shopPage;
    private static FilterMode mode = FilterMode.ALL;
    private static @Nullable String selectedCategory;
    private static @Nullable String pendingQuery;
    private static boolean openBook;
    private static boolean recipesHidden;
    private static long lastHandledNs;

    private RecipeBookShopOverlay() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientGuiEvent.INIT_POST.register(RecipeBookShopOverlay::onInit);
        ClientGuiEvent.RENDER_PRE.register((screen, graphics, mouseX, mouseY, delta) -> {
            refresh(screen, mouseX, mouseY);
            return EventResult.pass();
        });
        ClientGuiEvent.RENDER_POST.register((screen, graphics, mouseX, mouseY, delta) -> {
            paintForeground(screen, graphics, mouseX, mouseY);
            paintHoverTooltips(screen, graphics, mouseX, mouseY);
        });
        ClientScreenInputEvent.MOUSE_CLICKED_PRE.register(RecipeBookShopOverlay::onMouseClicked);
        ClientTickEvent.CLIENT_POST.register(RecipeBookShopOverlay::onTick);
        registerFabricForeground();
    }

    public static void onAfterForeground(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        paintForeground(screen, graphics, mouseX, mouseY);
    }

    private static void registerFabricForeground() {
        try {
            Class<?> eventsClass = Class.forName("net.fabricmc.fabric.api.client.screen.v1.ScreenEvents");
            Class<?> afterInitType = Class.forName("net.fabricmc.fabric.api.client.screen.v1.ScreenEvents$AfterInit");
            Object afterInitEvent = eventsClass.getField("AFTER_INIT").get(null);
            Object listener = Proxy.newProxyInstance(
                    afterInitType.getClassLoader(),
                    new Class<?>[] { afterInitType },
                    (proxy, method, args) -> {
                        if (args != null && args.length >= 2 && args[1] instanceof Screen screen) {
                            hookForeground(eventsClass, screen);
                        }
                        return null;
                    });
            invokeRegister(afterInitEvent, listener);
        } catch (Throwable ignored) {
        }
    }

    private static void hookForeground(Class<?> eventsClass, Screen screen) {
        if (!isTarget(screen)) return;
        try {
            Class<?> callback = Class.forName("net.fabricmc.fabric.api.client.screen.v1.ScreenEvents$AfterForeground");
            Object event = eventsClass.getMethod("afterForeground", Screen.class).invoke(null, screen);
            Object listener = Proxy.newProxyInstance(
                    callback.getClassLoader(),
                    new Class<?>[] { callback },
                    (proxy, method, args) -> {
                        if (args != null && args.length >= 4
                                && args[0] instanceof Screen open
                                && args[1] instanceof GuiGraphicsExtractor graphics) {
                            paintForeground(open, graphics, (Integer) args[2], (Integer) args[3]);
                        }
                        return null;
                    });
            invokeRegister(event, listener);
        } catch (Throwable ignored) {
        }
    }

    private static void invokeRegister(Object event, Object listener) throws Exception {
        for (Method method : event.getClass().getMethods()) {
            if ("register".equals(method.getName()) && method.getParameterCount() == 1) {
                method.invoke(event, listener);
                return;
            }
        }
    }

    private static void paintForeground(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (screen != bound) return;
        refresh(screen, mouseX, mouseY);
        if (shopPage != null && shopPage.visible) {
            shopPage.paint(graphics, mouseX, mouseY);
        }
        if (modeBar != null && modeBar.visible) {
            modeBar.paint(graphics, mouseX, mouseY);
        }
    }

    private static void paintHoverTooltips(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (screen != bound) return;
        boolean queued = shopPage != null && shopPage.visible
                && shopPage.queueHoverTooltip(graphics, mouseX, mouseY);
        queued |= ShopCategoryOverlay.queueHoverTooltip(graphics, mouseX, mouseY);
        if (queued) graphics.extractDeferredElements(mouseX, mouseY, 0f);
    }

    public static boolean isShopMode() {
        return mode == FilterMode.SHOP && bookVisible(bound);
    }

    public static @Nullable String selectedCategory() {
        return selectedCategory;
    }

    public static void selectCategory(String category) {
        selectedCategory = category;
        if (shopPage != null) shopPage.reload();
    }

    public static int bookLeft(Screen screen) {
        Object book = recipeBook(screen);
        Integer x = invokeInt(book, "getXOrigin");
        if (x != null) return x;
        if (screen instanceof AbstractContainerScreen<?> container) {
            return intField(container, "leftPos") - bookW();
        }
        return 0;
    }

    public static int bookTop(Screen screen) {
        Object book = recipeBook(screen);
        Integer y = invokeInt(book, "getYOrigin");
        if (y != null) return y;
        if (screen instanceof AbstractContainerScreen<?> container) {
            return intField(container, "topPos");
        }
        return 0;
    }

    private static boolean isTarget(Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CraftingScreen;
    }

    private static void onTick(Minecraft minecraft) {
        if (minecraft.player == null) return;
        ShopUi.Pending pending = ShopUi.consumeOpen(minecraft.player.getUUID());
        if (pending != null) {
            mode = FilterMode.SHOP;
            selectedCategory = pending.category();
            pendingQuery = pending.query();
            openBook = true;
            Screen screen = minecraft.gui.screen();
            if (isTarget(screen)) {
                applyOpen(screen, pendingQuery);
            } else {
                minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
            }
        }
        if (isShopMode()) hideVanillaRecipes(recipeBook(bound), true);
        Screen screen = minecraft.gui.screen();
        if (screen == bound) refresh(screen, 0, 0);
    }

    private static EventResult onMouseClicked(Minecraft client, Screen screen, MouseButtonEvent event, boolean doubleClick) {
        if (screen != bound || !bookVisible(screen)) return EventResult.pass();
        double mouseX = event.x();
        double mouseY = event.y();
        if (modeBar != null && modeBar.visible && modeBar.isMouseOver(mouseX, mouseY)) {
            if (event.button() == 0) modeBar.clickAt(mouseX, mouseY);
            return EventResult.interruptFalse();
        }
        if (mode == FilterMode.SHOP && shopPage != null && shopPage.visible) {
            hideVanillaRecipes(recipeBook(screen), true);
            if (shopPage.isMouseOver(mouseX, mouseY)) {
                if (event.button() == 0) shopPage.handleClick(mouseX, mouseY, event.hasShiftDown());
                return EventResult.interruptFalse();
            }
        }
        return EventResult.pass();
    }

    private static void onInit(Screen screen, ScreenAccess access) {
        if (!isTarget(screen)) {
            if (screen == bound) {
                bound = null;
                modeBar = null;
                shopPage = null;
                recipesHidden = false;
            }
            return;
        }
        bound = screen;
        Object book = recipeBook(screen);
        if (book == null) return;

        if (openBook) {
            mode = FilterMode.SHOP;
        } else {
            mode = isFiltering(book) ? FilterMode.CRAFTABLE : FilterMode.ALL;
        }

        AbstractWidget filter = filterButton(book);
        modeBar = new ModeBar(filter);
        modeBar.setTooltipDelay(Duration.ZERO);
        access.addRenderableWidget(modeBar);

        shopPage = new ShopPage();
        shopPage.setTooltipDelay(Duration.ZERO);
        access.addRenderableWidget(shopPage);

        if (openBook) applyOpen(screen, pendingQuery);
        refresh(screen, 0, 0);
    }

    private static void applyOpen(Screen screen, @Nullable String query) {
        Object book = recipeBook(screen);
        if (book == null) return;
        if (!isVisible(book)) invoke(book, "toggleVisibility");
        openBook = false;
        pendingQuery = null;
        if (query != null && !query.isBlank()) {
            EditBox search = searchBox(book);
            if (search != null) search.setValue(query);
        }
        if (shopPage != null) shopPage.reload();
        applyVanillaFilter(book);
    }

    private static void refresh(Screen screen, int mouseX, int mouseY) {
        if (screen != bound || modeBar == null || shopPage == null) return;
        Object book = recipeBook(screen);
        if (book == null || !isVisible(book)) {
            modeBar.visible = false;
            shopPage.visible = false;
            AbstractWidget filter = filterButton(book);
            if (filter != null) {
                filter.active = true;
                filter.visible = true;
            }
            recipesHidden = false;
            return;
        }
        AbstractWidget filter = filterButton(book);
        if (filter != null) {
            filter.active = false;
            filter.visible = false;
            filter.setX(-2000);
        }
        int left = bookLeft(screen);
        int top = bookTop(screen);
        int width = bookW();
        boolean shop = mode == FilterMode.SHOP;
        EditBox search = searchBox(book);
        if (search != null) {
            search.setX(left + 8);
            search.setY(top + 13);
            search.setWidth(width - 16 - BAR_W - 3);
            search.setHint(shop ? SHOP_SEARCH_HINT : RECIPE_SEARCH_HINT);
        }
        modeBar.setX(left + width - 8 - BAR_W);
        modeBar.setY(top + 12);
        modeBar.setHeight(BTN);
        modeBar.visible = true;
        modeBar.active = true;
        modeBar.syncTooltip(mouseX, mouseY);

        shopPage.visible = shop;
        shopPage.active = shop;
        shopPage.setX(left);
        shopPage.setY(top + GRID_Y);
        shopPage.setWidth(width);
        shopPage.setHeight(bookH() - GRID_Y);
        if (shop) {
            hideVanillaRecipes(book, true);
            shopPage.update();
        } else {
            hideVanillaRecipes(book, false);
        }
    }

    private static void selectMode(FilterMode next) {
        if (mode == next || !claimClick()) return;
        mode = next;
        applyVanillaFilter(recipeBook(bound));
        if (shopPage != null && mode == FilterMode.SHOP) shopPage.reload();
    }

    private static void applyVanillaFilter(Object book) {
        if (book == null || mode == FilterMode.SHOP) return;
        boolean want = mode == FilterMode.CRAFTABLE;
        if (isFiltering(book) != want) {
            invoke(book, "toggleFiltering");
            invoke(book, "sendUpdateSettings");
        }
        invoke(book, "updateCollections", new Class<?>[] { boolean.class, boolean.class }, false, want);
    }

    private static void hideVanillaRecipes(Object book, boolean hide) {
        Object page = field(book, "recipeBookPage");
        if (hide) {
            invoke(page, "setInvisible");
            setVisibleList(field(page, "buttons"), false);
            setVisible(field(page, "forwardButton"), false);
            setVisible(field(page, "backButton"), false);
            setVisibleList(field(book, "tabButtons"), false);
            recipesHidden = true;
            return;
        }
        if (!recipesHidden) return;
        invoke(page, "updateButtonsForPage");
        invoke(page, "updateArrowButtons");
        setVisibleList(field(book, "tabButtons"), true);
        recipesHidden = false;
    }

    private static void setVisibleList(Object list, boolean visible) {
        if (!(list instanceof List<?> widgets)) return;
        for (Object widget : widgets) setVisible(widget, visible);
    }

    private static void setVisible(Object widget, boolean visible) {
        if (widget instanceof AbstractWidget w) w.visible = visible;
    }

    private static boolean bookVisible(@Nullable Screen screen) {
        if (screen == null) return false;
        Object book = recipeBook(screen);
        return book != null && isVisible(book);
    }

    private static @Nullable Object recipeBook(Screen screen) {
        if (screen == null) return null;
        Object named = fieldNamed(screen, "recipeBookComponent");
        if (named != null) return named;
        Object getter = invoke(screen, "getRecipeBookComponent");
        if (getter != null) return getter;
        return fieldOfRecipeBook(screen);
    }

    private static boolean isVisible(Object book) {
        Object value = invoke(book, "isVisible");
        return value instanceof Boolean b && b;
    }

    private static boolean isFiltering(Object book) {
        Object value = invoke(book, "isFiltering");
        if (value instanceof Boolean b) return b;
        AbstractWidget filter = filterButton(book);
        if (filter == null) return false;
        Object triggered = invoke(filter, "isStateTriggered");
        if (triggered instanceof Boolean b) return b;
        Object cycle = invoke(filter, "getValue");
        return cycle instanceof Boolean b && b;
    }

    private static @Nullable AbstractWidget filterButton(Object book) {
        Object value = field(book, "filterButton");
        return value instanceof AbstractWidget w ? w : null;
    }

    private static @Nullable EditBox searchBox(Object book) {
        Object value = field(book, "searchBox");
        return value instanceof EditBox box ? box : null;
    }

    private static String searchQuery() {
        Object book = recipeBook(bound);
        EditBox box = searchBox(book);
        if (box == null) return "";
        String value = box.getValue();
        return value == null ? "" : value.trim();
    }

    private static @Nullable PriceRegistry prices() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? null : EconomyCraft.getManager(server).getPrices();
    }

    private static @Nullable ServerPlayer viewer() {
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) return null;
        return server.getPlayerList().getPlayer(minecraft.player.getUUID());
    }

    private static void sendBuy(PriceRegistry.PriceEntry entry, boolean bulk) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        int amount = bulk ? Math.max(1, entry.stack()) : 1;
        connection.sendCommand("eco buy " + entry.key() + (amount > 1 ? " " + amount : ""));
    }

    private static boolean claimClick() {
        long now = System.nanoTime();
        if (now - lastHandledNs < 50_000_000L) return false;
        lastHandledNs = now;
        return true;
    }

    private static @Nullable Object invoke(Object target, String name) {
        return invoke(target, name, new Class<?>[0]);
    }

    private static @Nullable Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        if (target == null) return null;
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Method method = type.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static @Nullable Integer invokeInt(Object target, String name) {
        Object value = invoke(target, name);
        return value instanceof Integer i ? i : null;
    }

    private static @Nullable Object field(Object target, String name) {
        if (target == null) return null;
        return fieldNamed(target, name);
    }

    private static @Nullable Object fieldNamed(Object target, String name) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static @Nullable Object fieldOfRecipeBook(Object target) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value != null && isRecipeBookClass(value.getClass())) return value;
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean isRecipeBookClass(Class<?> type) {
        while (type != null && type != Object.class) {
            if (type.getName().endsWith(".RecipeBookComponent") || type.getSimpleName().equals("RecipeBookComponent")) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static int intField(Object target, String name) {
        Object value = fieldNamed(target, name);
        return value instanceof Integer i ? i : 0;
    }

    private static int bookW() {
        return xlInt("imageWidth", 147);
    }

    private static int bookH() {
        return xlInt("imageHeight", 166);
    }

    private static int cols() {
        return xlInt("columns", 5);
    }

    private static int rows() {
        int columns = cols();
        return columns == 0 ? 4 : perPage() / columns;
    }

    private static int perPage() {
        return xlInt("itemsPerPage", 20);
    }

    private static int pageBackX() {
        return xlInt("pageBackX", 38);
    }

    private static int pageForwardX() {
        return xlInt("pageForwardX", 93);
    }

    private static int pageButtonY() {
        return xlInt("pageButtonY", 137);
    }

    private static int pageTextX() {
        return xlInt("pageTextX", 73);
    }

    private static int pageTextY() {
        return xlInt("pageTextY", 141);
    }

    private static int xlInt(String name, int fallback) {
        try {
            Class<?> layout = Class.forName("recipebookxl.RecipeBookLayout");
            Object value = layout.getMethod(name).invoke(null);
            if (value instanceof Integer i) return i;
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private enum FilterMode { CRAFTABLE, ALL, SHOP }

    private static final class ModeBar extends AbstractWidget {
        private ModeBar(@Nullable AbstractWidget filter) {
            super(filter == null ? 0 : filter.getX() + filter.getWidth() - BAR_W,
                    filter == null ? 0 : filter.getY(),
                    BAR_W, filter == null ? BTN : filter.getHeight(), SHOP);
        }

        private void clickAt(double mouseX, double mouseY) {
            int index = indexAt(mouseX, mouseY);
            if (index == 0) selectMode(FilterMode.CRAFTABLE);
            else if (index == 1) selectMode(FilterMode.ALL);
            else if (index == 2) selectMode(FilterMode.SHOP);
        }

        private int indexAt(double mouseX, double mouseY) {
            if (!isMouseOver(mouseX, mouseY)) return -1;
            int local = (int) mouseX - getX();
            int stride = BTN + BTN_GAP;
            int index = local / stride;
            if (local % stride >= BTN) return -1;
            return index >= 0 && index < 3 ? index : -1;
        }

        private void syncTooltip(int mouseX, int mouseY) {
            int index = indexAt(mouseX, mouseY);
            if (index == 0) setTooltip(Tooltip.create(CRAFTABLE));
            else if (index == 1) setTooltip(Tooltip.create(ALL));
            else if (index == 2) setTooltip(Tooltip.create(SHOP));
            else setTooltip(null);
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            clickAt(event.x(), event.y());
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        }

        private void paint(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!visible) return;
            paintBtn(graphics, 0, FilterMode.CRAFTABLE, CHECK);
            paintBtn(graphics, 1, FilterMode.ALL, CROSS);
            paintBtn(graphics, 2, FilterMode.SHOP, BAG);
        }

        private void paintBtn(GuiGraphicsExtractor graphics, int index, FilterMode forMode, Identifier icon) {
            int x = getX() + index * (BTN + BTN_GAP);
            int y = getY();
            if (mode == forMode) {
                graphics.fill(x, y, x + BTN, y + getHeight(), SELECTED_BG);
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x + 2, y + 2, BTN - 4, getHeight() - 4);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static final class ShopPage extends AbstractWidget {
        private List<PriceRegistry.PriceEntry> entries = List.of();
        private List<ItemStack> stacks = List.of();
        private int page;
        private String lastQuery = "";
        private @Nullable String lastCategory;

        private ShopPage() {
            super(0, 0, bookW(), bookH() - GRID_Y, SHOP);
        }

        private void reload() {
            PriceRegistry prices = prices();
            ServerPlayer viewer = viewer();
            if (prices == null || viewer == null) {
                entries = List.of();
                stacks = List.of();
                page = 0;
                return;
            }
            if (selectedCategory == null) {
                List<String> cats = ShopDisplay.displayCategories(prices);
                selectedCategory = cats.isEmpty() ? null : cats.get(0);
            }
            String query = searchQuery();
            List<PriceRegistry.PriceEntry> list = ShopDisplay.buyableForDisplay(prices, selectedCategory);
            if (!query.isEmpty()) {
                String q = query.toLowerCase();
                List<PriceRegistry.PriceEntry> filtered = new ArrayList<>();
                for (PriceRegistry.PriceEntry entry : list) {
                    ItemStack stack = ShopDisplay.createDisplayStack(entry, viewer);
                    if (MenuUiSupport.matchesSearch(stack, query)
                            || ShopDisplay.shopItemName(entry, stack).toLowerCase().contains(q)
                            || entry.id().asString().toLowerCase().contains(q)
                            || entry.key().toLowerCase().contains(q)) {
                        filtered.add(entry);
                    }
                }
                list = filtered;
            }
            entries = ShopDisplay.inVanillaOrder(list);
            List<ItemStack> next = new ArrayList<>(entries.size());
            for (PriceRegistry.PriceEntry entry : entries) {
                next.add(ShopDisplay.createDisplayStack(entry, viewer));
            }
            stacks = next;
            lastQuery = query;
            lastCategory = selectedCategory;
            int pages = Math.max(1, (entries.size() + perPage() - 1) / perPage());
            if (page >= pages) page = pages - 1;
            if (page < 0) page = 0;
        }

        private void update() {
            String query = searchQuery();
            if (!query.equals(lastQuery) || (selectedCategory != null && !selectedCategory.equals(lastCategory))) {
                reload();
            }
            int maxPage = pages() - 1;
            if (page > maxPage) page = maxPage;
            if (page < 0) page = 0;
        }

        private List<Component> hoverLinesAt(int mouseX, int mouseY) {
            int idx = itemIndex(indexAt(mouseX, mouseY));
            if (idx < 0 || idx >= entries.size()) return List.of();
            PriceRegistry.PriceEntry entry = entries.get(idx);
            PriceRegistry prices = prices();
            long buy = prices == null ? entry.unitBuy()
                    : EconomyCraft.getManager(Minecraft.getInstance().getSingleplayerServer())
                    .getEffectiveBuyPrice(entry);
            String name = idx < stacks.size()
                    ? ShopDisplay.shopItemName(entry, stacks.get(idx))
                    : entry.id().path();
            return List.of(
                    Component.literal(name),
                    Component.literal("Buy " + EconomyCraft.formatMoney(buy)));
        }

        private boolean queueHoverTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            List<Component> lines = hoverLinesAt(mouseX, mouseY);
            if (lines.isEmpty()) return false;
            graphics.setComponentTooltipForNextFrame(Minecraft.getInstance().font, lines, mouseX, mouseY);
            return true;
        }

        private int pages() {
            return Math.max(1, (entries.size() + perPage() - 1) / perPage());
        }

        private int itemIndex(int slot) {
            if (slot < 0) return -1;
            return page * perPage() + slot;
        }

        private int indexAt(double mouseX, double mouseY) {
            int localX = (int) mouseX - getX() - GRID_X;
            int localY = (int) mouseY - getY();
            int gridW = cols() * CELL;
            int gridH = rows() * CELL;
            if (localX < 0 || localY < 0 || localX >= gridW || localY >= gridH) return -1;
            int col = localX / CELL;
            int row = localY / CELL;
            return row * cols() + col;
        }

        private boolean overBack(double mouseX, double mouseY) {
            int x = getX() + pageBackX();
            int y = getY() + pageButtonY() - GRID_Y;
            return mouseX >= x && mouseX < x + ARROW_W && mouseY >= y && mouseY < y + ARROW_H && page > 0;
        }

        private boolean overForward(double mouseX, double mouseY) {
            int x = getX() + pageForwardX();
            int y = getY() + pageButtonY() - GRID_Y;
            return mouseX >= x && mouseX < x + ARROW_W && mouseY >= y && mouseY < y + ARROW_H && page + 1 < pages();
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            handleClick(event.x(), event.y(), event.hasShiftDown());
        }

        private void handleClick(double mouseX, double mouseY, boolean bulk) {
            if (overBack(mouseX, mouseY)) {
                if (!claimClick()) return;
                page--;
                return;
            }
            if (overForward(mouseX, mouseY)) {
                if (!claimClick()) return;
                page++;
                return;
            }
            int slot = indexAt(mouseX, mouseY);
            int idx = itemIndex(slot);
            if (idx < 0 || idx >= entries.size()) return;
            if (!claimClick()) return;
            sendBuy(entries.get(idx), bulk);
        }

        @Override
        public void playDownSound(SoundManager soundManager) {}

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        }

        private void paint(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!visible) return;
            Minecraft minecraft = Minecraft.getInstance();
            int columns = cols();
            int start = page * perPage();
            for (int i = 0; i < perPage(); i++) {
                int idx = start + i;
                if (idx >= entries.size()) break;
                int col = i % columns;
                int row = i / columns;
                int x = getX() + GRID_X + col * CELL;
                int y = getY() + row * CELL;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, RECIPE_SLOT, x, y, CELL, CELL);
                if (idx < stacks.size() && !stacks.get(idx).isEmpty()) {
                    graphics.item(stacks.get(idx), x + 4, y + 4);
                }
            }
            int arrowY = getY() + pageButtonY() - GRID_Y;
            if (page > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PAGE_BACK.get(true, overBack(mouseX, mouseY)),
                        getX() + pageBackX(), arrowY, ARROW_W, ARROW_H);
            }
            if (page + 1 < pages()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PAGE_FORWARD.get(true, overForward(mouseX, mouseY)),
                        getX() + pageForwardX(), arrowY, ARROW_W, ARROW_H);
            }
            if (pages() > 1) {
                Component text = Component.translatable("gui.recipebook.page", page + 1, pages());
                int width = minecraft.font.width(text);
                graphics.text(minecraft.font, text, getX() + pageTextX() - width / 2, getY() + pageTextY() - GRID_Y, PAGE_COLOR, true);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {}
    }
}
