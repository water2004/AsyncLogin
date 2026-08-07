package org.edtp.asynclogin.mixin;

import com.mojang.logging.LogUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.TagValueOutput;
import org.edtp.asynclogin.platform.AsyncLoginRuntime;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerDataStorage.class)
abstract class PlayerDataStorageMixin {
    private static final Logger ASYNCLOGIN_LOGGER = LogUtils.getLogger();

    @Shadow @Final private File playerDir;

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void asynclogin$snapshotAndQueueSave(Player player, CallbackInfo ci) {
        ci.cancel();

        CompoundTag dataToStore;
        String playerId;
        String playerName;
        try (ProblemReporter.ScopedCollector reporter =
                 new ProblemReporter.ScopedCollector(player.problemPath(), ASYNCLOGIN_LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, player.registryAccess());
            player.saveWithoutId(output);
            dataToStore = output.buildResult();
            playerId = player.getStringUUID();
            playerName = player.getPlainTextName();
        } catch (Exception ignored) {
            ASYNCLOGIN_LOGGER.warn("Failed to save player data for {}", player.getPlainTextName());
            return;
        }

        Path playerDirPath = this.playerDir.toPath();
        String description = "player DAT save (" + playerId + ")";
        AsyncLoginRuntime.observe(description, AsyncLoginRuntime.io().submit(description, () -> {
            try {
                Path tmpFile = Files.createTempFile(playerDirPath, playerId + "-", ".dat");
                NbtIo.writeCompressed(dataToStore, tmpFile);
                Path realFile = playerDirPath.resolve(playerId + ".dat");
                Path oldFile = playerDirPath.resolve(playerId + ".dat_old");
                Util.safeReplaceFile(realFile, tmpFile, oldFile);
            } catch (Exception ignored) {
                ASYNCLOGIN_LOGGER.warn("Failed to save player data for {}", playerName);
            }
        }));
    }
}
