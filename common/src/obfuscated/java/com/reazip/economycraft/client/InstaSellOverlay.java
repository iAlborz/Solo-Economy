package com.reazip.economycraft.client;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.PriceRegistry;
import com.reazip.economycraft.SellService;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.hooks.client.screen.ScreenAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.time.Duration;

@Environment(EnvType.CLIENT)
public final class InstaSellOverlay {
    private static final int SLOT_SIZE = 18;
    private static final int PAD = 6;
    private static final int BALANCE_Y = PAD;
    private static final int TITLE_DIVIDER_Y = BALANCE_Y + 10;
    private static final int TITLE_Y = TITLE_DIVIDER_Y + 4;
    private static final int SLOT_Y = TITLE_Y + 11;
    private static final int WIDTH = 72;
    private static final int HEIGHT = SLOT_Y + SLOT_SIZE + PAD;
    private static final int BORDER = 4;
    private static final int SRC_W = 176;
    private static final int SRC_H = 166;
    private static final int TEX = 256;
    private static final int PANEL_GREY = 0xFFC6C6C6;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int DIVIDER_COLOR = 0xFF8B8B8B;
    private static final Component TITLE = Component.literal("Insta Sell");
    private static final Component EMPTY_HINT = Component.literal("Drop items here to sell");
    private static final Component CANNOT_SELL = Component.literal("This item cannot be sold.")
            .withStyle(ChatFormatting.RED);
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
    private static final ResourceLocation PANEL = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final @Nullable Field LEFT_POS = intField(AbstractContainerScreen.class, "leftPos");
    private static final @Nullable Field TOP_POS = intField(AbstractContainerScreen.class, "topPos");
    private static final @Nullable Field IMAGE_WIDTH = intField(AbstractContainerScreen.class, "imageWidth");

    private static boolean registered;
    private static @Nullable Screen bound;
    private static @Nullable Panel panel;

    private InstaSellOverlay() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientGuiEvent.INIT_POST.register(InstaSellOverlay::onInit);
        ClientGuiEvent.RENDER_PRE.register((screen, graphics, mouseX, mouseY, delta) -> {
            refresh(screen, mouseX, mouseY);
            return EventResult.pass();
        });
    }

    private static boolean isTarget(Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CraftingScreen;
    }

    private static void onInit(Screen screen, ScreenAccess access) {
        if (!(screen instanceof AbstractContainerScreen<?> container) || !isTarget(screen)) {
            if (screen == bound) {
                bound = null;
                panel = null;
            }
            return;
        }
        bound = screen;
        panel = new Panel(0, 0);
        panel.setTooltipDelay(Duration.ZERO);
        access.addRenderableWidget(panel);
        place(container);
        panel.update(container.getMenu().getCarried(), 0, 0);
    }

    private static void refresh(Screen screen, int mouseX, int mouseY) {
        if (screen != bound || panel == null || !(screen instanceof AbstractContainerScreen<?> container)) return;
        place(container);
        panel.update(container.getMenu().getCarried(), mouseX, mouseY);
    }

    private static void place(AbstractContainerScreen<?> screen) {
        if (panel == null) return;
        panel.setX(intValue(LEFT_POS, screen) + intValue(IMAGE_WIDTH, screen));
        panel.setY(intValue(TOP_POS, screen));
    }

    private static void sendCommand(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.sendCommand(command);
    }

    private static Component tooltipFor(ItemStack carried) {
        if (carried.isEmpty()) return EMPTY_HINT;

        PriceRegistry prices = prices();
        if (prices == null) return carried.getHoverName();

        Long unit = SellService.sellableResolved(prices, carried) == null ? null : prices.getUnitSell(carried);
        Long total = unit == null ? null : safeMultiply(unit, carried.getCount());
        if (total == null) return CANNOT_SELL;

        return Component.literal(carried.getHoverName().getString() + "\n")
                .append(Component.literal(EconomyCraft.formatMoney(total)).withStyle(ChatFormatting.GREEN));
    }

    private static @Nullable PriceRegistry prices() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? null : EconomyCraft.getManager(server).getPrices();
    }

    private static long currentBalance() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return 0L;
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) return 0L;
        return EconomyCraft.getManager(server).getBalance(minecraft.player.getUUID(), true);
    }

    private static @Nullable Long safeMultiply(long value, int count) {
        try {
            return Math.multiplyExact(value, count);
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private static void blit(GuiGraphics graphics, int x, int y, float u, float v, int w, int h) {
        graphics.blit(PANEL, x, y, (int) u, (int) v, w, h);
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

    private static void divider(GuiGraphics graphics, int panelX, int y) {
        graphics.fill(panelX + BORDER + 2, y, panelX + WIDTH - BORDER - 2, y + 1, DIVIDER_COLOR);
    }

    private static int intValue(@Nullable Field field, Object target) {
        if (field == null) return 0;
        try {
            return field.getInt(target);
        } catch (IllegalAccessException ignored) {
            return 0;
        }
    }

    private static @Nullable Field intField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException ignored) {
            return null;
        }
    }

    private static final class Panel extends AbstractWidget {
        private @Nullable ItemStack lastCarried;
        private @Nullable Hover lastHover;
        private long lastBalance = Long.MIN_VALUE;

        private Panel(int x, int y) {
            super(x, y, WIDTH, HEIGHT, TITLE);
        }

        private void update(ItemStack carried, int mouseX, int mouseY) {
            Hover hover = hoverAt(mouseX, mouseY);
            long balance = currentBalance();
            if (lastHover == hover && lastBalance == balance && lastCarried != null
                    && ItemStack.matches(lastCarried, carried)) return;
            lastHover = hover;
            lastBalance = balance;
            lastCarried = carried.copy();
            if (hover == Hover.SLOT) setTooltip(Tooltip.create(tooltipFor(carried)));
            else setTooltip(null);
        }

        private Hover hoverAt(double mouseX, double mouseY) {
            return overSlot(mouseX, mouseY) ? Hover.SLOT : Hover.NONE;
        }

        private boolean overSlot(double mouseX, double mouseY) {
            int x = getX() + (WIDTH - SLOT_SIZE) / 2;
            int y = getY() + SLOT_Y;
            return mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!this.active || !this.visible || button != 0 || !this.isMouseOver(mouseX, mouseY)) return false;
            if (overSlot(mouseX, mouseY)) sendCommand("eco instasell");
            return true;
        }

        @Override
        public void playDownSound(SoundManager soundManager) {}

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (!visible) return;
            Minecraft minecraft = Minecraft.getInstance();
            blitPanel(graphics, getX(), getY(), WIDTH, HEIGHT);
            Component balance = Component.literal(EconomyCraft.formatMoney(currentBalance()));
            int slotX = getX() + (WIDTH - SLOT_SIZE) / 2;
            graphics.drawString(minecraft.font, balance,
                    getX() + (WIDTH - minecraft.font.width(balance)) / 2, getY() + BALANCE_Y, TITLE_COLOR, false);
            graphics.drawString(minecraft.font, TITLE,
                    getX() + (WIDTH - minecraft.font.width(TITLE)) / 2, getY() + TITLE_Y, TITLE_COLOR, false);
            graphics.blitSprite(SLOT, slotX, getY() + SLOT_Y, SLOT_SIZE, SLOT_SIZE);
            divider(graphics, getX(), getY() + TITLE_DIVIDER_Y);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private enum Hover { NONE, SLOT }
}
