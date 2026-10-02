package com.reazip.economycraft.fabric.client;

import com.reazip.economycraft.client.InstaSellOverlay;
import com.reazip.economycraft.client.MenuPaginationOverlay;
import com.reazip.economycraft.client.RecipeBookShopOverlay;
import com.reazip.economycraft.client.ShopCategoryOverlay;
import com.reazip.economycraft.client.ShopSearchOverlay;
import net.fabricmc.api.ClientModInitializer;

public final class EconomyCraftFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RecipeBookShopOverlay.register();
        MenuPaginationOverlay.register();
        ShopSearchOverlay.register();
        ShopCategoryOverlay.register();
        InstaSellOverlay.register();
    }
}
