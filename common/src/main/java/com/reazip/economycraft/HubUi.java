package com.reazip.economycraft;

import com.reazip.economycraft.shop.ShopUi;
import net.minecraft.server.level.ServerPlayer;

public final class HubUi {
    private HubUi() {}

    public static void open(ServerPlayer player) {
        ShopUi.open(player, EconomyCraft.getManager(player.level().getServer()));
    }
}
