package org.edtp.asynclogin.mixin;

import net.minecraft.server.MinecraftServer;
import org.edtp.asynclogin.platform.AsyncLoginRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @Inject(
        method = "stopServer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/SavedDataStorage;close()V"
        )
    )
    private void asynclogin$drainIoBeforeStorageCloses(CallbackInfo ci) {
        AsyncLoginRuntime.shutdown();
    }
}
