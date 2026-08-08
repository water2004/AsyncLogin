package org.edtp.asynclogin.mixin;

import com.google.common.io.Files;
import com.google.gson.JsonArray;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import net.minecraft.server.players.CachedUserNameToIdResolver;
import org.edtp.asynclogin.platform.AsyncLoginRuntime;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(CachedUserNameToIdResolver.class)
abstract class CachedUserNameToIdResolverMixin {
    @Shadow @Final private File file;

    @Inject(
        method = "save",
        at = @At(
            value = "INVOKE",
            target = "Lcom/google/common/io/Files;newWriter(Ljava/io/File;Ljava/nio/charset/Charset;)Ljava/io/BufferedWriter;"
        ),
        cancellable = true,
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void asynclogin$queueSave(CallbackInfo ci, JsonArray entryList, DateFormat dateFormat, String toSave) {
        String description = "usercache save";
        AsyncLoginRuntime.observe(description, AsyncLoginRuntime.io().submit(description, () -> {
            try (Writer writer = Files.newWriter(this.file, StandardCharsets.UTF_8)) {
                writer.write(toSave);
            } catch (IOException ignored) {
            }
        }));
        ci.cancel();
    }
}
