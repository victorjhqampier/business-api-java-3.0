package com.arify.application.ports;

import com.arify.application.adapters.CreateExampleAdapter;
import com.arify.application.adapters.ExampleRequestAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.executors.EasyResult;
import com.arify.domain.commons.CancellationToken;

public interface ExamplePort {
    CompletableFuture<EasyResult<CreateExampleAdapter>> getDataAsync(TraceIdentifierAdapter trace, ExampleRequestAdapter exampleRequest, CancellationToken token);
}
