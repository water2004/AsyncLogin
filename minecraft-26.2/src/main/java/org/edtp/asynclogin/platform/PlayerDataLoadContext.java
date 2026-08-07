package org.edtp.asynclogin.platform;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;

public final class PlayerDataLoadContext {
    private static final ThreadLocal<Result> CURRENT = new ThreadLocal<>();

    private PlayerDataLoadContext() {
    }

    public static CompletableFuture<Result> submit(PlayerList playerList, NameAndId nameAndId, String phase) {
        return AsyncLoginRuntime.io()
            .submit("player DAT load (" + phase + ", " + nameAndId.id() + ")", () -> playerList.loadPlayerData(nameAndId))
            .handle(Result::new);
    }

    public static Result current() {
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

    public record Result(Optional<CompoundTag> value, Throwable failure) {
        public Result(Optional<CompoundTag> value, Throwable failure) {
            this.value = value;
            this.failure = unwrap(failure);
        }

        public Optional<CompoundTag> getOrThrow() {
            if (this.failure == null) {
                return this.value;
            }
            if (this.failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (this.failure instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(this.failure);
        }

        private static Throwable unwrap(Throwable failure) {
            Throwable current = failure;
            while (current != null
                && (current instanceof java.util.concurrent.CompletionException
                    || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
                current = current.getCause();
            }
            return current;
        }
    }
}
