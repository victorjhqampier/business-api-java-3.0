package com.arify.domain.containers.cachelibraryservice;

import com.arify.domain.commons.CancellationToken;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Contrato del provider de cache (storage tonto).
 * 
 * <p>El provider debe ser stateless y solo ejecutar operaciones básicas por id y status.
 * No debe contener lógica de negocio, estrategias ni construcción de claves.
 * La inteligencia vive en CacheLibraryService y su builder.</p>
 * 
 * <p>Responsabilidades del provider:</p>
 * <ul>
 *   <li>Obtener un registro por id.</li>
 *   <li>Crear un registro solo si no existe (atomicidad).</li>
 *   <li>Actualizar un registro solo si el status actual coincide con expectedStatus.</li>
 *   <li>Validar ownerToken en transiciones started → created.</li>
 *   <li>Eliminar por id.</li>
 *   <li>Verificar existencia por id.</li>
 *   <li>Aplicar TTL real.</li>
 *   <li>Propagar CancellationToken.</li>
 *   <li>Fallar rápido si el storage está caído.</li>
 *   <li>Loguear Warning/Error según corresponda (no loguear cachedData ni secretos).</li>
 * </ul>
 */
public interface ICacheInfrastructure {
    /**
     * Obtiene un registro por id.
     * 
     * @param <T> Tipo del dato cacheado.
     * @param id Clave única del registro.
     * @param cancellationToken Token de cancelación.
     * @return CompletableFuture con el registro o null si no existe/expiró.
     */
    <T> CompletableFuture<CacheRecord<T>> getAsync(String id, CancellationToken cancellationToken);

    /**
     * Crea un registro solo si no existe (operación atómica).
     * 
     * @param <T> Tipo del dato cacheado.
     * @param record Registro a crear.
     * @param ttl Tiempo de vida del registro.
     * @param cancellationToken Token de cancelación.
     * @return CompletableFuture con true si creó, false si ya existía.
     */
    <T> CompletableFuture<Boolean> tryCreateAsync(CacheRecord<T> record, Duration ttl, CancellationToken cancellationToken);

    /**
     * Actualiza un registro de forma condicional por id, expectedStatus y expectedOwner.
     * 
     * <p>Transiciones permitidas:</p>
     * <ul>
     *   <li>started → created (completar reserva con validación de ownerToken)</li>
     *   <li>created → closed (cierre controlado)</li>
     * </ul>
     * 
     * @param <T> Tipo del dato cacheado.
     * @param record Nuevo registro con datos actualizados.
     * @param expectedStatus Estado actual esperado.
     * @param expectedOwner Token del propietario esperado (validado en started → created).
     * @param ttl Tiempo de vida restante/actualizado.
     * @param cancellationToken Token de cancelación.
     * @return CompletableFuture con true si actualizó, false si la condición no se cumplió.
     */
    <T> CompletableFuture<Boolean> tryUpdateAsync(CacheRecord<T> record, CacheStatus expectedStatus, String expectedOwner, Duration ttl, CancellationToken cancellationToken);

    /**
     * Elimina un registro por id.
     * 
     * @param id Clave única del registro.
     * @param cancellationToken Token de cancelación.
     * @return CompletableFuture con true si eliminó, false si no existía.
     */
    CompletableFuture<Boolean> removeAsync(String id, CancellationToken cancellationToken);

    /**
     * Verifica si un registro existe por id.
     * 
     * @param id Clave única del registro.
     * @param cancellationToken Token de cancelación.
     * @return CompletableFuture con true si existe, false si no.
     */
    CompletableFuture<Boolean> existsAsync(String id, CancellationToken cancellationToken);
}
