package com.reazip.economycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class ClientScreens {
    private ClientScreens() {}

    static Screen current(Minecraft minecraft) {
        return minecraft.gui.screen();
    }

    static void open(Minecraft minecraft, Screen screen) {
        minecraft.gui.setScreen(screen);
    }
}
