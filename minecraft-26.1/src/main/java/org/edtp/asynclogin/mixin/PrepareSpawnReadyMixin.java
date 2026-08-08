package org.edtp.asynclogin.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.edtp.asynclogin.platform.AsyncPrepareSpawnTask;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.network.config.PrepareSpawnTask$Ready")
abstract class PrepareSpawnReadyMixin {
    @Shadow @Final private ServerLevel spawnLevel;
    @Shadow @Final private Vec3 spawnPosition;
    @Shadow @Final private PrepareSpawnTask this$0;

    @Inject(method = "keepAlive", at = @At("TAIL"))
    private void asynclogin$pollEntityLoads(CallbackInfo ci) {
        ChunkPos spawnChunk = ChunkPos.containing(BlockPos.containing(this.spawnPosition));
        ((AsyncPrepareSpawnTask) this.this$0).asynclogin$setEntitiesReady(
            asynclogin$areEntitiesReady(this.spawnLevel, spawnChunk, 3)
        );
    }

    @Redirect(
        method = "spawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;waitForEntities(Lnet/minecraft/world/level/ChunkPos;I)V"
        )
    )
    private void asynclogin$avoidBlockingEntityWait(ServerLevel level, ChunkPos centerChunk, int radius) {
        if (!asynclogin$areEntitiesReady(level, centerChunk, radius)) {
            throw new IllegalStateException("Player spawn entity data was not ready");
        }
    }

    private static boolean asynclogin$areEntitiesReady(ServerLevel level, ChunkPos centerChunk, int radius) {
        ((ServerLevelAccessor) level).asynclogin$getEntityManager().processPendingLoads();
        return ChunkPos.rangeClosed(centerChunk, radius).allMatch(chunk -> level.areEntitiesLoaded(chunk.pack()));
    }
}
