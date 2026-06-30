package com.arify.startup.infrastructure;

import com.arify.domain.containers.cachelibraryservice.CacheLibraryService;
import com.arify.domain.containers.cachelibraryservice.ICacheInfrastructure;
import com.arify.redisinfra.RedisStarting;
import com.arify.redisinfra.general.RedisCacheInfrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import redis.clients.jedis.JedisPooled;
import java.util.concurrent.ExecutorService;

@ApplicationScoped
public class RedisSetting {
    @Inject
    @ConfigProperty(name = "REDISDATABASE_HOST")
    String redisHost;

    @Inject
    @ConfigProperty(name = "REDISDATABASE_PASSWD")
    String redisPassword;

    @Inject
    @ConfigProperty(name = "REDISDATABASE_DATABASE")
    int redisDatabase;

    @Inject
    @ConfigProperty(name = "REDISDATABASE_SSL", defaultValue = "false")
    boolean redisSsl;

    // Redis Connection Pool - Equivalente a IConnectionMultiplexer en .NET
    @Produces
    @ApplicationScoped
    public JedisPooled jedisPooled() {
        /*LOGGER.info("services.AddSingleton<JedisPooled>()");*/
        return RedisStarting.init(normalizeConfigValue(redisHost), normalizeConfigValue(redisPassword), redisDatabase, redisSsl);
    }

    private static String normalizeConfigValue(String value) {
        String normalized = value.trim();
        if (normalized.length() >= 2) {
            char first = normalized.charAt(0);
            char last = normalized.charAt(normalized.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return normalized.substring(1, normalized.length() - 1);
            }
        }
        return normalized;
    }

    // ---- Dependency Injection Section ------
    @Produces
    @ApplicationScoped
    public CacheLibraryService cacheLibraryService(ICacheInfrastructure cacheInfrastructure) {
        /*LOGGER.info("services.AddSingleton<CacheLibraryService>()");*/
        return new CacheLibraryService(cacheInfrastructure);
    }

    @Produces
    @ApplicationScoped
    public ICacheInfrastructure cacheInfrastructure(JedisPooled jedisPooled, @Named("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        /*LOGGER.info("services.AddSingleton<ICacheInfrastructure, RedisCacheInfrastructure>()");*/
        return new RedisCacheInfrastructure(jedisPooled, virtualThreadExecutor);
    }

    public void shutdownJedisPooled(@Disposes JedisPooled jedisPooled) {
        jedisPooled.close();
    }
}
