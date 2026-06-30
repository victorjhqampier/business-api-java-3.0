package com.arify.application.ports;

import com.arify.application.adapters.ExamplePreRequestAdapter;
import com.arify.application.adapters.RetrieveExampleAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.executors.EasyResult;
import com.arify.domain.commons.CancellationToken;

public interface ExampleIdempotencyPort {
    EasyResult<RetrieveExampleAdapter> getDataAsync(
            TraceIdentifierAdapter trace,
            CancellationToken token);

    EasyResult<RetrieveExampleAdapter> setIdempotencyAsync(
            TraceIdentifierAdapter trace,
            ExamplePreRequestAdapter body,
            CancellationToken token);
}
