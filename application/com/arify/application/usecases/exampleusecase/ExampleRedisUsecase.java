package com.arify.application.usecases.exampleusecase;

import com.arify.application.adapters.RetrieveExampleAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapterValidator;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class ExampleRedisUsecase implements ExampleCachePort {

    private final IFakeApiInfrastructure _fakeApiQuery;
    private final CacheLibraryService _cacheLibrary;
    private static final TraceIdentifierAdapterValidator _traceIdentifierAdapterValidator =  new TraceIdentifierAdapterValidator();

    public ExampleRedisUsecase(IFakeApiInfrastructure fakeApiQuery, ICacheInfrastructure redisProvider) {
        this._fakeApiQuery = fakeApiQuery;
        this._cacheLibrary = new CacheLibraryService(redisProvider);
    }

    @Override
    public EasyResult<RetrieveExampleAdapter> showExampleAsync(TraceIdentifierAdapter header, CancellationToken ctx) {
        
        // 1. Validación de headers (Hot Path - Reutilizando validador estático)
        var errors = FluentValidationExecutor.execute(header, _traceIdentifierAdapterValidator);
        if (!errors.isEmpty()) {
            return EasyResult.failure(422, errors);
        }

        // 2. Lógica de negocio (determinista para este ejemplo)
        int apiValue = ThreadLocalRandom.current().nextInt(1, 4);
        int productValue = ThreadLocalRandom.current().nextInt(1, 4);

        String cacheKeyA = "trx-a-" + header.channelIdentifier() + apiValue;
        String cacheKeyB = "trx-b-" + header.channelIdentifier() + productValue;

        // 3. Resolución con Cache (Patrón Inline - Zero Allocation de helpers)
        // Usamos tipos concretos (FakeApiEntity.class) y manejamos el Optional inline.
        CompletableFuture<FakeApiEntity> apiResultTask = _cacheLibrary.forKey(cacheKeyA)
                .useStrategy(CacheStrategy.CACHE_THEN_SOURCE_AND_STORE)
                .withTtl(Duration.ofMinutes(3), Duration.ofSeconds(30))
                .resolveAsync(
                        cacheToken -> _fakeApiQuery.getUserAsync(apiValue, cacheToken).thenApply(opt -> opt.orElse(null)),
                        FakeApiEntity.class,
                        ctx
                );

        CompletableFuture<FakeApiEntity> productResultTask = _cacheLibrary.forKey(cacheKeyB)
                .useStrategy(CacheStrategy.CACHE_THEN_SOURCE_AND_STORE)
                .withTtl(Duration.ofMinutes(3), Duration.ofSeconds(30))
                .resolveAsync(
                        cacheToken -> _fakeApiQuery.getTitleAsync(productValue, cacheToken).thenApply(opt -> opt.orElse(null)),
                        FakeApiEntity.class,
                        ctx
                );

        // 4. Orquestación y composición (Siguiendo ExampleUseCase.java)
        CompletableFuture.allOf(apiResultTask, productResultTask)
                .orTimeout(ctx.remainingTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .join();

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
    }
}
