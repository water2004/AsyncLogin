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
            Class.forName("net.minecraft.server.network.config.PrepareSpawnTask");
            Class.forName("net.minecraft.server.network.ServerConfigurationPacketListenerImpl");
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("Could not load an AsyncLogin Mixin target", failure);
        }
    }
}
