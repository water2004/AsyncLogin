package org.edtp.asynclogin.mixin;

import com.mojang.authlib.GameProfile;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.server.players.PlayerList;
import org.edtp.asynclogin.platform.AsyncPrepareSpawnTask;
import org.edtp.asynclogin.platform.PlayerDataLoadContext;
import org.edtp.asynclogin.platform.PlayerLoginDataLoadContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
abstract class ServerConfigurationPacketListenerMixin {
    @Unique private static final Map<MinecraftServer, Set<UUID>> ASYNCLOGIN_PENDING_LOGINS = new IdentityHashMap<>();

    @Shadow private static Logger LOGGER;
    @Shadow private static Component DISCONNECT_REASON_INVALID_DATA;
    @Shadow @Final private GameProfile gameProfile;
    @Shadow private ClientInformation clientInformation;
    @Shadow private @Nullable PrepareSpawnTask prepareSpawnTask;

    @Unique private CompletableFuture<PlayerLoginDataLoadContext.Result> asynclogin$finalLoad;
    @Unique private boolean asynclogin$finalLoadRequested;
    @Unique private @Nullable MinecraftServer asynclogin$reservedServer;
    @Unique private @Nullable UUID asynclogin$reservedPlayerId;

    @Inject(
        method = "handleConfigurationFinished",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/config/PrepareSpawnTask;spawnPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/network/CommonListenerCookie;)Lnet/minecraft/server/level/ServerPlayer;"
        ),
        cancellable = true
    )
    private void asynclogin$queueFinalPlayerDataLoad(ServerboundFinishConfigurationPacket packet, CallbackInfo ci) {
        if (this.prepareSpawnTask == null) {
            return;
        }

        if (this.asynclogin$reservedPlayerId == null) {
            MinecraftServer server = ((ServerCommonPacketListenerAccessor) this).asynclogin$getServer();
            UUID playerId = this.gameProfile.id();
            if (!asynclogin$reserveLogin(server, playerId)) {
                ((ServerConfigurationPacketListenerImpl) (Object) this)
                    .disconnect(PlayerList.DUPLICATE_LOGIN_DISCONNECT_MESSAGE);
                ci.cancel();
                return;
            }
            this.asynclogin$reservedServer = server;
            this.asynclogin$reservedPlayerId = playerId;
        }

        this.asynclogin$finalLoadRequested = true;
        this.asynclogin$tryBeginFinalLoad(this.prepareSpawnTask);
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void asynclogin$finishLoginAfterPlayerDataLoad(CallbackInfo ci) {
        PrepareSpawnTask task = this.prepareSpawnTask;
        if (!this.asynclogin$finalLoadRequested || task == null) {
            return;
        }

        this.asynclogin$tryBeginFinalLoad(task);
        CompletableFuture<PlayerLoginDataLoadContext.Result> future = this.asynclogin$finalLoad;
        if (future == null || !future.isDone()) {
            return;
        }

        PlayerLoginDataLoadContext.Result result = future.getNow(null);
        if (result == null) {
            return;
        }

        this.asynclogin$finalLoad = null;
        this.asynclogin$finalLoadRequested = false;
        try {
            ServerCommonPacketListenerAccessor commonListener = (ServerCommonPacketListenerAccessor) this;
            PlayerLoginDataLoadContext.runWith(result, () ->
                PlayerDataLoadContext.runWith(result.playerData(), () ->
                    task.spawnPlayer(
                        commonListener.asynclogin$getConnection(),
                        commonListener.asynclogin$createCookie(this.clientInformation)
                    )
                )
            );
        } catch (Exception failure) {
            LOGGER.error("Couldn't place player in world", failure);
            ((ServerConfigurationPacketListenerImpl) (Object) this).disconnect(DISCONNECT_REASON_INVALID_DATA);
        } finally {
            this.asynclogin$releaseLoginReservation();
        }
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void asynclogin$releaseLoginReservationOnDisconnect(DisconnectionDetails details, CallbackInfo ci) {
        this.asynclogin$finalLoadRequested = false;
        this.asynclogin$finalLoad = null;
        this.asynclogin$releaseLoginReservation();
    }

    @Unique
    private void asynclogin$tryBeginFinalLoad(PrepareSpawnTask task) {
        AsyncPrepareSpawnTask asyncTask = (AsyncPrepareSpawnTask) task;
        if (this.asynclogin$finalLoad == null && asyncTask.asynclogin$areEntitiesReady()) {
            this.asynclogin$finalLoad = asyncTask.asynclogin$beginFinalPlayerDataLoad();
        }
    }

    @Unique
    private static synchronized boolean asynclogin$reserveLogin(MinecraftServer server, UUID playerId) {
        return ASYNCLOGIN_PENDING_LOGINS.computeIfAbsent(server, ignored -> new HashSet<>()).add(playerId);
    }

    @Unique
    private static synchronized void asynclogin$releaseLogin(MinecraftServer server, UUID playerId) {
        Set<UUID> playerIds = ASYNCLOGIN_PENDING_LOGINS.get(server);
        if (playerIds != null && playerIds.remove(playerId) && playerIds.isEmpty()) {
            ASYNCLOGIN_PENDING_LOGINS.remove(server);
        }
    }

    @Unique
    private void asynclogin$releaseLoginReservation() {
        MinecraftServer server = this.asynclogin$reservedServer;
        UUID playerId = this.asynclogin$reservedPlayerId;
        this.asynclogin$reservedServer = null;
        this.asynclogin$reservedPlayerId = null;
        if (server != null && playerId != null) {
            asynclogin$releaseLogin(server, playerId);
        }
    }
}
