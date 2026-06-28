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
import com.arify.domain.containers.cachelibraryservice.ICacheInfrastructure;
import com.arify.domain.entities.FakeApiEntity;
import com.arify.domain.interfaces.IFakeApiInfrastructure;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Implementación de ExampleCachePort usando CacheLibraryService (Redis).
 * Sigue el patrón de ExampleUseCase.java y la referencia C# ExampleCacheCase.cs.
 */
public class ExampleRedisUsecase implements ExampleCachePort {
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration CACHE_JITTER = Duration.ofMinutes(2);

    private final IFakeApiInfrastructure fakeApiQuery;
    private final CacheLibraryService cacheLibrary;
    private final ExecutorService executor;

    public ExampleRedisUsecase(IFakeApiInfrastructure fakeApiQuery, ICacheInfrastructure redisProvider, ExecutorService executor) {
        this.fakeApiQuery = fakeApiQuery;
        this.cacheLibrary = new CacheLibraryService(redisProvider);
        this.executor = executor;
    }

    @Override
    public CompletableFuture<EasyResult<RetrieveExampleAdapter>> showExampleAsync(TraceIdentifierAdapter header, CancellationToken token) {
        
        // 1. Validación de headers (Hot Path - Reutilizando validador estático)
        List<ValidationResultAdapter> errors = FluentValidationExecutor.execute(header, new TraceIdentifierAdapterValidator());
        if (!errors.isEmpty()) {
            return CompletableFuture.completedFuture(EasyResult.failure(422, errors));
        }

        // 2. Lógica de negocio (determinista para este ejemplo)
        int apiValue = ThreadLocalRandom.current().nextInt(1, 4);
        int productValue = ThreadLocalRandom.current().nextInt(1, 4);

        var myConfig = Map.of(
                "CACHE_KEY_A", "trx-a-" + header.channelIdentifier() + apiValue,
                "CACHE_KEY_B", "trx-b-" + header.channelIdentifier() + productValue
        );

        // 3. Resolución con Cache (Patrón Inline - Zero Allocation de helpers)
        // Usamos tipos concretos (FakeApiEntity.class) y manejamos el Optional inline.
        CompletableFuture<FakeApiEntity> apiResultTask = cacheLibrary.forKey(myConfig.get("CACHE_KEY_A"))
                .useStrategy(CacheStrategy.CACHE_THEN_SOURCE_AND_STORE)
                .withTtl(CACHE_TTL, CACHE_JITTER)
                .resolveAsync(
                        cacheToken -> fakeApiQuery.getUserAsync(apiValue, cacheToken).thenApply(opt -> opt.orElse(null)),
                        FakeApiEntity.class,
                        token);

        CompletableFuture<FakeApiEntity> productResultTask = cacheLibrary.forKey(myConfig.get("CACHE_KEY_B"))
                .useStrategy(CacheStrategy.CACHE_THEN_SOURCE_AND_STORE)
                .withTtl(CACHE_TTL, CACHE_JITTER)
                .resolveAsync(
                        cacheToken -> fakeApiQuery.getTitleAsync(productValue, cacheToken).thenApply(opt -> opt.orElse(null)),
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
                    var result = new RetrieveExampleAdapter(
                            productResult.title(),
                            apiResult.title()
                    );

                    return EasyResult.success(result);
                }, executor);
    }
}
