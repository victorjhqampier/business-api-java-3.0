package com.arify.application.usecases.exampleusecase;

import com.arify.application.adapters.CreateExampleAdapter;
import com.arify.application.adapters.ExampleRequestAdapter;
import com.arify.application.adapters.ExampleRequestAdapterValidator;
import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapterValidator;
import com.arify.application.internals.adapters.ValidationResultAdapter;
import com.arify.application.internals.executors.EasyResult;
import com.arify.application.internals.executors.FluentValidationExecutor;
import com.arify.application.ports.ExamplePort;
import com.arify.domain.commons.CancellationReason;
import com.arify.domain.commons.CancellationToken;
import com.arify.domain.entities.FakeApiEntity;
import com.arify.domain.interfaces.IFakeApiInfrastructure;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ExampleUseCase implements ExamplePort {
    public static final int VALIDATION_FAILED_STATUS = 422;
    public static final int REQUEST_TIMEOUT_STATUS = 408;
    public static final int CLIENT_CLOSED_STATUS = 499;
    public static final int EXAMPLE_AGE = 5;

    public final IFakeApiInfrastructure fakeApi;

    public ExampleUseCase(IFakeApiInfrastructure fakeApi) {
        this.fakeApi = fakeApi;
    }

    @Override
    public EasyResult<CreateExampleAdapter> getDataAsync(
            CancellationToken cancellationToken,
            TraceIdentifierAdapter trace,
            ExampleRequestAdapter exampleRequest) {
        CancellationToken token = cancellationToken == null ? CancellationToken.withDefault() : cancellationToken;
        EasyResult<CreateExampleAdapter> cancellationResult = cancellationResultIfRequested(token);
        if (cancellationResult != null) {
            return cancellationResult;
        }

        List<ValidationResultAdapter> traceValidationErrors = FluentValidationExecutor.execute(trace, TraceIdentifierAdapterValidator::new);

        if (!traceValidationErrors.isEmpty()) {
            return EasyResult.failure(VALIDATION_FAILED_STATUS, traceValidationErrors);
        }

        cancellationResult = cancellationResultIfRequested(token);
        if (cancellationResult != null) {
            return cancellationResult;
        }

        List<ValidationResultAdapter> requestValidationErrors = FluentValidationExecutor.execute(exampleRequest, ExampleRequestAdapterValidator::new);
        if (!requestValidationErrors.isEmpty()) {
            return EasyResult.failure(VALIDATION_FAILED_STATUS, requestValidationErrors);
        }

        cancellationResult = cancellationResultIfRequested(token);
        if (cancellationResult != null) {
            return cancellationResult;
        }

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CompletableFuture<Optional<FakeApiEntity>> result1Task = CompletableFuture.supplyAsync(
                () -> fakeApi.getUserAsync(randomId(), token),
                executor);
        CompletableFuture<Optional<FakeApiEntity>> result2Task = CompletableFuture.supplyAsync(
                () -> fakeApi.getTitleAsync(randomId(), token),
                executor);

        token.onCancel(() -> {
            result1Task.cancel(true);
            result2Task.cancel(true);
            executor.shutdownNow();
        });

        try {
            Duration remainingTimeout = token.remainingTimeout();
            if (remainingTimeout.isZero()) {
                return cancellationResult(token);
            }

            CompletableFuture.allOf(result1Task, result2Task)
                    .get(timeoutMillis(remainingTimeout), TimeUnit.MILLISECONDS);

            cancellationResult = cancellationResultIfRequested(token);
            if (cancellationResult != null) {
                return cancellationResult;
            }

            Optional<FakeApiEntity> result1 = result1Task.getNow(Optional.empty());
            Optional<FakeApiEntity> result2 = result2Task.getNow(Optional.empty());
            if (result1.isEmpty() || result2.isEmpty()) {
                return EasyResult.empty();
            }

            CreateExampleAdapter result = new CreateExampleAdapter(
                    result1.get().title(),
                    EXAMPLE_AGE,
                    result2.get().title());

            return EasyResult.success(result);
        } catch (TimeoutException exception) {
            token.cancel(CancellationReason.TIMEOUT);
            return cancellationResult(token);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            token.cancel(CancellationReason.INTERRUPTED);
            return cancellationResult(token);
        } catch (CancellationException exception) {
            return cancellationResult(token);
        } catch (ExecutionException exception) {
            cancellationResult = cancellationResultIfRequested(token);
            if (cancellationResult != null) {
                return cancellationResult;
            }
            return EasyResult.empty();
        } finally {
            executor.shutdownNow();
        }
    }

    public int randomId() {
        return ThreadLocalRandom.current().nextInt(1, 11);
    }

    private EasyResult<CreateExampleAdapter> cancellationResultIfRequested(CancellationToken token) {
        return token.isCancellationRequested() ? cancellationResult(token) : null;
    }

    private EasyResult<CreateExampleAdapter> cancellationResult(CancellationToken token) {
        CancellationReason reason = token.cancellationReason().orElse(CancellationReason.INTERRUPTED);
        int status = reason == CancellationReason.TIMEOUT ? REQUEST_TIMEOUT_STATUS : CLIENT_CLOSED_STATUS;
        return EasyResult.failure(status, List.of());
    }

    private long timeoutMillis(Duration timeout) {
        return Math.max(1L, timeout.toMillis());
    }
}
