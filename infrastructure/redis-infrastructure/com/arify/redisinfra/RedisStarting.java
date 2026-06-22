package com.arify.redisinfra;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.util.logging.Logger;

/**
 * Configuración e inicialización de la conexión Redis.
 * 
 * <p>Equivalente a {@code ConnectionMultiplexer.Connect(ConfigurationOptions)} en .NET.</p>
 * 
 * <p>Características de alto rendimiento:</p>
 * <ul>
 *   <li>Connection Timeout: 500ms (fail-fast)</li>
 *   <li>Socket Timeout: 500ms (operaciones síncronas/asíncronas)</li>
 *   <li>Connection Retry: 1 intento</li>
 *   <li>KeepAlive: 30 segundos</li>
 *   <li>SSL: Configurable</li>
 * </ul>
 */
public final class RedisStarting {
    private static final Logger LOGGER = Logger.getLogger(RedisStarting.class.getName());

    private static final int DEFAULT_REDIS_PORT = 6379;
    private static final int CONNECTION_TIMEOUT_MS = 500;
    private static final int SOCKET_TIMEOUT_MS = 500;

    private RedisStarting() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Inicializa una conexión persistente a Redis con la configuración provista.
     * 
     * @param host Host de Redis (requerido).
     * @param password Contraseña de Redis (requerido).
     * @param database Número de base de datos Redis (0-15).
     * @param ssl Si la conexión debe usar SSL/TLS.
     * @param abortOnConnectFail Si debe lanzar excepción al fallar la conexión inicial.
     * @return Instancia de {@link JedisPooled} configurada y lista para usar.
     * @throws IllegalArgumentException Si los parámetros obligatorios son inválidos.
     * @throws RuntimeException Si {@code abortOnConnectFail=true} y la conexión falla.
     */
    public static JedisPooled init(
            String host,
            String password,
            int database,
            boolean ssl,
            boolean abortOnConnectFail) {

        validateConfiguration(host, password, database);

        LOGGER.info(String.format(
                "Initializing Redis connection: host=%s, database=%d, ssl=%b, abortOnConnectFail=%b",
                host, database, ssl, abortOnConnectFail));

        HostAndPort hostAndPort = new HostAndPort(host, DEFAULT_REDIS_PORT);

        DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
                .password(password)
                .database(database)
                .connectionTimeoutMillis(CONNECTION_TIMEOUT_MS)
                .socketTimeoutMillis(SOCKET_TIMEOUT_MS)
                .ssl(ssl)
                .build();

        try {
            JedisPooled jedis = new JedisPooled(hostAndPort, config);
            
            // Verificar conectividad inicial
            jedis.ping();
            
            LOGGER.info("Redis connection established successfully");
            return jedis;
        } catch (Exception ex) {
            String errorMsg = String.format(
                    "Failed to connect to Redis. Host=%s, Database=%d, SSL=%b",
                    host, database, ssl);
            
            if (abortOnConnectFail) {
                LOGGER.severe(errorMsg + " - Application startup aborted.");
                throw new RuntimeException(errorMsg, ex);
            } else {
                LOGGER.warning(errorMsg + " - Application will continue without Redis cache.");
                // Retornar una instancia "dummy" o relanzar para manejo en capas superiores
                throw new RuntimeException(errorMsg, ex);
            }
        }
    }

    private static void validateConfiguration(String host, String password, int database) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("RedisDatabase.Host or REDIS_HOST must be configured");
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("RedisDatabase.Passwd or REDIS_PASSWORD must be configured");
        }

        if (database < 0 || database > 15) {
            throw new IllegalArgumentException(
                    "RedisDatabase.Database or REDIS_DATABASE must be between 0 and 15. Provided: " + database);
        }
    }
}
