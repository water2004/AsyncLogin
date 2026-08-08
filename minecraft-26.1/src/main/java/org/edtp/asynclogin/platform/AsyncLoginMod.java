package org.edtp.asynclogin.platform;

import net.fabricmc.api.ModInitializer;

public final class AsyncLoginMod implements ModInitializer {
    @Override
    public void onInitialize() {
        AsyncLoginRuntime.initialize();
        if (Boolean.getBoolean("asynclogin.verifyMixins")) {
            verifyLazyMixinTargets();
        }
    }

    private static void verifyLazyMixinTargets() {
        try {
            ClassLoader loader = AsyncLoginMod.class.getClassLoader();
            Class.forName("net.minecraft.server.PlayerAdvancements", false, loader);
            Class.forName("net.minecraft.server.level.ServerLevel", false, loader);
            Class.forName("net.minecraft.server.network.config.PrepareSpawnTask", false, loader);
            Class.forName("net.minecraft.server.network.config.PrepareSpawnTask$Ready", false, loader);
            Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl", false, loader);
            Class.forName("net.minecraft.server.network.ServerConfigurationPacketListenerImpl", false, loader);
            Class.forName("net.minecraft.server.players.CachedUserNameToIdResolver", false, loader);
            Class.forName("net.minecraft.server.players.PlayerList", false, loader);
            Class.forName("net.minecraft.stats.ServerStatsCounter", false, loader);
            Class.forName("net.minecraft.world.level.storage.PlayerDataStorage", false, loader);
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("Could not load an AsyncLogin Mixin target", failure);
        }
    }
}
