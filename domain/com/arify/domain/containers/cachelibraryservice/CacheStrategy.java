package com.arify.domain.containers.cachelibraryservice;

/**
 * Estrategias de resolución de cache.
 * Define el comportamiento del builder en cuanto a lectura, ejecución de origen y almacenamiento.
 */
public enum CacheStrategy {
    /**
     * Lee solo desde cache. No ejecuta origen ni guarda.
     * Si el provider falla, retorna fallback (fail-open).
     */
    CACHE_ONLY,

    /**
     * Lee solo desde cache con provider requerido.
     * Si el provider falla, lanza excepción (fail-closed).
     */
    CACHE_ONLY_REQUIRED,

    /**
     * Lee desde cache y cierra el registro (created → closed).
     * Útil para consumo único de datos cacheados.
     */
    CACHE_ONLY_THEN_CLOSE,

    /**
     * Lee desde cache. Si no existe, ejecuta origen pero no guarda.
     * Estrategia de lectura con fallback sin poblamiento automático.
     */
    CACHE_THEN_SOURCE,

    /**
     * Lee desde cache. Si no existe, ejecuta origen y guarda como created.
     * Estrategia típica de cache backend reutilizable con anti-stampede.
     */
    CACHE_THEN_SOURCE_AND_STORE,

    /**
     * Fuerza ejecución del origen y guarda el resultado como created.
     * Útil para refresh o recalentamiento de cache ignorando el valor actual.
     */
    SOURCE_AND_STORE,

    /**
     * Guarda dato explícito directamente como created.
     * Compatible solo con PutAsync. No ejecuta loaders.
     */
    STORE_ONLY,

    /**
     * Crea reserva (started) si no hay dato, o guarda como created si hay dato.
     * Útil para idempotencia y control de concurrencia.
     * Compatible solo con PutAsync.
     */
    STORE_ONLY_OR_RESERVE
}
