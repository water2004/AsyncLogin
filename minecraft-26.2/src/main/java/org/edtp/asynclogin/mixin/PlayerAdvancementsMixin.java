package org.edtp.asynclogin.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.util.FileUtil;
import org.edtp.asynclogin.platform.AsyncLoginRuntime;
import org.edtp.asynclogin.platform.PlayerLoginDataLoadContext;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(PlayerAdvancements.class)
abstract class PlayerAdvancementsMixin {
    @Shadow private static Gson GSON;
    @Shadow private static Logger LOGGER;
    @Shadow @Final private Path playerSavePath;
    @Shadow @Final private Set<AdvancementHolder> progressChanged;

    @Shadow
    private void checkForAutomaticTriggers(ServerAdvancementManager manager) {
        throw new AssertionError();
    }

    @Shadow
    private void registerListeners(ServerAdvancementManager manager) {
        throw new AssertionError();
    }

    @Shadow
    private void startProgress(AdvancementHolder holder, AdvancementProgress progress) {
        throw new AssertionError();
    }

    @Shadow
    private void markForVisibilityUpdate(AdvancementHolder holder) {
        throw new AssertionError();
    }

    @Inject(method = "load", at = @At("HEAD"), cancellable = true)
    private void asynclogin$applyCapturedProgress(ServerAdvancementManager manager, CallbackInfo ci) {
        PlayerLoginDataLoadContext.Result result = PlayerLoginDataLoadContext.current();
        PlayerLoginDataLoadContext.AdvancementSnapshot snapshot = result == null
            ? null
            : result.advancements(this.playerSavePath);
        if (snapshot == null) {
            return;
        }

        if (snapshot.failureKind() == PlayerLoginDataLoadContext.FailureKind.ACCESS) {
            LOGGER.error("Couldn't access player advancements in {}", this.playerSavePath, snapshot.failure());
        } else if (snapshot.failureKind() == PlayerLoginDataLoadContext.FailureKind.PARSE) {
            LOGGER.error("Couldn't parse player advancements in {}", this.playerSavePath, snapshot.failure());
        } else if (snapshot.hasProgress()) {
            snapshot.forEach((id, progress) -> {
                AdvancementHolder advancement = manager.get(id);
                if (advancement == null) {
                    LOGGER.warn(
                        "Ignored advancement '{}' in progress file {} - it doesn't exist anymore?",
                        id,
                        this.playerSavePath
                    );
                } else {
                    this.startProgress(advancement, progress);
                    this.progressChanged.add(advancement);
                    this.markForVisibilityUpdate(advancement);
                }
            });
        }

        this.checkForAutomaticTriggers(manager);
        this.registerListeners(manager);
        ci.cancel();
    }

    @Inject(
        method = "save",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/FileUtil;createDirectoriesSafe(Ljava/nio/file/Path;)V"
        ),
        cancellable = true,
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void asynclogin$queueSave(CallbackInfo ci, JsonElement json) {
        if (AsyncLoginRuntime.io().isIoThread()) {
            return;
        }

        String description = "player advancements save (" + this.playerSavePath.getFileName() + ")";
        AsyncLoginRuntime.observe(description, AsyncLoginRuntime.io().submit(description, () -> {
            try {
                FileUtil.createDirectoriesSafe(this.playerSavePath.getParent());
                try (Writer writer = Files.newBufferedWriter(this.playerSavePath, StandardCharsets.UTF_8)) {
                    GSON.toJson(json, GSON.newJsonWriter(writer));
                }
            } catch (IOException | JsonIOException failure) {
                LOGGER.error("Couldn't save player advancements to {}", this.playerSavePath, failure);
            }
        }));
        ci.cancel();
    }
}
