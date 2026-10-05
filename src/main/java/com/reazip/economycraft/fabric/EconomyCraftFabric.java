package com.reazip.economycraft.fabric;

import net.fabricmc.api.ModInitializer;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.net.EconomyServerNet;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;

public final class EconomyCraftFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        EconomyCraft.registerEvents();
        EconomyServerNet.register();

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer victim) {
                EconomyCraft.tryHandlePvpKill(victim, damageSource.getEntity());
            }
        });
    }
}
