package com.arify.application.usecases.exampleusecase;

import com.arify.application.adapters.ExamplePostValidIdemAdapter;
import com.arify.application.adapters.ExamplePreRequestAdapter;
import com.arify.application.adapters.RetrieveExampleAdapter;
import com.arify.application.adapters.validations.ExamplePreRequestAdapterValidator;
import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapterValidator;
import com.arify.application.internals.adapters.ValidationResultAdapter;
import com.arify.application.internals.executors.EasyResult;
import com.arify.application.internals.executors.FluentValidationExecutor;
import com.arify.application.ports.ExampleIdempotencyPort;
import com.arify.domain.commons.CancellationToken;
import com.arify.domain.containers.cachelibraryservice.CacheLibraryService;
import com.arify.domain.containers.cachelibraryservice.CacheStrategy;
import com.arify.domain.entities.FakeApiEntity;
import com.arify.domain.interfaces.IFakeApiInfrastructure;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Implementación de ExampleIdempotencyPort usando CacheLibraryService.
 * Sigue la lógica de referencia C# ExampleIdempotencyCase.cs y el patrón de ExampleUseCase.java.
 */
public class ExampleIdempotencyUsecase implements ExampleIdempotencyPort {
    private static final int VALIDATION_FAILED_STATUS = 422;
    private static final int CONFLICT_STATUS = 409;
    
    private static final Duration RESERVE_TTL = Duration.ofMinutes(1);
    private static final Duration RESERVE_JITTER = Duration.ofSeconds(10);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration CACHE_JITTER = Duration.ofMinutes(2);
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(4);
    private static final Duration PREVIEW_JITTER = Duration.ofMinutes(1);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(15);
    private static final Duration IDEMPOTENCY_JITTER = Duration.ofSeconds(30);

    private static final TraceIdentifierAdapterValidator TRACE_IDENTIFIER_VALIDATOR = new TraceIdentifierAdapterValidator();
    private static final ExamplePreRequestAdapterValidator PRE_REQUEST_VALIDATOR = new ExamplePreRequestAdapterValidator();

    private final IFakeApiInfrastructure fakeApi;
    private final CacheLibraryService cacheLibrary;
    private final ExecutorService executor;

