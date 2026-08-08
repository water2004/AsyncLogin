package org.edtp.asynclogin.platform;

import java.util.concurrent.CompletableFuture;

public interface AsyncPrepareSpawnTask {
    CompletableFuture<PlayerLoginDataLoadContext.Result> asynclogin$beginFinalPlayerDataLoad();

    boolean asynclogin$areEntitiesReady();

    void asynclogin$setEntitiesReady(boolean ready);
}
