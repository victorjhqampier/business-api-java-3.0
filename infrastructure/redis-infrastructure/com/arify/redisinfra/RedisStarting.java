package com.arify.redisinfra;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import java.util.logging.Logger;

/* ********************************************************************************************************
 * Copyright © 2026 Arify Labs - All rights reserved.
 *
 * Info                  : Redis connection factory (JedisPooled initialization from ENV variables).
 *
 * By                    : Victor Jhampier Caxi Maquera
 * Email/Mobile/Phone    : victorjhampier@gmail.com | 968991*14
 *
 * Creation date         : 22/06/2026 3:05h
 **********************************************************************************************************/

public final class RedisStarting {
    private static final Logger LOGGER = Logger.getLogger(RedisStarting.class.getName());

    private static final int DEFAULT_REDIS_PORT = 6379;
    private static final int CONNECTION_TIMEOUT_MS = 500;
    private static final int SOCKET_TIMEOUT_MS = 500;

    private RedisStarting() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static JedisPooled init(String host, String password, int database, boolean ssl) {

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
            String errorMsg = String.format("Failed to connect to Redis. Host=%s, Database=%d, SSL=%b", host, database, ssl);
            LOGGER.severe(errorMsg + " - Application startup aborted.");
            throw new RuntimeException(errorMsg, ex);
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
