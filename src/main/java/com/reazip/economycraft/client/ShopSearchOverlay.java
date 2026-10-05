package com.reazip.economycraft.client;

import com.reazip.economycraft.util.MenuUiSupport;
import com.reazip.economycraft.net.EconomyPackets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

@Environment(EnvType.CLIENT)
public final class ShopSearchOverlay {
    private static final int FIELD_WIDTH = 81;
    private static final int FIELD_HEIGHT = 14;
    private static final int ICON_SIZE = 12;
    private static final int ICON_TO_FIELD = 17;
    private static final Component ITEMS_HINT = hint("Search");
    private static final Component PLAYERS_HINT = hint("Search players");

    private static boolean registered;

    private ShopSearchOverlay() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            onInit(screen);
            ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                if (s instanceof AbstractContainerScreen<?> container) hideSearchSlots(container);
            });
        });
    }

    private static void onInit(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return;
        SearchScreen search = findSearch(container);
        if (search == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        int rows = Math.max(1, (container.getMenu().slots.size() - 36) / 9);
        int imageHeight = 114 + rows * 18;
        int left = (screen.width - 176) / 2;
        int top = (screen.height - imageHeight) / 2;
        boolean showIcon = search.hint.getString().toLowerCase().contains("player");
        int groupWidth = (showIcon ? ICON_TO_FIELD : 0) + FIELD_WIDTH;
        int x = left + 176 - 7 - groupWidth;
        int y = top + 2;

        if (showIcon) {
            ImageWidget icon = ImageWidget.sprite(ICON_SIZE, ICON_SIZE, Identifier.withDefaultNamespace("icon/search"));
            icon.setX(x);
            icon.setY(y + 1);
            Screens.getWidgets(screen).add(icon);
        }

        EditBox box = new EditBox(minecraft.font, x + (showIcon ? ICON_TO_FIELD : 0), y, FIELD_WIDTH, FIELD_HEIGHT, search.hint) {
            @Override
            public boolean keyPressed(KeyEvent event) {
                if (super.keyPressed(event)) return true;
                return this.canConsumeInput() && !event.isEscape();
            }
        };
        box.setHint(search.hint);
        box.setMaxLength(MenuUiSupport.SEARCH_QUERY_MAX_LENGTH);
        box.setVisible(true);
        box.setTextColor(-1);
        box.setValue(search.query);
        box.setResponder(ShopSearchOverlay::sendSearch);
        Screens.getWidgets(screen).add(box);
    }

    private static void sendSearch(String query) {
        ClientEconomy.send(new EconomyPackets.SearchQuery(query == null ? "" : query.trim()));
    }

    private static void hideSearchSlots(AbstractContainerScreen<?> screen) {
        for (Slot slot : screen.getMenu().slots) {
            if (isSearchSlot(slot.getItem().getHoverName().getString())) {
                slot.set(ItemStack.EMPTY);
            }
        }
    }

    private static SearchScreen findSearch(AbstractContainerScreen<?> screen) {
        String title = screen.getTitle().getString();
        String query = title.startsWith("Search:") ? title.substring("Search:".length()).trim() : "";
        String hintName = null;
        boolean found = title.startsWith("Search:");
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String name = stack.getHoverName().getString();
            if ("Clear search".equals(name)) {
                found = true;
                if (query.isEmpty()) query = showingQuery(stack);
            } else if (name.startsWith("Search")) {
                found = true;
                hintName = name;
            }
        }
        if (!found) return null;
        Component hint;
        if (hintName != null && hintName.toLowerCase().contains("player")) {
            hint = PLAYERS_HINT;
        } else if (hintName != null) {
            hint = hint(hintName);
        } else if (isPlayerTitle(title)) {
            hint = PLAYERS_HINT;
        } else {
            hint = ITEMS_HINT;
        }
        return new SearchScreen(hint, query);
    }

    private static boolean isPlayerTitle(String title) {
        String lower = title.toLowerCase();
        return lower.contains("player") || lower.contains("pay who");
    }

    private static boolean isSearchSlot(String name) {
        return "Clear search".equals(name)
                || "Search".equals(name)
                || "Search items".equals(name)
                || "Search players".equals(name);
    }

    private static String showingQuery(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return "";
        for (Component line : lore.lines()) {
            String text = line.getString();
            if (text.startsWith("Showing: ")) return text.substring("Showing: ".length());
        }
        return "";
    }

    private static Component hint(String text) {
        return Component.literal(text).withStyle(EditBox.SEARCH_HINT_STYLE);
    }

    private record SearchScreen(Component hint, String query) {}
}
