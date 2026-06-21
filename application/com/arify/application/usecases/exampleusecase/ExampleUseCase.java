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
    // Equivale a una dependencia inyectada por constructor en C# .NET.
    // Ejemplo C#: private readonly IFakeApiInfrastructure _fakeApi;
    public final IFakeApiInfrastructure fakeApi;

    // Equivale al ThreadPool / TaskScheduler controlado en C# .NET.
    public final ExecutorService executor;

    public ExampleUseCase(IFakeApiInfrastructure fakeApi, ExecutorService executor) {
        this.fakeApi = fakeApi;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<EasyResult<CreateExampleAdapter>> getDataAsync(TraceIdentifierAdapter headers, ExampleRequestAdapter exampleRequest, CancellationToken token) {
        // Equivale a FluentValidation en C# .NET.
        // Primero validamos headers antes de ejecutar lógica de negocio.
        List<ValidationResultAdapter> traceValidationErrors = FluentValidationExecutor.execute(headers, TraceIdentifierAdapterValidator::new);
        if (!traceValidationErrors.isEmpty()) {
            return CompletableFuture.completedFuture(
                EasyResult.failure(422, traceValidationErrors)
            );
        }

        List<ValidationResultAdapter> requestValidationErrors = FluentValidationExecutor.execute(exampleRequest, ExampleRequestAdapterValidator::new);
        if (!requestValidationErrors.isEmpty()) {
            return CompletableFuture.completedFuture(
                    EasyResult.failure(422, requestValidationErrors)
            );
        }

        // Equivale a Task.Run(...) en C# .NET usando un scheduler/executor controlado.
        CompletableFuture<Optional<FakeApiEntity>> result1Task = CompletableFuture.supplyAsync(
            () -> fakeApi.getUser(randomId(), token),
            executor
        );

        // Equivale a otra Task.Run(...) independiente para ejecutar en paralelo.
        CompletableFuture<Optional<FakeApiEntity>> result2Task = CompletableFuture.supplyAsync(
            () -> fakeApi.getTitle(randomId(), token),
            executor
        );

        // Equivale a Task.WhenAll(result1Task, result2Task) en C# .NET.
        return CompletableFuture
                .allOf(result1Task, result2Task)
                // Equivale conceptualmente a aplicar timeout tipo:
                // await Task.WhenAll(...).WaitAsync(timeout, cancellationToken)
                .orTimeout(token.remainingTimeout().toMillis(), TimeUnit.MILLISECONDS)
                // Equivale a continuar luego del await en C# .NET.
                .thenApply(ignored -> {                    
                    // Equivale a leer Result después de que Task.WhenAll ya terminó.
                    // join() aquí no debería bloquear porque allOf ya completó correctamente.
                    Optional<FakeApiEntity> result1 = result1Task.join();
                    Optional<FakeApiEntity> result2 = result2Task.join();

                    if (result1.isEmpty() || result2.isEmpty()) {
                        return EasyResult.empty();
                    }

                    // Equivale a mapear entidades/respuestas a un Response DTO en C# .NET.
                    CreateExampleAdapter result = new CreateExampleAdapter(
                            result1.get().title(),
                            EXAMPLE_AGE,
                            result2.get().title()
                    );

                    return EasyResult.success(result);
                });
    }
}