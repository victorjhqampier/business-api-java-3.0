package com.arify.domain.containers.cachelibraryservice;

import java.util.Objects;

/**
 * Punto de entrada del servicio de cache.
 * 
 * <p>Recibe un provider (ICacheInfrastructure) en su constructor y permite crear
 * builders fluidos mediante forKey(). La instancia de este servicio es ligera y
 * puede crearse por caso de uso. El recurso compartido y costoso vive en el provider
 * (conexión Redis, pool, cliente DynamoDB, etc.).</p>
 * 
 * <p>No debe registrarse como singleton global en Application. El provider sí debe
 * ser singleton (@ApplicationScoped).</p>
 * 
 * <p>Ejemplo de uso:</p>
 * <pre>{@code
 * var cache = new CacheLibraryService(provider);
 * var result = cache.forKey("cfg-11-currencies")
 *     .useStrategy(CacheStrategy.CACHE_THEN_SOURCE_AND_STORE)
 *     .withTtl(Duration.ofMinutes(10))
 *     .resolveAsync(token -> fetchFromSource(token), MyType.class, cancellationToken)
 *     .join();
 * }</pre>
 */
public final class CacheLibraryService {
    private final ICacheInfrastructure provider;

    /**
     * Crea una nueva instancia del servicio de cache.
     * 
     * @param provider Provider de cache (debe ser singleton/shared).
     * @throws NullPointerException si provider es null.
     */
    public CacheLibraryService(ICacheInfrastructure provider) {
        this.provider = Objects.requireNonNull(provider, "provider cannot be null");
    }

    /**
     * Inicia un builder fluido para la clave especificada.
     * 
     * @param key Clave única del registro (determinística y exacta).
     * @return Builder fluido para configurar estrategia, TTL y resolver.
     * @throws IllegalArgumentException si key es null, vacía o solo espacios.
     */
    public CacheQueryBuilder forKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Cache key cannot be empty");
        }
        return new CacheQueryBuilder(key, provider);
    }
}
