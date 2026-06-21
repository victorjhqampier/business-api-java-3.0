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
import com.arify.domain.commons.CancellationToken;
import com.arify.domain.entities.FakeApiEntity;
import com.arify.domain.interfaces.IFakeApiInfrastructure;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

// Implementa el puerto de entrada ExamplePort.
// Orquesta la lógica de negocio y validación, sin manejar excepciones de infraestructura.
// Siguiendo el patrón C#, las excepciones fluyen hacia el controlador.
public class ExampleUseCase implements ExamplePort {
    public static final int VALIDATION_FAILED_STATUS = 422;
    public static final int EXAMPLE_AGE = 5;

    // Equivalente a una dependencia inyectada por constructor en C# .NET.
    // Ejemplo C#: private readonly IFakeApiInfrastructure _fakeApi;
    public final IFakeApiInfrastructure fakeApi;

    // Equivalente al ThreadPool / TaskScheduler controlado en C# .NET.
    // En Java 21, usamos un ExecutorService de Virtual Threads para operaciones I/O-bound.
    public final ExecutorService executor;

    public ExampleUseCase(IFakeApiInfrastructure fakeApi, ExecutorService executor) {
        this.fakeApi = fakeApi;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<EasyResult<CreateExampleAdapter>> getDataAsync(
            TraceIdentifierAdapter headers, 
            ExampleRequestAdapter exampleRequest, 
            CancellationToken token) {
        
        // Equivalente a FluentValidation en C# .NET.
        // Primero validamos headers antes de ejecutar lógica de negocio.
        List<ValidationResultAdapter> traceValidationErrors = FluentValidationExecutor.execute(
                headers, 
                TraceIdentifierAdapterValidator::new);
        if (!traceValidationErrors.isEmpty()) {
            return CompletableFuture.completedFuture(
                    EasyResult.failure(VALIDATION_FAILED_STATUS, traceValidationErrors)
            );
        }

        // Validamos el body del request
        List<ValidationResultAdapter> requestValidationErrors = FluentValidationExecutor.execute(
                exampleRequest, 
                ExampleRequestAdapterValidator::new);
        if (!requestValidationErrors.isEmpty()) {
            return CompletableFuture.completedFuture(
                    EasyResult.failure(VALIDATION_FAILED_STATUS, requestValidationErrors)
            );
        }

        // Equivalente a Task.Run(...) en C# .NET usando un scheduler/executor controlado.
        // Usamos el ExecutorService de Virtual Threads para ejecutar operaciones I/O-bound.
        CompletableFuture<Optional<FakeApiEntity>> result1Task = fakeApi.getUserAsync(randomId(), token);
        CompletableFuture<Optional<FakeApiEntity>> result2Task = fakeApi.getTitleAsync(randomId(), token);

        // Equivalente a Task.WhenAll(result1Task, result2Task).WaitAsync(timeout, cancellationToken) en C#.
        // allOf() espera a que ambas tareas completen, y orTimeout() aplica el timeout del token.
        // Si alguna tarea lanza una excepción (TimeoutException, CancellationException), esta fluirá
        // hacia el controlador sin ser capturada aquí, manteniendo la capa de aplicación pura.
        return CompletableFuture.allOf(result1Task, result2Task)
                .orTimeout(token.remainingTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .thenApply(ignored -> {
                    // Equivalente a leer Result después de que Task.WhenAll ya terminó.
                    // join() aquí no bloquea porque allOf ya completó exitosamente.
                    Optional<FakeApiEntity> result1 = result1Task.join();
                    Optional<FakeApiEntity> result2 = result2Task.join();

                    if (result1.isEmpty() || result2.isEmpty()) {
                        return EasyResult.empty();
                    }

                    // Equivalente a mapear entidades/respuestas a un Response DTO en C# .NET.
                    CreateExampleAdapter result = new CreateExampleAdapter(
                            result1.get().title(),
                            EXAMPLE_AGE,
                            result2.get().title()
                    );

                    return EasyResult.success(result);
                });
    }

    private int randomId() {
        return ThreadLocalRandom.current().nextInt(1, 15);
    }
}