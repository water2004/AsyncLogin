package org.edtp.asynclogin.mixin;

import net.minecraft.server.players.CachedUserNameToIdResolver;
import org.edtp.asynclogin.platform.AsyncLoginRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CachedUserNameToIdResolver.class)
abstract class CachedUserNameToIdResolverMixin {
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void asynclogin$queueSave(CallbackInfo ci) {
        if (AsyncLoginRuntime.io().isIoThread()) {
            return;
        }

        String description = "usercache save";
        AsyncLoginRuntime.observe(description, AsyncLoginRuntime.io().submit(description,
            () -> ((CachedUserNameToIdResolver) (Object) this).save()
        ));
        ci.cancel();
    }
}
