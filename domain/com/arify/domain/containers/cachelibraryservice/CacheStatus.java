package com.arify.domain.containers.cachelibraryservice;

/**
 * Representa el estado del ciclo de vida de un registro en cache.
 * 
 * <ul>
 *   <li><b>STARTED</b>: Registro reservado o proceso en curso. No puede leerse como dato válido.</li>
 *   <li><b>CREATED</b>: Registro completo, válido y listo para lectura funcional.</li>
 *   <li><b>CLOSED</b>: Registro cerrado, consumido o invalidado. No puede actualizarse.</li>
 * </ul>
 */
public enum CacheStatus {
    /**
     * Registro reservado o proceso en curso.
     * Puede actualizarse para completar una reserva (started → created).
     */
    STARTED,

    /**
     * Registro completo y válido.
     * Puede leerse como cache hit funcional.
     * Solo puede cerrarse (created → closed).
     */
    CREATED,

    /**
     * Registro cerrado o consumido.
     * No puede actualizarse ni leerse como dato válido.
     */
    CLOSED
}
