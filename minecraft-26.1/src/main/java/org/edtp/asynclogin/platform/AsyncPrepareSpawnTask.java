package org.edtp.asynclogin.platform;

import java.util.concurrent.CompletableFuture;

public interface AsyncPrepareSpawnTask {
    CompletableFuture<PlayerDataLoadContext.Result> asynclogin$beginFinalPlayerDataLoad();
}
