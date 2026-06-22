package com.arify.application.ports;

import com.arify.application.adapters.RetrieveExampleAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.executors.EasyResult;
import com.arify.domain.commons.CancellationToken;
import java.util.concurrent.CompletableFuture;

public interface ExampleCachePort {
    CompletableFuture<EasyResult<RetrieveExampleAdapter>> showExampleAsync(
            TraceIdentifierAdapter trace,
            CancellationToken token);
}
