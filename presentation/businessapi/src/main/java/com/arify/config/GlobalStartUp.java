package com.arify.config;

import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.httpclientbuilder.HttpClientConnector;
import com.arify.httpclientbuilder.HttpClientStarting;
import com.arify.redisinfra.RedisStarting;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import redis.clients.jedis.JedisPooled;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/* ********************************************************************************************************
 * Copyright © 2026 Arify Labs - All rights reserved.
 *
 * Info                  : Composition Root - Global technical resources (virtual thread executor, HTTP connector, Redis, memory queue).
 *
 * By                    : Victor Jhampier Caxi Maquera
 * Email/Mobile/Phone    : victorjhampier@gmail.com | 968991*14
 *
 * Creation date         : 22/06/2026 3:05h
 **********************************************************************************************************/

@ApplicationScoped
public class GlobalStartUp {
    private static final Logger LOGGER = Logger.getLogger(GlobalStartUp.class.getName());

    @Produces
    @ApplicationScoped
    public MicroserviceCallMemoryQueue memoryQueue() {
        LOGGER.info("services.AddSingleton<MicroserviceCallMemoryQueue>()");
        return new MicroserviceCallMemoryQueue(1500);
    }

    @Produces
    @ApplicationScoped
    public HttpClientConnector httpClientConnector(@Named("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        LOGGER.info("services.AddSingleton<HttpClientConnector>()");
        return HttpClientStarting.init(virtualThreadExecutor);
    }

    // Virtual Threads (Java 21) - Equivalente al .NET ThreadPool para I/O-bound
    @Produces
    @ApplicationScoped
    @Named("virtualThreadExecutor")
    public ExecutorService virtualThreadExecutor() {
        LOGGER.info("services.AddSingleton<ExecutorService>(virtualThreadExecutor)");
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    // Redis Connection Pool - Equivalente a IConnectionMultiplexer en .NET
    @Produces
    @ApplicationScoped
    public JedisPooled jedisPooled() {
        LOGGER.info("services.AddSingleton<JedisPooled>()");
        return RedisStarting.init();
    }

    public void shutdownVirtualThreadExecutor(@Disposes @Named("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        LOGGER.info("Closing Virtual Thread Executor");
        virtualThreadExecutor.shutdown();
    }

    public void shutdownJedisPooled(@Disposes JedisPooled jedisPooled) {
        LOGGER.info("Closing Redis connection pool");
        jedisPooled.close();
    }
}
