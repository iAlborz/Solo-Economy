package com.reazip.economycraft.client;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.shop.ShopDisplay;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.hooks.client.screen.ScreenAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class ShopCategoryOverlay {
    private static final int SLOT_SIZE = 18;
    private static final int COLS = 3;
    private static final int PAD = 6;
    private static final int BALANCE_Y = PAD;
    private static final int GRID_Y = BALANCE_Y + 12;
    private static final int WIDTH = PAD * 2 + COLS * SLOT_SIZE;
    private static final int BORDER = 4;
    private static final int SRC_W = 176;
    private static final int SRC_H = 166;
    private static final int PANEL_GREY = 0xFFC6C6C6;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int SELECTED_FILL = 0xFF7CB342;
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
    private static final ResourceLocation PANEL = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");

    private static boolean registered;
    private static @Nullable Screen bound;
    private static @Nullable Panel panel;
    private static @Nullable String pendingSelected;

    private ShopCategoryOverlay() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientGuiEvent.INIT_POST.register(ShopCategoryOverlay::onInit);
        ClientGuiEvent.RENDER_PRE.register((screen, graphics, mouseX, mouseY, delta) -> {
            refresh(screen);
            return EventResult.pass();
        });
    }

    private static boolean isShop(Screen screen) {
        if (!(screen instanceof InventoryScreen || screen instanceof CraftingScreen)) return false;
        return RecipeBookShopOverlay.isShopMode();
    }

    private static void onInit(Screen screen, ScreenAccess access) {
        if (!(screen instanceof AbstractContainerScreen<?> container) || !(screen instanceof InventoryScreen || screen instanceof CraftingScreen)) {
            if (screen == bound) {
                bound = null;
                panel = null;
            }
            return;
        }
        bound = screen;
        if (pendingSelected == null) pendingSelected = RecipeBookShopOverlay.selectedCategory();
        panel = new Panel(0, 0);
        panel.setTooltipDelay(Duration.ZERO);
        access.addRenderableWidget(panel);
        panel.reload();
        place(container);
        panel.visible = RecipeBookShopOverlay.isShopMode();
    }

    private static void refresh(Screen screen) {
        if (screen != bound || panel == null || !(screen instanceof AbstractContainerScreen<?> container)) return;
        panel.visible = RecipeBookShopOverlay.isShopMode();
        if (!panel.visible) return;
        place(container);
    }

    private static void place(AbstractContainerScreen<?> screen) {
        if (panel == null) return;
        panel.setX(RecipeBookShopOverlay.bookLeft(screen) - WIDTH);
        panel.setY(RecipeBookShopOverlay.bookTop(screen));
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

    private static long currentBalance() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return 0L;
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) return 0L;
        return EconomyCraft.getManager(server).getBalance(minecraft.player.getUUID(), true);
    }

    private static void blit(GuiGraphics graphics, int x, int y, int u, int v, int w, int h) {
        graphics.blit(PANEL, x, y, u, v, w, h);
    }

    private static void tileH(GuiGraphics graphics, int x, int y, int w, int u, int v) {
        int dx = 0;
        while (dx < w) {
            int cw = Math.min(SRC_W - 2 * BORDER, w - dx);
            blit(graphics, x + dx, y, u, v, cw, BORDER);
            dx += cw;
        }
    }

    private static void tileV(GuiGraphics graphics, int x, int y, int h, int u, int v) {
        int dy = 0;
        while (dy < h) {
            int ch = Math.min(SRC_H - 2 * BORDER, h - dy);
            blit(graphics, x, y + dy, u, v, BORDER, ch);
            dy += ch;
        }
    }

    private static void blitPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x + BORDER, y + BORDER, x + w - BORDER, y + h - BORDER, PANEL_GREY);
        blit(graphics, x, y, 0, 0, BORDER, BORDER);
        blit(graphics, x + w - BORDER, y, SRC_W - BORDER, 0, BORDER, BORDER);
        blit(graphics, x, y + h - BORDER, 0, SRC_H - BORDER, BORDER, BORDER);
        blit(graphics, x + w - BORDER, y + h - BORDER, SRC_W - BORDER, SRC_H - BORDER, BORDER, BORDER);
        tileH(graphics, x + BORDER, y, w - 2 * BORDER, BORDER, 0);
        tileH(graphics, x + BORDER, y + h - BORDER, w - 2 * BORDER, BORDER, SRC_H - BORDER);
        tileV(graphics, x, y + BORDER, h - 2 * BORDER, 0, BORDER);
        tileV(graphics, x + w - BORDER, y + BORDER, h - 2 * BORDER, SRC_W - BORDER, BORDER);
    }

    private static final class Panel extends AbstractWidget {
        private List<String> categories = List.of();
        private List<ItemStack> icons = List.of();
        private @Nullable String selected;

        private Panel(int x, int y) {
            super(x, y, WIDTH, GRID_Y + SLOT_SIZE + PAD, Component.literal("Categories"));
        }

        private void reload() {
            PriceRegistry prices = prices();
            ServerPlayer viewer = viewer();
            if (prices == null || viewer == null) {
                categories = List.of();
                icons = List.of();
                selected = null;
                setHeight(GRID_Y + SLOT_SIZE + PAD);
                return;
            }

            categories = ShopDisplay.displayCategories(prices);
            List<ItemStack> next = new ArrayList<>(categories.size());
            for (String cat : categories) {
                next.add(ShopDisplay.createCategoryIcon(cat, ShopDisplay.primarySource(cat), prices, viewer, true));
            }
            icons = next;
            selected = resolveSelected();
            if (selected != null && RecipeBookShopOverlay.selectedCategory() == null) {
                RecipeBookShopOverlay.selectCategory(selected);
            }
            int rows = Math.max(1, (categories.size() + COLS - 1) / COLS);
            setHeight(GRID_Y + rows * SLOT_SIZE + PAD);
        }

        private @Nullable String resolveSelected() {
            if (pendingSelected != null) {
                for (String cat : categories) {
                    if (cat.equalsIgnoreCase(pendingSelected)) return cat;
                    if (ShopDisplay.formatCategoryTitle(cat).equalsIgnoreCase(pendingSelected.replace('_', ' '))) {
                        return cat;
                    }
                    for (String source : ShopDisplay.sourceCategories(cat)) {
                        if (source.equalsIgnoreCase(pendingSelected)) return cat;
                    }
                }
            }
            return categories.isEmpty() ? null : categories.get(0);
        }

        private @Nullable Component hoverNameAt(int mouseX, int mouseY) {
            int index = indexAt(mouseX, mouseY);
            if (index < 0 || index >= categories.size()) return null;
            PriceRegistry prices = prices();
            String cat = categories.get(index);
            String name = prices == null ? ShopDisplay.formatCategoryTitle(cat)
                    : ShopDisplay.getCategoryName(prices, cat, cat);
            return Component.literal(name);
        }

        private int indexAt(double mouseX, double mouseY) {
            int localX = (int) mouseX - getX() - PAD;
            int localY = (int) mouseY - getY() - GRID_Y;
            if (localX < 0 || localY < 0) return -1;
            int col = localX / SLOT_SIZE;
            int row = localY / SLOT_SIZE;
            if (col < 0 || col >= COLS) return -1;
            int index = row * COLS + col;
            return index < categories.size() ? index : -1;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!this.active || !this.visible || button != 0 || !this.isMouseOver(mouseX, mouseY)) {
                return false;
            }
            int index = indexAt(mouseX, mouseY);
            if (index < 0 || index >= categories.size()) return true;
            String cat = categories.get(index);
            selected = cat;
            pendingSelected = cat;
            RecipeBookShopOverlay.selectCategory(cat);
            return true;
        }

        @Override
        public void playDownSound(SoundManager soundManager) {}

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (!visible) return;
            blitPanel(graphics, getX(), getY(), WIDTH, getHeight());
            Minecraft minecraft = Minecraft.getInstance();
            Component balance = Component.literal(EconomyCraft.formatMoney(currentBalance()));
            graphics.drawString(minecraft.font, balance,
                    getX() + (WIDTH - minecraft.font.width(balance)) / 2, getY() + BALANCE_Y, TITLE_COLOR, false);
            for (int i = 0; i < categories.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;
                int slotX = getX() + PAD + col * SLOT_SIZE;
                int slotY = getY() + GRID_Y + row * SLOT_SIZE;
                graphics.blitSprite(SLOT, slotX, slotY, SLOT_SIZE, SLOT_SIZE);
                boolean active = categories.get(i).equals(selected);
                if (active) {
                    graphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, SELECTED_FILL);
                }
                if (i < icons.size() && !icons.get(i).isEmpty()) {
                    graphics.renderItem(icons.get(i), slotX + 1, slotY + 1);
                }
            }
            Component hover = hoverNameAt(mouseX, mouseY);
            if (hover != null) {
                graphics.renderTooltip(minecraft.font, hover, mouseX, mouseY);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