    public ExampleIdempotencyUsecase(
            IFakeApiInfrastructure fakeApi,
            CacheLibraryService cacheLibrary,
            ExecutorService executor) {
        this.fakeApi = fakeApi;
        this.cacheLibrary = cacheLibrary;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<EasyResult<RetrieveExampleAdapter>> getDataAsync(TraceIdentifierAdapter header, CancellationToken token) {
        // 1. Validación de headers
        List<ValidationResultAdapter> errors = FluentValidationExecutor.execute(header, TRACE_IDENTIFIER_VALIDATOR);
        if (!errors.isEmpty()) {
            return CompletableFuture.completedFuture(EasyResult.failure(VALIDATION_FAILED_STATUS, errors));
        }

        String cacheKey = "get-" + header.messageIdentifier();
        String owner = UUID.randomUUID().toString();

        // 2. Reserva atómica
        return cacheLibrary.forKey(cacheKey)
                .withOwner(owner)
                .withTtl(RESERVE_TTL, RESERVE_JITTER)
                .ensureProviderAvailable(true)
                .tryReserveAsync(token)
                .thenCompose(reserved -> {
                    if (!reserved) {
                        return CompletableFuture.completedFuture(EasyResult.failure(
                                VALIDATION_FAILED_STATUS,
                                List.of(new ValidationResultAdapter(
                                        "12345",
                                        "Message Identifier already exists for GET idempotency",
                                        "MessageIdentifier"))));
                    }

                    // 3. Inicio de lógica de negocio (en paralelo con cache-through)
                    int apiValue = ThreadLocalRandom.current().nextInt(1, 4);
                    int productValue = ThreadLocalRandom.current().nextInt(1, 4);

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

                    return CompletableFuture.allOf(apiResultTask, productResultTask)
                            .orTimeout(token.remainingTimeout().toMillis(), TimeUnit.MILLISECONDS)
                            .thenComposeAsync(ignored -> {
                                FakeApiEntity apiResult = apiResultTask.join();
                                FakeApiEntity productResult = productResultTask.join();

                                if (apiResult == null || productResult == null) {
                                    return CompletableFuture.completedFuture(EasyResult.empty());
                                }

                                // 4. Fin de lógica de negocio y preparación de respuesta
                                RetrieveExampleAdapter result = new RetrieveExampleAdapter(
                                        productResult.title(),
                                        apiResult.title());

                                ExamplePostValidIdemAdapter preview = new ExamplePostValidIdemAdapter(
                                        header.channelIdentifier(),
                                        header.deviceIdentifier(),
                                        result);

                                // 5. Completar reserva atómicamente
                                return cacheLibrary.forKey(cacheKey)
                                        .withOwner(owner)
                                        .withTtl(PREVIEW_TTL, PREVIEW_JITTER)
                                        .ensureProviderAvailable(true)
                                        .tryCompleteAsync(preview, token)
                                        .thenApply(ignoredComplete -> EasyResult.success(result));
                            }, executor);
                });
    }

    @Override
    public CompletableFuture<EasyResult<RetrieveExampleAdapter>> setIdempotencyAsync(
            TraceIdentifierAdapter header,
            ExamplePreRequestAdapter body,
            CancellationToken token) {
        
        // 1. Validación de headers y body
        List<ValidationResultAdapter> errors = FluentValidationExecutor.execute(header, TRACE_IDENTIFIER_VALIDATOR);
        if (!errors.isEmpty()) {
            return CompletableFuture.completedFuture(EasyResult.failure(VALIDATION_FAILED_STATUS, errors));
        }

        errors = FluentValidationExecutor.execute(body, PRE_REQUEST_VALIDATOR);
        if (!errors.isEmpty()) {
            return CompletableFuture.completedFuture(EasyResult.failure(VALIDATION_FAILED_STATUS, errors));
        }

        String cacheKey = "post-" + header.messageIdentifier();
        String previewCacheKey = "get-" + body.previewMessageIdentification();
        String owner = UUID.randomUUID().toString();

        // 2. Verificar si ya existe el resultado (idempotencia)
        return cacheLibrary.forKey(cacheKey)
                .ensureProviderAvailable(true)
                .getCreatedAsync(RetrieveExampleAdapter.class, true, token)
                .thenCompose(cachedData -> {
                    if (cachedData != null) {
                        return CompletableFuture.completedFuture(EasyResult.success(cachedData));
                    }

                    // 3. Reservar llave de idempotencia
                    return cacheLibrary.forKey(cacheKey)
                            .withOwner(owner)
                            .withTtl(RESERVE_TTL, RESERVE_JITTER)
                            .ensureProviderAvailable(true)
                            .tryReserveAsync(token)
                            .thenCompose(reserved -> {
                                if (!reserved) {
                                    // Fallback concurrente: alguien más está procesando o ya terminó
                                    return cacheLibrary.forKey(cacheKey)
                                            .ensureProviderAvailable(true)
                                            .getCreatedAsync(RetrieveExampleAdapter.class, true, token)
                                            .thenApply(concurrentResult -> {
                                                if (concurrentResult != null) {
                                                    return EasyResult.success(concurrentResult);
                                                }
                                                return EasyResult.failure(
                                                        CONFLICT_STATUS,
                                                        List.of(new ValidationResultAdapter(
                                                                "12347",
                                                                "Idempotency request is already in progress",
                                                                "MessageIdentifier")));
                                            });
                                }

                                // 4. Validar preview previo de GetDataAsync
                                return cacheLibrary.forKey(previewCacheKey)
                                        .useStrategy(CacheStrategy.CACHE_ONLY_THEN_CLOSE)
                                        .ensureProviderAvailable(true)
                                        .resolveAsync(ExamplePostValidIdemAdapter.class, token)
                                        .thenCompose(previewData -> {
                                            if (previewData == null
                                                    || !Objects.equals(previewData.channelId(), header.channelIdentifier())
                                                    || !Objects.equals(previewData.deviceId(), header.deviceIdentifier())) {
                                                
                                                // Invalidación por desajuste de datos
                                                return cacheLibrary.forKey(cacheKey)
                                                        .ensureProviderAvailable(true)
                                                        .removeAsync(token)
                                                        .thenApply(ignored -> EasyResult.failure(
                                                                VALIDATION_FAILED_STATUS,
                                                                List.of(new ValidationResultAdapter(
                                                                        "12346",
                                                                        "Invalid preview message identifier or trace data does not match with preview data",
                                                                        "MessageIdentifier"))));
                                            }

                                            // 5. Generar resultado final basado en el preview
                                            RetrieveExampleAdapter response = previewData.response();
                                            RetrieveExampleAdapter result = new RetrieveExampleAdapter(
                                                    response == null || response.ping() == null ? "" : response.ping(),
                                                    Instant.now().toString());

                                            // 6. Completar idempotencia
                                            return cacheLibrary.forKey(cacheKey)
                                                    .withOwner(owner)
                                                    .withTtl(IDEMPOTENCY_TTL, IDEMPOTENCY_JITTER)
                                                    .tryCompleteAsync(result, token)
                                                    .thenCompose(completed -> {
                                                        if (!completed) {
                                                            return cacheLibrary.forKey(cacheKey)
                                                                    .ensureProviderAvailable(true)
                                                                    .removeAsync(token)
                                                                    .thenApply(ignored -> EasyResult.failure(
                                                                            CONFLICT_STATUS,
                                                                            List.of(new ValidationResultAdapter(
                                                                                    "12348",
                                                                                    "Unable to complete idempotency process",
                                                                                    "MessageIdentifier"))));
                                                        }
                                                        return CompletableFuture.completedFuture(EasyResult.success(result));
                                                    });
                                        });
                            });
                });
    }
}
