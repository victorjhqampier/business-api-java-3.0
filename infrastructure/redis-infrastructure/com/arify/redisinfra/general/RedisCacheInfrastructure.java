package com.arify.redisinfra.general;

import com.arify.domain.commons.CancellationToken;
import com.arify.domain.containers.cachelibraryservice.CacheRecord;
import com.arify.domain.containers.cachelibraryservice.CacheStatus;
import com.arify.domain.containers.cachelibraryservice.ICacheInfrastructure;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación de cache usando Redis (Jedis 5.x).
 * 
 * <p>Características:</p>
 * <ul>
 *   <li>Operaciones atómicas con Lua scripts para UPDATE condicional.</li>
 *   <li>Circuit breaker de 1 segundo para evitar colapsar la aplicación si Redis falla.</li>
 *   <li>Serialización JSON con Jackson (ObjectMapper estático).</li>
 *   <li>Validación de ownerToken en transiciones started → created.</li>
 *   <li>Timeout de 500ms configurado en el pool de Jedis.</li>
 * </ul>
 */
public final class RedisCacheInfrastructure implements ICacheInfrastructure {
    private static final Logger LOGGER = Logger.getLogger(RedisCacheInfrastructure.class.getName());
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private static final String UPDATE_WHEN_STATUS_MATCHES_SCRIPT = """
            local value = redis.call('GET', KEYS[1])
            if not value then
                return 0
            end
            
            local record = cjson.decode(value)
            if record.status ~= ARGV[1] then
                return 0
            end
            
            if ARGV[1] == 'started' then
                local ownerToken = record.ownerToken
                if ownerToken == nil then
                    ownerToken = record.ownerFingerprint
                end
                if ownerToken ~= nil and ownerToken ~= cjson.null and ownerToken ~= '' and ownerToken ~= ARGV[4] then
                    return 0
                end
            end
            
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            return 1
            """;

    private static final int UNAVAILABLE_COOLDOWN_MILLISECONDS = 1000;
    private static final AtomicLong unavailableUntilUnixMilliseconds = new AtomicLong(0);

    private final JedisPooled jedis;
    private final ExecutorService executor;

    // Constructor original (mantiene compatibilidad)
    public RedisCacheInfrastructure(JedisPooled jedis) {
        if (jedis == null) {
            throw new IllegalArgumentException("jedis cannot be null");
        }
        this.jedis = jedis;
        this.executor = ForkJoinPool.commonPool(); // Fallback al pool por defecto
        LOGGER.warning("RedisCacheInfrastructure initialized without explicit executor. Using ForkJoinPool.commonPool(). For high performance, inject Virtual Threads.");
    }

    // Constructor con Virtual Threads (Alto Rendimiento - Recomendado)
    public RedisCacheInfrastructure(JedisPooled jedis, ExecutorService executor) {
        if (jedis == null) {
            throw new IllegalArgumentException("jedis cannot be null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor cannot be null");
        }
        this.jedis = jedis;
        this.executor = executor;
    }

    @Override
    public <T> CompletableFuture<CacheRecord<T>> getAsync(String id, CancellationToken cancellationToken) {
        return CompletableFuture.supplyAsync(() -> {
            validateId(id);
            if (cancellationToken.isCancellationRequested()) {
                throw new RuntimeException("Operation cancelled");
            }
            throwIfRedisTemporarilyUnavailable(id, "get");
            logDisconnected(id, "get");

            try {
                String value = jedis.get(id);
                if (value == null) {
                    return null;
                }

                RedisCacheRecord record = deserializeRecord(value, id);
                if (record == null) {
                    return null;
                }

                if (record.isExpired(Instant.now())) {
                    LOGGER.log(Level.WARNING, "Redis cache record expired. CacheId=[{0}] Status=[{1}]", 
                            new Object[]{id, record.status});
                    jedis.del(id);
                    return null;
                }

                return toTypedRecord(record);
            } catch (Exception ex) {
                if (ex instanceof RuntimeException && ex.getMessage() != null && ex.getMessage().contains("cancelled")) {
                    throw (RuntimeException) ex;
                }
                markRedisTemporarilyUnavailable();
                LOGGER.log(Level.SEVERE, "Redis cache get failed. CacheId=[" + id + "]", ex);
                throw new RuntimeException("Redis cache get failed", ex);
            }
        }, executor);
    }

