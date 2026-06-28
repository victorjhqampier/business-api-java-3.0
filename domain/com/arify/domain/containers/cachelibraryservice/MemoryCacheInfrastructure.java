package com.arify.domain.containers.cachelibraryservice;

import com.arify.domain.commons.CancellationToken;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;

/**
 * Implementación de cache en memoria usando ConcurrentHashMap.
 * Provider built-in para desarrollo, pruebas y casos de uso que no requieren persistencia externa.
 * 
 * <p>Características:</p>
 * <ul>
 *   <li>Gestión automática de capacidad con eviction LRU.</li>
 *   <li>Limpieza de registros expirados.</li>
 *   <li>Operaciones atómicas (CAS) para actualizaciones condicionales.</li>
 *   <li>Validación de ownerToken en transiciones started → created.</li>
 * </ul>
 */
public final class MemoryCacheInfrastructure implements ICacheInfrastructure {
    private final ConcurrentHashMap<String, CacheEntry> records;
    private final int maxRecords;

    /**
     * Crea una nueva instancia del provider en memoria.
     * 
     * @param maxRecords Capacidad máxima de registros (debe ser positivo).
     * @throws IllegalArgumentException si maxRecords <= 0.
     */
    public MemoryCacheInfrastructure(int maxRecords) {
        if (maxRecords <= 0) {
            throw new IllegalArgumentException("Max records must be positive");
        }
        this.maxRecords = maxRecords;
        this.records = new ConcurrentHashMap<>();
    }

    @Override
    public <T> CompletableFuture<CacheRecord<T>> getAsync(String id, Class<T> type, CancellationToken cancellationToken) {
        if (cancellationToken.isCancellationRequested()) {
            return CompletableFuture.failedFuture(new CancellationException("Operation cancelled"));
        }
        validateId(id);

        CacheEntry entry = records.get(id);
        if (entry == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (entry.record.isExpired(Instant.now())) {
            records.remove(id);
            return CompletableFuture.completedFuture(null);
        }

        if (entry.value != null && type != Object.class && !type.isInstance(entry.value)) {
            return CompletableFuture.completedFuture(null);
        }

        @SuppressWarnings("unchecked")
        T typedValue = entry.value == null ? null : (T) entry.value;
        CacheRecord<T> typedRecord = new CacheRecord<>(
                entry.record.id(),
                entry.record.status(),
                typedValue,
                entry.record.createdAt(),
                entry.record.expireIn(),
                entry.record.ownerToken());

        return CompletableFuture.completedFuture(typedRecord);
    }

    @Override
    public <T> CompletableFuture<Boolean> tryCreateAsync(
            CacheRecord<T> record,
            Duration ttl,
            CancellationToken cancellationToken) {
        if (cancellationToken.isCancellationRequested()) {
            return CompletableFuture.failedFuture(new CancellationException("Operation cancelled"));
        }
        validateRecord(record, ttl);
        ensureCapacity();

        CacheEntry entry = new CacheEntry(toObjectRecord(record), record.cachedData());
        CacheEntry previous = records.putIfAbsent(record.id(), entry);
        return CompletableFuture.completedFuture(previous == null);
    }

    @Override
    public <T> CompletableFuture<Boolean> tryUpdateAsync(
            CacheRecord<T> record,
            CacheStatus expectedStatus,
            String expectedOwner,
            Duration ttl,
            CancellationToken cancellationToken) {
        if (cancellationToken.isCancellationRequested()) {
            return CompletableFuture.failedFuture(new CancellationException("Operation cancelled"));
        }
        validateRecord(record, ttl);

        // Compare-and-swap loop para actualización atómica
        while (true) {
            CacheEntry current = records.get(record.id());
            if (current == null) {
                return CompletableFuture.completedFuture(false);
            }

            if (current.record.isExpired(Instant.now())) {
                records.remove(record.id());
                return CompletableFuture.completedFuture(false);
            }

            if (current.record.status() != expectedStatus) {
                return CompletableFuture.completedFuture(false);
            }

            // Validar ownerToken en transición started → created
            if (expectedStatus == CacheStatus.STARTED && record.status() == CacheStatus.CREATED) {
                String currentOwner = current.record.ownerToken();
                if (currentOwner != null && !currentOwner.isEmpty() && !currentOwner.equals(expectedOwner)) {
                    return CompletableFuture.completedFuture(false);
                }
            }

            CacheEntry updated = new CacheEntry(toObjectRecord(record), record.cachedData());
            if (records.replace(record.id(), current, updated)) {
                return CompletableFuture.completedFuture(true);
            }
            // Si falla el CAS, reintenta (otro thread modificó el registro)
        }
    }

    @Override
    public CompletableFuture<Boolean> removeAsync(String id, CancellationToken cancellationToken) {
        if (cancellationToken.isCancellationRequested()) {
            return CompletableFuture.failedFuture(new CancellationException("Operation cancelled"));
        }
        validateId(id);
        CacheEntry removed = records.remove(id);
        return CompletableFuture.completedFuture(removed != null);
    }

    @Override
    public CompletableFuture<Boolean> existsAsync(String id, CancellationToken cancellationToken) {
        if (cancellationToken.isCancellationRequested()) {
            return CompletableFuture.failedFuture(new CancellationException("Operation cancelled"));
        }
        validateId(id);

        CacheEntry entry = records.get(id);
        if (entry == null) {
            return CompletableFuture.completedFuture(false);
        }

        if (entry.record.isExpired(Instant.now())) {
            records.remove(id);
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.completedFuture(true);
    }

    // --- Helpers privados ---

    private void ensureCapacity() {
        if (records.size() < maxRecords) {
            return;
        }

        // Limpiar registros expirados
        Instant now = Instant.now();
        records.entrySet().removeIf(entry -> entry.getValue().record.isExpired(now));

        // Si sigue lleno, evict el más antiguo (LRU por createdAt)
        while (records.size() >= maxRecords) {
            String oldestKey = null;
            long oldestCreatedAt = Long.MAX_VALUE;

            for (var entry : records.entrySet()) {
                long createdAt = entry.getValue().record.createdAt();
                if (createdAt < oldestCreatedAt) {
                    oldestCreatedAt = createdAt;
                    oldestKey = entry.getKey();
                }
            }

            if (oldestKey == null) {
                break;
            }
            records.remove(oldestKey);
        }
    }

    private CacheRecord<Object> toObjectRecord(CacheRecord<?> record) {
        return new CacheRecord<>(
                record.id(),
                record.status(),
                record.cachedData(),
                record.createdAt(),
                record.expireIn(),
                record.ownerToken());
    }

    private void validateRecord(CacheRecord<?> record, Duration ttl) {
        if (record == null) {
            throw new IllegalArgumentException("Record cannot be null");
        }
        validateId(record.id());
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        if (record.expireIn() <= record.createdAt()) {
            throw new IllegalArgumentException("Record expiration must be greater than creation time");
        }
    }

    private void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cache id cannot be empty");
        }
    }

    /**
     * Entrada interna que almacena el record como Object y el valor tipado.
     */
    private record CacheEntry(CacheRecord<Object> record, Object value) {
    }
}
