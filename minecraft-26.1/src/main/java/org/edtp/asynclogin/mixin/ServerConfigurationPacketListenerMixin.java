package org.edtp.asynclogin.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.config.PrepareSpawnTask;
import org.edtp.asynclogin.platform.AsyncPrepareSpawnTask;
import org.edtp.asynclogin.platform.PlayerDataLoadContext;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
abstract class ServerConfigurationPacketListenerMixin {
    @Shadow private @Nullable PrepareSpawnTask prepareSpawnTask;

    @Unique private CompletableFuture<PlayerDataLoadContext.Result> asynclogin$finalLoad;
    @Unique private ServerboundFinishConfigurationPacket asynclogin$finishPacket;
    @Unique private boolean asynclogin$replayingFinish;

    @Inject(
        method = "handleConfigurationFinished",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;finishCurrentTask(Lnet/minecraft/server/network/ConfigurationTask$Type;)V"
        ),
        cancellable = true
    )
    private void asynclogin$queueFinalPlayerDataLoad(ServerboundFinishConfigurationPacket packet, CallbackInfo ci) {
        if (this.asynclogin$replayingFinish || this.prepareSpawnTask == null) {
            return;
        }

        if (this.asynclogin$finalLoad == null) {
            this.asynclogin$finishPacket = packet;
            this.asynclogin$finalLoad = ((AsyncPrepareSpawnTask) this.prepareSpawnTask)
                .asynclogin$beginFinalPlayerDataLoad();
        }
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void asynclogin$finishLoginAfterPlayerDataLoad(CallbackInfo ci) {
        CompletableFuture<PlayerDataLoadContext.Result> future = this.asynclogin$finalLoad;
        if (future == null || !future.isDone()) {
            return;
        }

        PlayerDataLoadContext.Result result = future.getNow(null);
        ServerboundFinishConfigurationPacket packet = this.asynclogin$finishPacket;
        if (result == null || packet == null) {
            return;
        }

        this.asynclogin$finalLoad = null;
        this.asynclogin$finishPacket = null;
        this.asynclogin$replayingFinish = true;
        try {
            PlayerDataLoadContext.runWith(result, () ->
                ((ServerConfigurationPacketListenerImpl) (Object) this).handleConfigurationFinished(packet)
            );
        } finally {
            this.asynclogin$replayingFinish = false;
        }
    }
}
