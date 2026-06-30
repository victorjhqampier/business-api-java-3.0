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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class ExampleIdempotencyUsecase implements ExampleIdempotencyPort {
    private static final int VALIDATION_FAILED_STATUS = 422;
    private static final int CONFLICT_STATUS = 409;
    private static final String MICROSERVICE_ID = "1234";
    private static final Duration RESERVE_TTL = Duration.ofMinutes(1);
    private static final Duration RESERVE_JITTER = Duration.ofSeconds(10);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(15);
    private static final Duration IDEMPOTENCY_JITTER = Duration.ofSeconds(30);

    private static final TraceIdentifierAdapterValidator TRACE_IDENTIFIER_VALIDATOR = new TraceIdentifierAdapterValidator();
    private static final ExamplePreRequestAdapterValidator PRE_REQUEST_VALIDATOR = new ExamplePreRequestAdapterValidator();

    private final IFakeApiInfrastructure _fakeApi;
    private final CacheLibraryService _cacheLibrary;

    public ExampleIdempotencyUsecase(IFakeApiInfrastructure fakeApi, CacheLibraryService cacheLibrary) {
        this._fakeApi = fakeApi;
        this._cacheLibrary = cacheLibrary;
    }

    @Override
    public EasyResult<RetrieveExampleAdapter> getDataAsync(TraceIdentifierAdapter header, CancellationToken ctx) {
        var errors = FluentValidationExecutor.execute(header, TRACE_IDENTIFIER_VALIDATOR);
        if (!errors.isEmpty()) {
            return EasyResult.failure(VALIDATION_FAILED_STATUS, errors);
        }

        String cacheKey = "get-" + MICROSERVICE_ID + header.messageIdentifier();
        String owner = UUID.randomUUID().toString();

        boolean reserved = _cacheLibrary.forKey(cacheKey)
                .withOwner(owner)
                .withTtl(RESERVE_TTL, RESERVE_JITTER)
                .ensureProviderAvailable(true)
                .tryReserveAsync(ctx)
                .join();

        if (!reserved) {
            return EasyResult.failure(
                    CONFLICT_STATUS,
                    List.of(new ValidationResultAdapter(
                            "12345",
                            "Message Identifier already exists for GET idempotency",
                            "MessageIdentifier")));
        }

        int apiValue = ThreadLocalRandom.current().nextInt(1, 4);
        int productValue = ThreadLocalRandom.current().nextInt(1, 4);

        CompletableFuture<FakeApiEntity> apiResultTask = _cacheLibrary
                .forKey("trx-title-" + apiValue)
                .useStrategy(CacheStrategy.CACHE_THEN_SOURCE_AND_STORE)
                .withTtl(Duration.ofMinutes(15), Duration.ofMinutes(2))
                .resolveAsync(
                        cacheToken -> _fakeApi.getUserAsync(apiValue, cacheToken).thenApply(opt -> opt.orElse(null)),
                        FakeApiEntity.class,
                        ctx);

        CompletableFuture<FakeApiEntity> productResultTask = _cacheLibrary
                .forKey("trx-product-" + productValue)
                .useStrategy(CacheStrategy.CACHE_THEN_SOURCE_AND_STORE)
                .withTtl(Duration.ofMinutes(15), Duration.ofMinutes(2))
                .resolveAsync(
                        cacheToken -> _fakeApi.getTitleAsync(productValue, cacheToken).thenApply(opt -> opt.orElse(null)),
                        FakeApiEntity.class,
                        ctx);

        CompletableFuture.allOf(apiResultTask, productResultTask)
                .orTimeout(ctx.remainingTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .join();

        FakeApiEntity apiResult = apiResultTask.join();
        FakeApiEntity productResult = productResultTask.join();

        if (apiResult == null || productResult == null) {
            _cacheLibrary.forKey(cacheKey).ensureProviderAvailable(true).removeAsync(ctx).join();
            return EasyResult.empty();
        }

        var result = new RetrieveExampleAdapter(productResult.title(), apiResult.title());
        var preview = new ExamplePostValidIdemAdapter(
                header.channelIdentifier(),
                header.deviceIdentifier(),
                result);

        boolean completed = _cacheLibrary.forKey(cacheKey)
                .withOwner(owner)
                .withTtl(IDEMPOTENCY_TTL, IDEMPOTENCY_JITTER)
                .ensureProviderAvailable(true)
                .tryCompleteAsync(preview, ctx)
                .join();

        if (!completed) {
            _cacheLibrary.forKey(cacheKey).ensureProviderAvailable(true).removeAsync(ctx).join();
            return EasyResult.failure(
                    CONFLICT_STATUS,
                    List.of(new ValidationResultAdapter(
                            "12348",
                            "Unable to complete idempotency preview",
                            "MessageIdentifier")));
        }

        return EasyResult.success(result);
    }

    @Override
    public EasyResult<RetrieveExampleAdapter> setIdempotencyAsync(TraceIdentifierAdapter header, ExamplePreRequestAdapter body, CancellationToken ctx) {
        var errors = FluentValidationExecutor.execute(header, TRACE_IDENTIFIER_VALIDATOR);
        if (!errors.isEmpty()) {
            return EasyResult.failure(VALIDATION_FAILED_STATUS, errors);
        }

        errors = FluentValidationExecutor.execute(body, PRE_REQUEST_VALIDATOR);
        if (!errors.isEmpty()) {
            return EasyResult.failure(VALIDATION_FAILED_STATUS, errors);
        }

        if (ctx.isCancellationRequested()) {
            throw new CancellationException("Operation cancelled");
        }

        String previewCacheKey = "get-" + body.previewMessageIdentification();
        String cacheKey = "post-" + header.messageIdentifier();
        String owner = UUID.randomUUID().toString();

        RetrieveExampleAdapter cachedData = _cacheLibrary.forKey(cacheKey)
                .ensureProviderAvailable(true)
                .getCreatedAsync(RetrieveExampleAdapter.class, true, ctx)
                .join();

        if (cachedData != null) {
            return EasyResult.success(cachedData);
        }

        boolean reserved = _cacheLibrary.forKey(cacheKey)
                .withOwner(owner)
                .withTtl(RESERVE_TTL, RESERVE_JITTER)
                .ensureProviderAvailable(true)
                .tryReserveAsync(ctx)
                .join();

        if (!reserved) {
            RetrieveExampleAdapter concurrentResult = _cacheLibrary.forKey(cacheKey)
                    .ensureProviderAvailable(true)
                    .getCreatedAsync(RetrieveExampleAdapter.class, true, ctx)
                    .join();

            if (concurrentResult != null) {
                return EasyResult.success(concurrentResult);
            }

            return EasyResult.failure(
                    CONFLICT_STATUS,
                    List.of(new ValidationResultAdapter(
                            "12347",
                            "Idempotency request is already in progress",
                            "MessageIdentifier")));
        }

        ExamplePostValidIdemAdapter previewData = _cacheLibrary.forKey(previewCacheKey)
                .useStrategy(CacheStrategy.CACHE_ONLY_THEN_CLOSE)
                .ensureProviderAvailable(true)
                .resolveAsync(ExamplePostValidIdemAdapter.class, ctx)
                .join();

        if (previewData == null
                || !Objects.equals(previewData.channelId(), header.channelIdentifier())
                || !Objects.equals(previewData.deviceId(), header.deviceIdentifier())) {
            _cacheLibrary.forKey(cacheKey).ensureProviderAvailable(true).removeAsync(ctx).join();
            return EasyResult.failure(
                    VALIDATION_FAILED_STATUS,
                    List.of(new ValidationResultAdapter(
                            "12346",
                            "Invalid preview message identifier or trace data does not match with preview data",
                            "MessageIdentifier")));
        }

        RetrieveExampleAdapter response = previewData.response();
        RetrieveExampleAdapter result = new RetrieveExampleAdapter(
                response == null || response.ping() == null ? "" : response.ping(),
                Instant.now().toString());

        boolean completed = _cacheLibrary.forKey(cacheKey)
                .withOwner(owner)
                .withTtl(IDEMPOTENCY_TTL, IDEMPOTENCY_JITTER)
                .tryCompleteAsync(result, ctx)
                .join();

        if (!completed) {
            _cacheLibrary.forKey(cacheKey).ensureProviderAvailable(true).removeAsync(ctx).join();
            return EasyResult.failure(
                    CONFLICT_STATUS,
                    List.of(new ValidationResultAdapter(
                            "12348",
                            "Unable to complete idempotency process",
                            "MessageIdentifier")));
        }

        return EasyResult.success(result);
    }
}
