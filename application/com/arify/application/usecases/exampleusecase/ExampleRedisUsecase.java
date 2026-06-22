package com.arify.application.usecases.exampleusecase;

import com.arify.application.adapters.RetrieveExampleAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapterValidator;
import com.arify.application.internals.adapters.ValidationResultAdapter;
import com.arify.application.internals.executors.EasyResult;
import com.arify.application.internals.executors.FluentValidationExecutor;
import com.arify.application.ports.ExampleCachePort;
import com.arify.domain.commons.CancellationToken;
import com.arify.domain.containers.cachelibraryservice.CacheLibraryService;
import com.arify.domain.containers.cachelibraryservice.CacheStrategy;
import com.arify.domain.entities.FakeApiEntity;
import com.arify.domain.interfaces.IFakeApiInfrastructure;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Implementación de ExampleCachePort usando CacheLibraryService (Redis).
 * Sigue el patrón de ExampleUseCase.java y la referencia C# ExampleCacheCase.cs.
 */
public class ExampleRedisUsecase implements ExampleCachePort {
    private static final int VALIDATION_FAILED_STATUS = 422;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration CACHE_JITTER = Duration.ofMinutes(2);
    private static final TraceIdentifierAdapterValidator TRACE_IDENTIFIER_VALIDATOR = new TraceIdentifierAdapterValidator();

    private final IFakeApiInfrastructure fakeApi;
    private final CacheLibraryService cacheLibrary;
    private final ExecutorService executor;

    public ExampleRedisUsecase(
            IFakeApiInfrastructure fakeApi,
            CacheLibraryService cacheLibrary,
            ExecutorService executor) {
        this.fakeApi = fakeApi;
        this.cacheLibrary = cacheLibrary;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<EasyResult<RetrieveExampleAdapter>> showExampleAsync(
            TraceIdentifierAdapter header,
            CancellationToken token) {
        
        // 1. Validación de headers (Hot Path - Reutilizando validador estático)
        List<ValidationResultAdapter> errors = FluentValidationExecutor.execute(header, TRACE_IDENTIFIER_VALIDATOR);
        if (!errors.isEmpty()) {
            return CompletableFuture.completedFuture(EasyResult.failure(VALIDATION_FAILED_STATUS, errors));
        }

        // 2. Lógica de negocio (determinista para este ejemplo)
        int apiValue = ThreadLocalRandom.current().nextInt(1, 4);
        int productValue = ThreadLocalRandom.current().nextInt(1, 4);

        // 3. Resolución con Cache (Patrón Inline - Zero Allocation de helpers)
        // Usamos tipos concretos (FakeApiEntity.class) y manejamos el Optional inline.
        CompletableFuture<FakeApiEntity> apiResultTask = cacheLibrary
                .forKey("trx-" + header.channelIdentifier() + "-fake-api-title-" + apiValue)
                .useStrategy(CacheStrategy.CACHE_THEN_SOURCE_AND_STORE)
                .withTtl(CACHE_TTL, CACHE_JITTER)
                .resolveAsync(
                        cacheToken -> fakeApi.getUserAsync(apiValue, cacheToken)
                                .thenApply(opt -> opt.orElse(null)),
                        FakeApiEntity.class,
                        token);

        CompletableFuture<FakeApiEntity> productResultTask = cacheLibrary
                .forKey("trx-" + header.channelIdentifier() + "-fake-api-product-" + productValue)
                .useStrategy(CacheStrategy.CACHE_THEN_SOURCE_AND_STORE)
                .withTtl(CACHE_TTL, CACHE_JITTER)
                .resolveAsync(
                        cacheToken -> fakeApi.getTitleAsync(productValue, cacheToken)
                                .thenApply(opt -> opt.orElse(null)),
                        FakeApiEntity.class,
                        token);

        // 4. Orquestación y composición (Siguiendo ExampleUseCase.java)
        return CompletableFuture.allOf(apiResultTask, productResultTask)
                .orTimeout(token.remainingTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .thenApplyAsync(ignored -> {
                    // join() es seguro aquí porque allOf ya completó.
                    FakeApiEntity apiResult = apiResultTask.join();
                    FakeApiEntity productResult = productResultTask.join();

                    if (apiResult == null || productResult == null) {
                        return EasyResult.empty();
                    }

                    // 5. Mapeo a Response DTO
                    RetrieveExampleAdapter result = new RetrieveExampleAdapter(
                            productResult.title(),
                            apiResult.title());

                    return EasyResult.success(result);
                }, executor);
    }
}