    @Override
    public <T> CompletableFuture<Boolean> tryCreateAsync(CacheRecord<T> record, Duration ttl, CancellationToken cancellationToken) {
        return CompletableFuture.supplyAsync(() -> {
            validateRecord(record, ttl);
            if (cancellationToken.isCancellationRequested()) {
                throw new RuntimeException("Operation cancelled");
            }
            throwIfRedisTemporarilyUnavailable(record.id(), "create");
            logDisconnected(record.id(), "create");

            try {
                String payload = OBJECT_MAPPER.writeValueAsString(toRedisRecord(record));
                long ttlMillis = ttl.toMillis();
                
                SetParams params = new SetParams().nx().px(ttlMillis);
                String result = jedis.set(record.id(), payload, params);
                
                return "OK".equals(result);
            } catch (JsonProcessingException ex) {
                LOGGER.log(Level.SEVERE, "Redis cache serialization failed. CacheId=[" + record.id() + "]", ex);
                throw new RuntimeException("Redis cache serialization failed", ex);
            } catch (Exception ex) {
                if (ex instanceof RuntimeException && ex.getMessage() != null && ex.getMessage().contains("cancelled")) {
                    throw (RuntimeException) ex;
                }
                markRedisTemporarilyUnavailable();
                LOGGER.log(Level.SEVERE, "Redis cache create failed. CacheId=[" + record.id() + "] Status=[" + record.status() + "]", ex);
                throw new RuntimeException("Redis cache create failed", ex);
            }
        }, executor);
    }

