package org.edtp.asynclogin.platform;

import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.util.FileUtil;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Carries the final login-phase file snapshots while vanilla constructs the player on the server thread.
 */
public final class PlayerLoginDataLoadContext {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Result> CURRENT = new ThreadLocal<>();

    private PlayerLoginDataLoadContext() {
    }

    public static CompletableFuture<Result> submit(
        MinecraftServer server,
        PlayerList playerList,
        NameAndId nameAndId
    ) {
        Path statsFolder = server.getWorldPath(LevelResource.PLAYER_STATS_DIR);
        Path advancementFile = server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR)
            .resolve(nameAndId.id() + ".json");
        DataFixer dataFixer = server.getFixerUpper();
        String description = "player login storage load (finish configuration, " + nameAndId.id() + ")";

        return AsyncLoginRuntime.io().submit(description, () -> {
            ServerStatsCounter stats;
            try {
                Path statsFile = locateStatsFile(statsFolder, nameAndId);
                stats = new ServerStatsCounter(server, statsFile);
            } catch (Throwable failure) {
                return Result.statsFailure(nameAndId.id(), failure);
            }

            AdvancementSnapshot advancements;
            try {
                advancements = AdvancementSnapshot.read(dataFixer, advancementFile);
            } catch (Throwable failure) {
                return Result.advancementsFailure(nameAndId.id(), stats, failure);
            }

            try {
                Optional<CompoundTag> playerData = playerList.loadPlayerData(nameAndId);
                return Result.success(nameAndId.id(), stats, advancements, playerData);
            } catch (Throwable failure) {
                return Result.playerDataFailure(nameAndId.id(), stats, advancements, failure);
            }
        });
    }

    public static @Nullable Result current() {
        return CURRENT.get();
    }

    public static void runWith(Result result, Runnable operation) {
        Result previous = CURRENT.get();
        CURRENT.set(result);
        try {
            operation.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    private static Path locateStatsFile(Path statsFolder, NameAndId nameAndId) {
        Path uuidStatsFile = statsFolder.resolve(nameAndId.id() + ".json");
        if (Files.exists(uuidStatsFile)) {
            return uuidStatsFile;
        }

        String playerNameStatsFile = nameAndId.name() + ".json";
        if (FileUtil.isValidPathSegment(playerNameStatsFile)) {
            Path playerNameStatsPath = statsFolder.resolve(playerNameStatsFile);
            if (Files.isRegularFile(playerNameStatsPath)) {
                try {
                    return Files.move(playerNameStatsPath, uuidStatsFile);
                } catch (IOException ignored) {
                    LOGGER.warn("Failed to copy file {} to {}", playerNameStatsFile, uuidStatsFile);
                    return playerNameStatsPath;
                }
            }
        }

        return uuidStatsFile;
    }

    public static final class Result {
        private final UUID playerId;
        private final @Nullable ServerStatsCounter stats;
        private final @Nullable Throwable statsFailure;
        private final @Nullable AdvancementSnapshot advancements;
        private final @Nullable Throwable advancementsFailure;
        private final @Nullable Optional<CompoundTag> playerData;
        private final @Nullable Throwable playerDataFailure;

        private Result(
            UUID playerId,
            @Nullable ServerStatsCounter stats,
            @Nullable Throwable statsFailure,
            @Nullable AdvancementSnapshot advancements,
            @Nullable Throwable advancementsFailure,
            @Nullable Optional<CompoundTag> playerData,
            @Nullable Throwable playerDataFailure
        ) {
            this.playerId = playerId;
            this.stats = stats;
            this.statsFailure = unwrap(statsFailure);
            this.advancements = advancements;
            this.advancementsFailure = unwrap(advancementsFailure);
            this.playerData = playerData;
            this.playerDataFailure = unwrap(playerDataFailure);
        }

        public @Nullable ServerStatsCounter stats(UUID candidatePlayerId) {
            if (!this.playerId.equals(candidatePlayerId)) {
                return null;
            }
            throwUnchecked(this.statsFailure);
            if (this.stats == null) {
                throw new IllegalStateException("Player statistics were not loaded");
            }
            return this.stats;
        }

        public @Nullable AdvancementSnapshot advancements(Path candidatePath) {
            throwUnchecked(this.advancementsFailure);
            AdvancementSnapshot snapshot = this.advancements;
            if (snapshot == null) {
                throwUnchecked(this.statsFailure);
                throw new IllegalStateException("Player advancements were not loaded");
            }
            return snapshot.path().equals(candidatePath) ? snapshot : null;
        }

        public PlayerDataLoadContext.Result playerData() {
            Throwable failure = this.statsFailure != null
                ? this.statsFailure
                : this.advancementsFailure != null ? this.advancementsFailure : this.playerDataFailure;
            return new PlayerDataLoadContext.Result(this.playerData, failure);
        }

        private static Result statsFailure(UUID playerId, Throwable failure) {
            return new Result(playerId, null, failure, null, null, null, null);
        }

        private static Result advancementsFailure(UUID playerId, ServerStatsCounter stats, Throwable failure) {
            return new Result(playerId, stats, null, null, failure, null, null);
        }

        private static Result playerDataFailure(
            UUID playerId,
            ServerStatsCounter stats,
            AdvancementSnapshot advancements,
            Throwable failure
        ) {
            return new Result(playerId, stats, null, advancements, null, null, failure);
        }

        private static Result success(
            UUID playerId,
            ServerStatsCounter stats,
            AdvancementSnapshot advancements,
            Optional<CompoundTag> playerData
        ) {
            return new Result(playerId, stats, null, advancements, null, playerData, null);
        }
    }

    public static final class AdvancementSnapshot {
        private final Path path;
        private final @Nullable Map<Identifier, AdvancementProgress> progress;
        private final @Nullable FailureKind failureKind;
        private final @Nullable Throwable failure;

        private AdvancementSnapshot(
            Path path,
            @Nullable Map<Identifier, AdvancementProgress> progress,
            @Nullable FailureKind failureKind,
            @Nullable Throwable failure
        ) {
            this.path = path;
            this.progress = progress;
            this.failureKind = failureKind;
            this.failure = failure;
        }

        private static AdvancementSnapshot read(DataFixer dataFixer, Path path) {
            if (!Files.isRegularFile(path)) {
                return new AdvancementSnapshot(path, null, null, null);
            }

            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Codec<Map<Identifier, AdvancementProgress>> codec = DataFixTypes.ADVANCEMENTS.wrapCodec(
                    Codec.unboundedMap(Identifier.CODEC, AdvancementProgress.CODEC),
                    dataFixer,
                    1343
                );
                Map<Identifier, AdvancementProgress> progress = codec
                    .parse(JsonOps.INSTANCE, StrictJsonParser.parse(reader))
                    .getOrThrow(JsonParseException::new);
                return new AdvancementSnapshot(path, progress, null, null);
            } catch (IOException | JsonIOException failure) {
                return new AdvancementSnapshot(path, null, FailureKind.ACCESS, failure);
            } catch (JsonParseException failure) {
                return new AdvancementSnapshot(path, null, FailureKind.PARSE, failure);
            }
        }

        public Path path() {
            return this.path;
        }

        public boolean hasProgress() {
            return this.progress != null;
        }

        public void forEach(BiConsumer<Identifier, AdvancementProgress> consumer) {
            if (this.progress != null) {
                this.progress.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue())
                    .forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
            }
        }

        public @Nullable FailureKind failureKind() {
            return this.failureKind;
        }

        public @Nullable Throwable failure() {
            return this.failure;
        }
    }

    public enum FailureKind {
        ACCESS,
        PARSE
    }

    private static @Nullable Throwable unwrap(@Nullable Throwable failure) {
        Throwable current = failure;
        while (current != null
            && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void throwUnchecked(@Nullable Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new RuntimeException(failure);
        }
    }
}
