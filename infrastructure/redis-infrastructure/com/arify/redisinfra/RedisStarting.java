package com.arify.redisinfra;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import java.util.logging.Logger;

public final class RedisStarting {
    private static final Logger LOGGER = Logger.getLogger(RedisStarting.class.getName());

    private static final int DEFAULT_REDIS_PORT = 6379;
    private static final int CONNECTION_TIMEOUT_MS = 500;
    private static final int SOCKET_TIMEOUT_MS = 500;

    private RedisStarting() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static JedisPooled init() {
        String host = System.getenv("REDISDATABASE_HOST");
        String password = System.getenv("REDISDATABASE_PASSWD");
        int database = Integer.parseInt(System.getenv("REDISDATABASE_DATABASE"));
        boolean ssl = Boolean.parseBoolean(
                System.getenv().getOrDefault("REDISDATABASE_SSL", "false")
        );
        boolean abortOnConnectFail = Boolean.parseBoolean(
                System.getenv().getOrDefault("REDISDATABASE_ABORTONCONNECTFAIL", "false")
        );

        validateConfiguration(host, password, database);
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