    @Override
    public <T> CompletableFuture<Boolean> tryUpdateAsync(
            CacheRecord<T> record,
            CacheStatus expectedStatus,
            String expectedOwner,
            Duration ttl,
            CancellationToken cancellationToken) {
        return CompletableFuture.supplyAsync(() -> {
            validateRecord(record, ttl);
            if (cancellationToken.isCancellationRequested()) {
                throw new RuntimeException("Operation cancelled");
            }
            throwIfRedisTemporarilyUnavailable(record.id(), "update");
            logDisconnected(record.id(), "update");

            try {
                if (!isAllowedTransition(expectedStatus, record.status())) {
                    LOGGER.log(Level.WARNING, 
                            "Redis cache update rejected because transition is not allowed. CacheId=[{0}] ExpectedStatus=[{1}] NewStatus=[{2}]",
                            new Object[]{record.id(), expectedStatus, record.status()});
                    return false;
                }

                String payload = OBJECT_MAPPER.writeValueAsString(toRedisRecord(record));
                long ttlMillis = Math.max(1, ttl.toMillis());
                
                String expectedStatusStr = toRedisStatus(expectedStatus);
                String expectedOwnerStr = (expectedOwner == null || expectedOwner.isEmpty()) ? "" : expectedOwner;
                
                Object result = jedis.eval(
                        UPDATE_WHEN_STATUS_MATCHES_SCRIPT,
                        List.of(record.id()),
                        List.of(expectedStatusStr, payload, String.valueOf(ttlMillis), expectedOwnerStr));

                boolean updated = result != null && ((Number) result).intValue() == 1;
                if (!updated) {
                    LOGGER.log(Level.WARNING,
                            "Redis cache conditional transition skipped because current status did not match. CacheId=[{0}] ExpectedStatus=[{1}] NewStatus=[{2}]",
                            new Object[]{record.id(), expectedStatus, record.status()});
                }

                return updated;
            } catch (JsonProcessingException ex) {
                LOGGER.log(Level.SEVERE, "Redis cache serialization failed. CacheId=[" + record.id() + "]", ex);
                throw new RuntimeException("Redis cache serialization failed", ex);
            } catch (Exception ex) {
                if (ex instanceof RuntimeException && ex.getMessage() != null && ex.getMessage().contains("cancelled")) {
                    throw (RuntimeException) ex;
                }
                markRedisTemporarilyUnavailable();
                LOGGER.log(Level.SEVERE,
                        "Redis cache update failed. CacheId=[" + record.id() + "] ExpectedStatus=[" + expectedStatus + "] NewStatus=[" + record.status() + "]",
                        ex);
                throw new RuntimeException("Redis cache update failed", ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> removeAsync(String id, CancellationToken cancellationToken) {
        return CompletableFuture.supplyAsync(() -> {
            validateId(id);
            if (cancellationToken.isCancellationRequested()) {
                throw new RuntimeException("Operation cancelled");
            }
            throwIfRedisTemporarilyUnavailable(id, "remove");
            logDisconnected(id, "remove");

            try {
                long removed = jedis.del(id);
                if (removed == 0) {
                    LOGGER.log(Level.WARNING, "Redis cache remove skipped because record was not found. CacheId=[{0}]", id);
                }
                return removed > 0;
            } catch (Exception ex) {
                if (ex instanceof RuntimeException && ex.getMessage() != null && ex.getMessage().contains("cancelled")) {
                    throw (RuntimeException) ex;
                }
                markRedisTemporarilyUnavailable();
                LOGGER.log(Level.SEVERE, "Redis cache remove failed. CacheId=[" + id + "]", ex);
                throw new RuntimeException("Redis cache remove failed", ex);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> existsAsync(String id, CancellationToken cancellationToken) {
        return CompletableFuture.supplyAsync(() -> {
            validateId(id);
            if (cancellationToken.isCancellationRequested()) {
                throw new RuntimeException("Operation cancelled");
            }
            throwIfRedisTemporarilyUnavailable(id, "exists");
            logDisconnected(id, "exists");

            try {
                return jedis.exists(id);
            } catch (Exception ex) {
                if (ex instanceof RuntimeException && ex.getMessage() != null && ex.getMessage().contains("cancelled")) {
                    throw (RuntimeException) ex;
                }
                markRedisTemporarilyUnavailable();
                LOGGER.log(Level.SEVERE, "Redis cache exists check failed. CacheId=[" + id + "]", ex);
                throw new RuntimeException("Redis cache exists check failed", ex);
            }
        }, executor);
    }

    // --- Helpers privados ---

    private RedisCacheRecord deserializeRecord(String value, String id) {
        try {
            return OBJECT_MAPPER.readValue(value, RedisCacheRecord.class);
        } catch (JsonProcessingException ex) {
            LOGGER.log(Level.WARNING, "Redis cache record has invalid JSON. CacheId=[" + id + "]", ex);
            return null;
        }
    }

    private void logDisconnected(String id, String operation) {
        try {
            jedis.ping();
        } catch (JedisConnectionException ex) {
            LOGGER.log(Level.SEVERE, "Redis connection is not available. Operation=[{0}] CacheId=[{1}]",
                    new Object[]{operation, id});
        }
    }

    private String toRedisStatus(CacheStatus status) {
        return switch (status) {
            case STARTED -> "started";
            case CREATED -> "created";
            case CLOSED -> "closed";
        };
    }

    private CacheStatus fromRedisStatus(String status) {
        return switch (status) {
            case "started" -> CacheStatus.STARTED;
            case "created" -> CacheStatus.CREATED;
            case "closed" -> CacheStatus.CLOSED;
            default -> throw new IllegalArgumentException("Unknown status: " + status);
        };
    }

    private boolean isAllowedTransition(CacheStatus expectedStatus, CacheStatus newStatus) {
        return (expectedStatus == CacheStatus.STARTED && newStatus == CacheStatus.CREATED)
                || (expectedStatus == CacheStatus.CREATED && newStatus == CacheStatus.CLOSED);
    }

    @SuppressWarnings("unchecked")
    private <T> CacheRecord<T> toTypedRecord(RedisCacheRecord record) {
        T cachedData = null;
        if (record.cachedData != null && !record.cachedData.isNull()) {
            try {
                cachedData = (T) OBJECT_MAPPER.treeToValue(record.cachedData, Object.class);
            } catch (JsonProcessingException ex) {
                LOGGER.log(Level.WARNING,
                        "Redis cache record payload could not be deserialized. CacheId=[{0}] Status=[{1}]",
                        new Object[]{record.id, record.status});
                return null;
            }
        }

        return new CacheRecord<>(
                record.id,
                fromRedisStatus(record.status),
                cachedData,
                record.createdAt,
                record.expireIn,
                record.ownerToken);
    }

    private <T> RedisCacheRecord toRedisRecord(CacheRecord<T> record) {
        JsonNode cachedDataNode = null;
        if (record.cachedData() != null) {
            cachedDataNode = OBJECT_MAPPER.valueToTree(record.cachedData());
        }

        return new RedisCacheRecord(
                record.id(),
                toRedisStatus(record.status()),
                cachedDataNode,
                record.createdAt(),
                record.expireIn(),
                record.ownerToken());
    }

    private void throwIfRedisTemporarilyUnavailable(String id, String operation) {
        long unavailableUntil = unavailableUntilUnixMilliseconds.get();
        if (Instant.now().toEpochMilli() < unavailableUntil) {
            LOGGER.log(Level.WARNING,
                    "Redis cache {0} skipped because Redis is temporarily unavailable. CacheId=[{1}]",
                    new Object[]{operation, id});
            throw new JedisConnectionException("Redis is temporarily unavailable");
        }
    }

    private static void markRedisTemporarilyUnavailable() {
        long unavailableUntil = Instant.now().toEpochMilli() + UNAVAILABLE_COOLDOWN_MILLISECONDS;
        unavailableUntilUnixMilliseconds.set(unavailableUntil);
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
     * Registro interno para serialización Redis (con JsonNode para cachedData).
     */
    private static final class RedisCacheRecord {
        public final String id;
        public final String status;
        public final JsonNode cachedData;
        public final long createdAt;
        public final long expireIn;
        public final String ownerToken;

        @JsonCreator
        public RedisCacheRecord(
                @JsonProperty("id") String id,
                @JsonProperty("status") String status,
                @JsonProperty("cachedData") JsonNode cachedData,
                @JsonProperty("createdAt") long createdAt,
                @JsonProperty("expireIn") long expireIn,
                @JsonProperty("ownerToken") String ownerToken) {
            this.id = id;
            this.status = status;
            this.cachedData = cachedData;
            this.createdAt = createdAt;
            this.expireIn = expireIn;
            this.ownerToken = ownerToken;
        }

        public boolean isExpired(Instant now) {
            return expireIn <= now.getEpochSecond();
        }
    }
}
