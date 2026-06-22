package com.arify.domain.containers.cachelibraryservice;

import java.time.Instant;

/**
 * Registro de cache genérico con estado, datos y metadatos de tiempo.
 * 
 * @param <T> Tipo del dato cacheado (cachedData).
 * @param id Clave única del registro (obligatorio).
 * @param status Estado del ciclo de vida (obligatorio).
 * @param cachedData Contenido funcional cacheado (puede ser null).
 * @param createdAt Timestamp de creación en epoch seconds UTC.
 * @param expireIn Timestamp de expiración en epoch seconds UTC.
 * @param ownerToken Token del propietario para validar reservas (opcional).
 */
public record CacheRecord<T>(
    String id,
    CacheStatus status,
    T cachedData,
    long createdAt,
    long expireIn,
    String ownerToken
) {
    /**
     * Verifica si el registro ha expirado respecto al instante dado.
     * 
     * @param now Instante actual.
     * @return true si expireIn <= now (expresado en epoch seconds).
     */
    public boolean isExpired(Instant now) {
        return expireIn <= now.getEpochSecond();
    }
}
