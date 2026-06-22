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

/**
 * Configuración de recursos técnicos globales del sistema.
 * 
 * <p><strong>Símil .NET:</strong> Equivale a configuraciones base del Runtime:<br>
 * {@code builder.Services.AddSingleton<HttpClient>();}<br>
 * {@code builder.Services.AddSingleton(ThreadPool);}<br>
 * {@code builder.Services.AddSingleton<MemoryQueue>();}</p>
 * 
 * <p>Todos los componentes son Singletons ({@code @ApplicationScoped}) 
 * para máximo rendimiento.</p>
 */
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
