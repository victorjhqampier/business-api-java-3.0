package com.arify.domain.containers.cachelibraryservice;

import com.arify.domain.commons.CancellationToken;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builder fluido para configurar y resolver operaciones de cache.
 * Implementa las 8 estrategias de resolución con soporte para TTL con jitter,
 * fail-open, timeout de 500ms y manejo de reservas atómicas.
 */
public final class CacheQueryBuilder {
    private static final Logger LOGGER = Logger.getLogger(CacheQueryBuilder.class.getName());
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final Duration PROVIDER_TIMEOUT = Duration.ofMillis(500);
    private static final Duration MINIMUM_TTL = Duration.ofMillis(1);

    private final String id;
    private final ICacheInfrastructure provider;
    private CacheStrategy strategy = CacheStrategy.CACHE_THEN_SOURCE_AND_STORE;
    private Duration ttl = DEFAULT_TTL;
    private Duration ttlJitter = Duration.ZERO;
    private boolean providerRequired = false;
    private String ownerToken = null;

    CacheQueryBuilder(String id, ICacheInfrastructure provider) {
        this.id = id;
        this.provider = provider;
    }

    /**
     * Configura la estrategia de resolución.
     */
    public CacheQueryBuilder useStrategy(CacheStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "strategy cannot be null");
        return this;
    }

    /**
     * Configura el TTL sin jitter.
     */
    public CacheQueryBuilder withTtl(Duration ttl) {
        return withTtl(ttl, Duration.ZERO);
    }

    /**
     * Configura el TTL con jitter anti-stampede.
     * 
     * @param ttl Tiempo de vida base (debe ser positivo).
     * @param ttlJitter Variación aleatoria (debe ser >= 0 y < ttl/3).
     */
    public CacheQueryBuilder withTtl(Duration ttl, Duration ttlJitter) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        if (ttlJitter == null || ttlJitter.isNegative()) {
            throw new IllegalArgumentException("TTL jitter cannot be negative");
        }
        if (!ttlJitter.isZero() && ttlJitter.compareTo(ttl.dividedBy(3)) >= 0) {
            throw new IllegalArgumentException("TTL jitter must be lower than one third of TTL");
        }
        this.ttl = ttl;
        this.ttlJitter = ttlJitter;
        return this;
    }

    /**
     * Configura si el provider es requerido (fail-closed vs fail-open).
     */
    public CacheQueryBuilder ensureProviderAvailable(boolean required) {
        this.providerRequired = required;
        return this;
    }

    /**
     * Configura el token de propietario para validación de reservas.
     */
    public CacheQueryBuilder withOwner(String ownerToken) {
        this.ownerToken = (ownerToken == null || ownerToken.isBlank()) ? null : ownerToken;
        return this;
    }

    /**
     * Resuelve la operación sin loader (solo lectura de cache).
     */
    public <T> CompletableFuture<T> resolveAsync(Class<T> type, CancellationToken cancellationToken) {
        return resolveAsync(null, type, cancellationToken);
    }

    /**
     * Resuelve la operación con loader (estrategia con origen).
     */
    public <T> CompletableFuture<T> resolveAsync(
            Function<CancellationToken, CompletableFuture<T>> loader,
            Class<T> type,
            CancellationToken cancellationToken) {

        return switch (strategy) {
            case CACHE_ONLY -> resolveCacheOnly(type, cancellationToken, false);
            case CACHE_ONLY_REQUIRED -> resolveCacheOnly(type, cancellationToken, true);
            case CACHE_ONLY_THEN_CLOSE -> resolveCacheOnlyThenClose(type, cancellationToken);
            case CACHE_THEN_SOURCE -> resolveCacheThenSource(loader, type, cancellationToken);
            case CACHE_THEN_SOURCE_AND_STORE -> resolveCacheThenSourceAndStore(loader, type, cancellationToken);
            case SOURCE_AND_STORE -> resolveSourceAndStore(loader, type, cancellationToken);
            case STORE_ONLY, STORE_ONLY_OR_RESERVE ->
                    CompletableFuture.failedFuture(new IllegalStateException(
                            strategy + " is not compatible with resolveAsync. Use putAsync instead"));
        };
    }

    /**
     * Almacena dato explícito (compatible con StoreOnly y StoreOnlyOrReserve).
     */
    public <T> CompletableFuture<Boolean> putAsync(T value, CancellationToken cancellationToken) {
        return switch (strategy) {
            case STORE_ONLY -> storeCreatedAsync(value, cancellationToken);
            case STORE_ONLY_OR_RESERVE -> reserveAsync(cancellationToken);
            default -> CompletableFuture.failedFuture(new IllegalStateException(
                    strategy + " is not compatible with putAsync"));
        };
    }

    /**
     * Elimina el registro por id.
     */
    public CompletableFuture<Boolean> removeAsync(CancellationToken cancellationToken) {
        return executeProviderAsync(
                token -> provider.removeAsync(id, token),
                false,
                cancellationToken,
                false);
    }

    /**
     * Verifica si el registro existe.
     */
    public CompletableFuture<Boolean> existsAsync(CancellationToken cancellationToken) {
        return executeProviderAsync(
                token -> provider.existsAsync(id, token),
                false,
                cancellationToken,
                true);
    }

    /**
     * Lee solo si el status es CREATED (con opción de required).
     */
    public <T> CompletableFuture<T> getCreatedAsync(Class<T> type, boolean required, CancellationToken cancellationToken) {
        return readRecordAsync(type, cancellationToken, required)
                .thenApply(record -> (record != null && record.status() == CacheStatus.CREATED) ? record.cachedData() : null);
    }

    /**
     * Lee solo el status del registro.
     */
    public CompletableFuture<CacheStatus> getStatusAsync(boolean required, CancellationToken cancellationToken) {
        return readRecordAsync(Object.class, cancellationToken, required)
                .thenApply(record -> record != null ? record.status() : null);
    }

    /**
     * Intenta crear una reserva (status=STARTED).
     */
    public CompletableFuture<Boolean> tryReserveAsync(CancellationToken cancellationToken) {
        Instant now = Instant.now();
        Duration effectiveTtl = getEffectiveTtl();
        CacheRecord<Object> record = new CacheRecord<>(
                id,
                CacheStatus.STARTED,
                null,
                now.getEpochSecond(),
                now.plus(effectiveTtl).getEpochSecond(),
                ownerToken);
        return createRecordAsync(record, effectiveTtl, cancellationToken);
    }

    /**
     * Intenta completar una reserva (STARTED → CREATED).
     */
    public <T> CompletableFuture<Boolean> tryCompleteAsync(T value, CancellationToken cancellationToken) {
        Instant now = Instant.now();
        Duration effectiveTtl = getEffectiveTtl();
        CacheRecord<T> record = new CacheRecord<>(
                id,
                CacheStatus.CREATED,
                value,
                now.getEpochSecond(),
                now.plus(effectiveTtl).getEpochSecond(),
                ownerToken);
        return updateRecordAsync(record, CacheStatus.STARTED, ownerToken, cancellationToken);
    }

    // --- Estrategias privadas ---

    private <T> CompletableFuture<T> resolveCacheOnly(Class<T> type, CancellationToken ct, boolean required) {
        return readRecordAsync(type, ct, required)
                .thenApply(record -> (record != null && record.status() == CacheStatus.CREATED) ? record.cachedData() : null);
    }

    private <T> CompletableFuture<T> resolveCacheOnlyThenClose(Class<T> type, CancellationToken ct) {
        return readRecordAsync(type, ct, false).thenCompose(record -> {
            if (record == null || record.status() != CacheStatus.CREATED) {
                return CompletableFuture.completedFuture(null);
            }
            CacheRecord<T> closed = new CacheRecord<>(
                    record.id(),
                    CacheStatus.CLOSED,
                    record.cachedData(),
                    record.createdAt(),
                    record.expireIn(),
                    record.ownerToken());
            return updateRecordAsync(closed, CacheStatus.CREATED, null, ct)
                    .thenApply(changed -> changed ? record.cachedData() : null);
        });
    }

    private <T> CompletableFuture<T> resolveCacheThenSource(
            Function<CancellationToken, CompletableFuture<T>> loader,
            Class<T> type,
            CancellationToken ct) {
        return readRecordAsync(type, ct, false).thenCompose(cached -> {
            if (cached != null && cached.status() == CacheStatus.CREATED) {
                return CompletableFuture.completedFuture(cached.cachedData());
            }
            if (loader == null) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "This cache strategy requires a deferred async loader"));
            }
            return loader.apply(ct);
        });
    }

    private <T> CompletableFuture<T> resolveCacheThenSourceAndStore(
            Function<CancellationToken, CompletableFuture<T>> loader,
            Class<T> type,
            CancellationToken ct) {
        return readRecordAsync(type, ct, false).thenCompose(record -> {
            if (record != null && record.status() == CacheStatus.CREATED) {
                return CompletableFuture.completedFuture(record.cachedData());
            }
            if (record != null && (record.status() == CacheStatus.STARTED || record.status() == CacheStatus.CLOSED)) {
                return CompletableFuture.completedFuture(null);
            }
            return reserveAsync(ct).thenCompose(reserved -> {
                if (!reserved) {
                    return readRecordAsync(type, ct, false).thenCompose(concurrent -> {
                        if (concurrent != null && concurrent.status() == CacheStatus.CREATED) {
                            return CompletableFuture.completedFuture(concurrent.cachedData());
                        }
                        if (concurrent != null && (concurrent.status() == CacheStatus.STARTED || concurrent.status() == CacheStatus.CLOSED)) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return loadAndStoreAsync(loader, ct, false);
                    });
                }
                return loadAndStoreAsync(loader, ct, true);
            });
        });
    }

    private <T> CompletableFuture<T> loadAndStoreAsync(
            Function<CancellationToken, CompletableFuture<T>> loader,
            CancellationToken ct,
            boolean reserved) {
        if (loader == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "This cache strategy requires a deferred async loader"));
        }
        return loader.apply(ct).thenCompose(value -> {
            if (value == null) {
                return removeAsync(ct).thenApply(ignored -> null);
            }
            CompletableFuture<Boolean> storeTask = reserved
                    ? tryCompleteAsync(value, ct)
                    : storeCreatedAsync(value, ct);
            return storeTask.thenApply(ignored -> value);
        }).exceptionally(ex -> {
            removeAsync(CancellationToken.withDefault()).join();
            throw (ex instanceof CompletionException) ? (CompletionException) ex : new CompletionException(ex);
        });
    }

    private <T> CompletableFuture<T> resolveSourceAndStore(
            Function<CancellationToken, CompletableFuture<T>> loader,
            Class<T> type,
            CancellationToken ct) {
        if (loader == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "This cache strategy requires a deferred async loader"));
        }
        return loader.apply(ct).thenCompose(value -> {
            if (value == null) {
                return CompletableFuture.completedFuture(null);
            }
            return storeCreatedAsync(value, ct).thenApply(ignored -> value);
        });
    }

    // --- Helpers privados ---

    private <T> CompletableFuture<Boolean> reserveAsync(CancellationToken ct) {
        Instant now = Instant.now();
        Duration effectiveTtl = getEffectiveTtl();
        CacheRecord<T> record = new CacheRecord<>(
                id,
                CacheStatus.STARTED,
                null,
                now.getEpochSecond(),
                now.plus(effectiveTtl).getEpochSecond(),
                ownerToken);
        return createRecordAsync(record, effectiveTtl, ct);
    }

    private <T> CompletableFuture<Boolean> storeCreatedAsync(T value, CancellationToken ct) {
        Instant now = Instant.now();
        Duration effectiveTtl = getEffectiveTtl();
        CacheRecord<T> record = new CacheRecord<>(
                id,
                CacheStatus.CREATED,
                value,
                now.getEpochSecond(),
                now.plus(effectiveTtl).getEpochSecond(),
                null);
        return createRecordAsync(record, effectiveTtl, ct).thenCompose(created -> {
            if (created) {
                return CompletableFuture.completedFuture(true);
            }
            return updateRecordAsync(record, CacheStatus.STARTED, null, ct);
        });
    }

    private <T> CompletableFuture<CacheRecord<T>> readRecordAsync(Class<T> type, CancellationToken ct, boolean required) {
        return executeProviderAsync(
                token -> provider.getAsync(id, type, token),
                null,
                ct,
                required);
    }

    private <T> CompletableFuture<Boolean> createRecordAsync(CacheRecord<T> record, Duration ttl, CancellationToken ct) {
        return executeProviderAsync(
                token -> provider.tryCreateAsync(record, ttl, token),
                false,
                ct,
                false);
    }

    private <T> CompletableFuture<Boolean> updateRecordAsync(
            CacheRecord<T> record,
            CacheStatus expectedStatus,
            String expectedOwner,
            CancellationToken ct) {
        Duration remainingTtl = getRemainingTtl(record);
        if (remainingTtl.isZero() || remainingTtl.isNegative()) {
            return CompletableFuture.completedFuture(false);
        }
        return executeProviderAsync(
                token -> provider.tryUpdateAsync(record, expectedStatus, expectedOwner, remainingTtl, token),
                false,
                ct,
                false);
    }

    private Duration getEffectiveTtl() {
        if (ttlJitter.isZero()) {
            return ttl;
        }
        long jitterNanos = ttlJitter.toNanos();
        long randomNanos = ThreadLocalRandom.current().nextLong(-jitterNanos, jitterNanos + 1);
        Duration effective = ttl.plusNanos(randomNanos);
        return effective.compareTo(MINIMUM_TTL) < 0 ? MINIMUM_TTL : effective;
    }

    private <T> Duration getRemainingTtl(CacheRecord<T> record) {
        Instant expireAt = Instant.ofEpochSecond(record.expireIn());
        Duration remaining = Duration.between(Instant.now(), expireAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private <T> CompletableFuture<T> executeProviderAsync(
            Function<CancellationToken, CompletableFuture<T>> operation,
            T fallback,
            CancellationToken cancellationToken,
            boolean required) {
        try {
            return operation.apply(cancellationToken)
                    .orTimeout(PROVIDER_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        Throwable cause = (ex instanceof CompletionException) ? ex.getCause() : ex;
                        if (cause instanceof TimeoutException && !cancellationToken.isCancellationRequested()) {
                            if (required || providerRequired) {
                                throw createProviderUnavailableException();
                            }
                            LOGGER.log(Level.WARNING, "Cache provider timeout. CacheId=[{0}]", id);
                            return fallback;
                        }
                        if (cause instanceof RuntimeException) {
                            if (required || providerRequired) {
                                throw createProviderUnavailableException();
                            }
                            LOGGER.log(Level.WARNING, "Cache provider failed. CacheId=[{0}]", id);
                            return fallback;
                        }
                        throw (ex instanceof CompletionException) ? (CompletionException) ex : new CompletionException(ex);
                    });
        } catch (Exception ex) {
            if (required || providerRequired) {
                throw createProviderUnavailableException();
            }
            LOGGER.log(Level.WARNING, "Cache provider execution failed. CacheId=[{0}]", id);
            return CompletableFuture.completedFuture(fallback);
        }
    }

    private IllegalStateException createProviderUnavailableException() {
        return new IllegalStateException("Cache Provider is unavailable. CacheId=[" + id + "]");
    }
}
