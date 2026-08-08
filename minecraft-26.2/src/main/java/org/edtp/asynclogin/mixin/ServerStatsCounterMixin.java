package org.edtp.asynclogin.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.util.FileUtil;
import org.edtp.asynclogin.platform.AsyncLoginRuntime;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerStatsCounter.class)
abstract class ServerStatsCounterMixin {
    @Shadow private static Gson GSON;
    @Shadow private static Logger LOGGER;
    @Shadow @Final private Path file;

    @Shadow protected abstract JsonElement toJson();

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void asynclogin$snapshotAndQueueSave(CallbackInfo ci) {
        if (AsyncLoginRuntime.io().isIoThread()) {
            return;
        }

        JsonElement json;
        try {
            json = this.toJson();
        } catch (JsonIOException failure) {
            LOGGER.error("Couldn't save stats to {}", this.file, failure);
            ci.cancel();
            return;
        }

        String description = "player stats save (" + this.file.getFileName() + ")";
        AsyncLoginRuntime.observe(description, AsyncLoginRuntime.io().submit(description, () -> {
            try {
                FileUtil.createDirectoriesSafe(this.file.getParent());
                try (Writer writer = Files.newBufferedWriter(this.file, StandardCharsets.UTF_8)) {
                    GSON.toJson(json, GSON.newJsonWriter(writer));
                }
            } catch (IOException | JsonIOException failure) {
                LOGGER.error("Couldn't save stats to {}", this.file, failure);
            }
        }));
        ci.cancel();
    }
}
