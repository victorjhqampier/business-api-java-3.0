package com.arify.application.usecases.exampleusecase;

import com.arify.application.adapters.CreateExampleAdapter;
import com.arify.application.adapters.ExampleRequestAdapter;
import com.arify.application.adapters.validations.ExampleRequestAdapterValidator;
import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapterValidator;
import com.arify.application.internals.adapters.ValidationResultAdapter;
import com.arify.application.internals.executors.EasyResult;
import com.arify.application.internals.executors.FluentValidationExecutor;
import com.arify.application.ports.ExamplePort;
import com.arify.domain.commons.CancellationToken;
import com.arify.domain.containers.cachelibraryservice.CacheLibraryService;
import com.arify.domain.entities.FakeApiEntity;
import com.arify.domain.interfaces.IFakeApiInfrastructure;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

// Implement el puerto de entrada ExamplePort.
// Orquesta la lógica de negocio y validación, sin manejar excepciones de infraestructura.
// Siguiendo el patrón C#, las excepciones fluyen hacia el controlador.
public class ExampleUseCase implements ExamplePort {
    public static final int VALIDATION_FAILED_STATUS = 422;
    public static final int EXAMPLE_AGE = 5;
    private static final TraceIdentifierAdapterValidator TRACE_IDENTIFIER_VALIDATOR = new TraceIdentifierAdapterValidator();
    private static final ExampleRequestAdapterValidator EXAMPLE_REQUEST_VALIDATOR = new ExampleRequestAdapterValidator();

    // Equivalente a una dependencia inyectada por constructor en C# .NET.
    // Ejemplo C#: private readonly IFakeApiInfrastructure _fakeApi;
    public final IFakeApiInfrastructure fakeApi;

    // Servicio de cache inyectado (Singleton que encapsula el provider de cache).
    // Ejemplo de uso: cacheLibrary.forKey("key").useStrategy(...).resolveAsync(...)
    public final CacheLibraryService cacheLibrary;

    // Executor Java puro para continuaciones del caso de uso.
    // El composition root decide si lo implementa con virtual threads u otra estrategia.
    public final ExecutorService myThreadExec;

    public ExampleUseCase(IFakeApiInfrastructure fakeApi, CacheLibraryService cacheLibrary, ExecutorService executor) {
        this.fakeApi = fakeApi;
        this.cacheLibrary = cacheLibrary;
        this.myThreadExec = executor;
    }

    @Override
    public CompletableFuture<EasyResult<CreateExampleAdapter>> getDataAsync(
            TraceIdentifierAdapter headers, 
            ExampleRequestAdapter exampleRequest, 
            CancellationToken token) {
        
        // Equivalente a FluentValidation en C# .NET.
        // Primero validamos headers antes de ejecutar lógica de negocio.
        List<ValidationResultAdapter> traceValidationErrors = FluentValidationExecutor.execute(headers, TRACE_IDENTIFIER_VALIDATOR);
        if (!traceValidationErrors.isEmpty()) {
            return CompletableFuture.completedFuture(
                    EasyResult.failure(VALIDATION_FAILED_STATUS, traceValidationErrors)
            );
        }

        // Validamos el body del request
        List<ValidationResultAdapter> requestValidationErrors = FluentValidationExecutor.execute(
                exampleRequest, 
                EXAMPLE_REQUEST_VALIDATOR);
        if (!requestValidationErrors.isEmpty()) {
            return CompletableFuture.completedFuture(
                    EasyResult.failure(VALIDATION_FAILED_STATUS, requestValidationErrors)
            );
        }

        // Las interfaces de dominio ya retornan CompletableFuture; no envolvemos llamadas no bloqueantes en supplyAsync.
        CompletableFuture<Optional<FakeApiEntity>> result1Task = fakeApi.getUserAsync(randomId(), token);
        CompletableFuture<Optional<FakeApiEntity>> result2Task = fakeApi.getTitleAsync(randomId(), token);

        // Equivalente a Task.WhenAll(result1Task, result2Task).WaitAsync(timeout, cancellationToken) en C#.
        // allOf() espera a que ambas tareas completen, y orTimeout() aplica el timeout del token.
        // Si alguna tarea lanza una excepción (TimeoutException, CancellationException), esta fluirá
        // hacia el controlador sin ser capturada aquí, manteniendo la capa de aplicación pura.
        return CompletableFuture.allOf(result1Task, result2Task)
                .orTimeout(token.remainingTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .thenApplyAsync(ignored -> {
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
                }, myThreadExec);
        
        // Ejemplo de uso de CacheLibraryService (patrón fluido):
        // String cacheKey = "example-user-" + exampleRequest.name();
        // return cacheLibrary.forKey(cacheKey)
        //         .useStrategy(CacheStrategy.CACHE_THEN_SOURCE_AND_STORE)
        //         .withTtl(Duration.ofMinutes(5))
        //         .resolveAsync(token -> fetchDataFromExternalApis(exampleRequest, token), CreateExampleAdapter.class, token);
    }

    private int randomId() {
        return ThreadLocalRandom.current().nextInt(1, 15);
    }
}
