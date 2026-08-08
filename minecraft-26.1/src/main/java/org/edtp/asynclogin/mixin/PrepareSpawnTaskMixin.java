package org.edtp.asynclogin.mixin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.server.players.NameAndId;
import org.edtp.asynclogin.platform.AsyncPrepareSpawnTask;
import org.edtp.asynclogin.platform.PlayerDataLoadContext;
import org.edtp.asynclogin.platform.PlayerLoginDataLoadContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PrepareSpawnTask.class)
abstract class PrepareSpawnTaskMixin implements AsyncPrepareSpawnTask {
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private NameAndId nameAndId;

    @Unique private CompletableFuture<PlayerDataLoadContext.Result> asynclogin$initialLoad;
    @Unique private CompletableFuture<PlayerLoginDataLoadContext.Result> asynclogin$finalLoad;
    @Unique private Consumer<Packet<?>> asynclogin$startConsumer;
    @Unique private boolean asynclogin$replayingStart;
    @Unique private boolean asynclogin$initialLoadApplied;
    @Unique private boolean asynclogin$entitiesReady;

    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private void asynclogin$queueInitialPlayerDataLoad(Consumer<Packet<?>> connection, CallbackInfo ci) {
        if (this.asynclogin$replayingStart) {
            return;
        }

        if (this.asynclogin$initialLoad != null || this.asynclogin$initialLoadApplied) {
            throw new IllegalStateException("PrepareSpawnTask was started more than once");
        }

        this.asynclogin$startConsumer = connection;
        this.asynclogin$initialLoad = PlayerDataLoadContext.submit(
            this.server.getPlayerList(), this.nameAndId, "prepare spawn"
        );
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void asynclogin$applyInitialPlayerDataLoad(CallbackInfoReturnable<Boolean> cir) {
        if (this.asynclogin$initialLoadApplied) {
            return;
        }

        CompletableFuture<PlayerDataLoadContext.Result> future = this.asynclogin$initialLoad;
        if (future == null || !future.isDone()) {
            cir.setReturnValue(false);
            return;
        }

        PlayerDataLoadContext.Result result = future.getNow(null);
        if (result == null) {
            cir.setReturnValue(false);
            return;
        }

        Consumer<Packet<?>> consumer = this.asynclogin$startConsumer;
        this.asynclogin$initialLoadApplied = true;
        this.asynclogin$initialLoad = null;
        this.asynclogin$startConsumer = null;
        this.asynclogin$replayingStart = true;
        try {
            PlayerDataLoadContext.runWith(result, () -> ((PrepareSpawnTask) (Object) this).start(consumer));
        } finally {
            this.asynclogin$replayingStart = false;
        }
    }

    @Override
    public CompletableFuture<PlayerLoginDataLoadContext.Result> asynclogin$beginFinalPlayerDataLoad() {
        if (this.asynclogin$finalLoad == null) {
            this.asynclogin$finalLoad = PlayerLoginDataLoadContext.submit(
                this.server, this.server.getPlayerList(), this.nameAndId
            );
        }
        return this.asynclogin$finalLoad;
    }

    @Override
    public boolean asynclogin$areEntitiesReady() {
        return this.asynclogin$entitiesReady;
    }

    @Override
    public void asynclogin$setEntitiesReady(boolean ready) {
        this.asynclogin$entitiesReady = ready;
    }
}
