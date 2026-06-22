package com.arify.domain.containers.cachelibraryservice;

import java.util.Objects;

/* ********************************************************************************************************
 * Copyright © 2026 Arify Labs - All rights reserved.
 *
 * Info                  : Cache Library Service - Entry point for cache queries (fluent builder via forKey).
 *
 * By                    : Victor Jhampier Caxi Maquera
 * Email/Mobile/Phone    : victorjhampier@gmail.com | 968991*14
 *
 * Creation date         : 22/06/2026 3:05h
 **********************************************************************************************************/

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
