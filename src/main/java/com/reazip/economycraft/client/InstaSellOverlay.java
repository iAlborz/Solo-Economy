package com.reazip.economycraft.client;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.net.EconomyPackets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.time.Duration;

@Environment(EnvType.CLIENT)
public final class InstaSellOverlay {
    private static final int SLOT_SIZE = 18;
    private static final int PAD = 6;
    private static final int BALANCE_Y = PAD;
    private static final int JOINT_Y = BALANCE_Y + 10;
    private static final int TITLE_DIVIDER_Y = JOINT_Y + 10;
    private static final int TITLE_Y = TITLE_DIVIDER_Y + 4;
    private static final int SLOT_Y = TITLE_Y + 11;
    private static final int WIDTH = 72;
    private static final int BUTTON_Y = SLOT_Y + SLOT_SIZE + 4;
    private static final int BUTTON_H = 12;
    private static final int BUTTON_GAP = 2;
    private static final int BUTTON_W = (WIDTH - 2 * 6 - BUTTON_GAP) / 2;
    private static final int HEIGHT = BUTTON_Y + BUTTON_H + PAD;
    private static final int BUTTON_BORDER = 0xFF373737;
    private static final int BUTTON_FILL = 0xFF8B8B8B;
    private static final int BUTTON_HOVER = 0xFFA6A6A6;
    private static final Component SEND = Component.literal("Send");
    private static final Component JOINT = Component.literal("Joint");
    private static final Component SEND_HINT = Component.literal("Send money to another player");
    private static final Component JOINT_HINT = Component.literal("Share finances with other players, or leave");
    private static final int BORDER = 4;
    private static final int SRC_W = 176;
    private static final int SRC_H = 166;
    private static final int TEX = 256;
    private static final int PANEL_GREY = 0xFFC6C6C6;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int DIVIDER_COLOR = 0xFF8B8B8B;
    private static final Component TITLE = Component.literal("Insta Sell");
    private static final Component EMPTY_HINT = Component.literal("Drop items here to sell\nCtrl/Cmd-click a stack in shop mode to sell it");
    private static final Component CANNOT_SELL = Component.literal("This item cannot be sold.")
            .withStyle(ChatFormatting.RED);
    private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier PANEL = Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
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
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            onInit(screen);
            ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                refresh(s, mouseX, mouseY);
            });
        });
    }

    private static boolean isTarget(Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CraftingScreen;
    }

    private static void onInit(Screen screen) {
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
        Screens.getWidgets(screen).add(panel);
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

    private static Component tooltipFor(ItemStack carried) {
        if (carried.isEmpty()) return EMPTY_HINT;

        Long unit = ClientEconomy.unitSell(carried);
        Long total = unit == null ? null : safeMultiply(unit, carried.getCount());
        if (total == null) return CANNOT_SELL;

        return Component.literal(carried.getHoverName().getString() + "\n")
                .append(Component.literal(EconomyCraft.formatMoney(total)).withStyle(ChatFormatting.GREEN));
    }

    private static @Nullable Long safeMultiply(long value, int count) {
        try {
            return Math.multiplyExact(value, count);
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private static void blit(GuiGraphicsExtractor graphics, int x, int y, float u, float v, int w, int h) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, x, y, u, v, w, h, TEX, TEX);
    }

    private static void tileH(GuiGraphicsExtractor graphics, int x, int y, int w, int u, int v) {
        int dx = 0;
        while (dx < w) {
            int cw = Math.min(SRC_W - 2 * BORDER, w - dx);
            blit(graphics, x + dx, y, u, v, cw, BORDER);
            dx += cw;
        }
    }

    private static void tileV(GuiGraphicsExtractor graphics, int x, int y, int h, int u, int v) {
        int dy = 0;
        while (dy < h) {
            int ch = Math.min(SRC_H - 2 * BORDER, h - dy);
            blit(graphics, x, y + dy, u, v, BORDER, ch);
            dy += ch;
        }
    }

    private static void blitPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
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

    private static void divider(GuiGraphicsExtractor graphics, int panelX, int y) {
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
        private String lastPartners = "";

        private Panel(int x, int y) {
            super(x, y, WIDTH, HEIGHT, TITLE);
        }

        private void update(ItemStack carried, int mouseX, int mouseY) {
            Hover hover = hoverAt(mouseX, mouseY);
            long balance = ClientEconomy.balance();
            String partners = ClientEconomy.partners();
            if (lastHover == hover && lastBalance == balance && lastPartners.equals(partners) && lastCarried != null
                    && ItemStack.matches(lastCarried, carried)) return;
            lastHover = hover;
            lastBalance = balance;
            lastPartners = partners;
            lastCarried = carried.copy();
            switch (hover) {
                case SLOT -> setTooltip(Tooltip.create(tooltipFor(carried)));
                case SEND -> setTooltip(Tooltip.create(SEND_HINT));
                case JOINT -> setTooltip(Tooltip.create(JOINT_HINT));
                default -> setTooltip(null);
            }
        }

        private Hover hoverAt(double mouseX, double mouseY) {
            if (overSlot(mouseX, mouseY)) return Hover.SLOT;
            if (overButton(mouseX, mouseY, 0)) return Hover.SEND;
            if (overButton(mouseX, mouseY, 1)) return Hover.JOINT;
            return Hover.NONE;
        }

        private boolean overButton(double mouseX, double mouseY, int index) {
            int x = getX() + BORDER + 2 + index * (BUTTON_W + BUTTON_GAP);
            int y = getY() + BUTTON_Y;
            return mouseX >= x && mouseX < x + BUTTON_W && mouseY >= y && mouseY < y + BUTTON_H;
        }

        private boolean overSlot(double mouseX, double mouseY) {
            int x = getX() + (WIDTH - SLOT_SIZE) / 2;
            int y = getY() + SLOT_Y;
            return mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            if (overSlot(event.x(), event.y())) {
                if (ClientEconomy.sellEnabled()) ClientEconomy.send(new EconomyPackets.InstaSell());
            } else if (overButton(event.x(), event.y(), 0)) {
                ClientEconomy.send(new EconomyPackets.OpenMenu(EconomyPackets.OpenMenu.Menu.SEND));
            } else if (overButton(event.x(), event.y(), 1)) {
                ClientEconomy.send(new EconomyPackets.OpenMenu(EconomyPackets.OpenMenu.Menu.JOINT));
            }
        }

        @Override
        public void playDownSound(SoundManager soundManager) {}

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            if (!visible) return;
            Minecraft minecraft = Minecraft.getInstance();
            blitPanel(graphics, getX(), getY(), WIDTH, HEIGHT);
            Component balance = Component.literal(EconomyCraft.formatMoney(ClientEconomy.balance()));
            String partners = ClientEconomy.partners();
            Component joint = Component.literal(minecraft.font.plainSubstrByWidth(
                    partners.isEmpty() ? "Solo" : "With " + partners, WIDTH - 2 * BORDER - 4));
            int slotX = getX() + (WIDTH - SLOT_SIZE) / 2;
            graphics.text(minecraft.font, balance,
                    getX() + (WIDTH - minecraft.font.width(balance)) / 2, getY() + BALANCE_Y, TITLE_COLOR, false);
            graphics.text(minecraft.font, joint,
                    getX() + (WIDTH - minecraft.font.width(joint)) / 2, getY() + JOINT_Y, TITLE_COLOR, false);
            graphics.text(minecraft.font, TITLE,
                    getX() + (WIDTH - minecraft.font.width(TITLE)) / 2, getY() + TITLE_Y, TITLE_COLOR, false);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, slotX, getY() + SLOT_Y, SLOT_SIZE, SLOT_SIZE);
            divider(graphics, getX(), getY() + TITLE_DIVIDER_Y);
            paintButton(graphics, minecraft, 0, SEND, mouseX, mouseY);
            paintButton(graphics, minecraft, 1, JOINT, mouseX, mouseY);
        }

        private void paintButton(GuiGraphicsExtractor graphics, Minecraft minecraft, int index, Component label,
                                 int mouseX, int mouseY) {
            int x = getX() + BORDER + 2 + index * (BUTTON_W + BUTTON_GAP);
            int y = getY() + BUTTON_Y;
            graphics.fill(x, y, x + BUTTON_W, y + BUTTON_H, BUTTON_BORDER);
            graphics.fill(x + 1, y + 1, x + BUTTON_W - 1, y + BUTTON_H - 1,
                    overButton(mouseX, mouseY, index) ? BUTTON_HOVER : BUTTON_FILL);
            graphics.text(minecraft.font, label, x + (BUTTON_W - minecraft.font.width(label)) / 2,
                    y + (BUTTON_H - 8) / 2, 0xFFFFFFFF, true);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private enum Hover { NONE, SLOT, SEND, JOINT }
}
