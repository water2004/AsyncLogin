package org.edtp.asynclogin.mixin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.entity.player.Player;
import org.edtp.asynclogin.platform.PlayerLoginDataLoadContext;
import org.edtp.asynclogin.platform.PlayerDataLoadContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
abstract class PlayerListMixin {
    @Shadow @Final private Map<UUID, ServerStatsCounter> stats;

    @Inject(method = "loadPlayerData", at = @At("HEAD"), cancellable = true)
    private void asynclogin$useCompletedPlayerData(
        NameAndId nameAndId,
        CallbackInfoReturnable<Optional<CompoundTag>> cir
    ) {
        PlayerDataLoadContext.Result result = PlayerDataLoadContext.current();
        if (result != null) {
            cir.setReturnValue(result.getOrThrow());
        }
    }

    @Inject(method = "getPlayerStats", at = @At("HEAD"), cancellable = true)
    private void asynclogin$useCompletedStatsFile(Player player, CallbackInfoReturnable<ServerStatsCounter> cir) {
        PlayerLoginDataLoadContext.Result result = PlayerLoginDataLoadContext.current();
        if (result == null) {
            return;
        }

        UUID playerId = player.getUUID();
        ServerStatsCounter preparedCounter = result.stats(playerId);
        if (preparedCounter == null) {
            return;
        }

        ServerStatsCounter counter = this.stats.get(playerId);
        if (counter == null) {
            counter = preparedCounter;
            this.stats.put(playerId, counter);
        }
        cir.setReturnValue(counter);
    }
}
