package org.edtp.asynclogin.mixin;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.edtp.asynclogin.platform.PlayerDataLoadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
abstract class PlayerListMixin {
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
}
