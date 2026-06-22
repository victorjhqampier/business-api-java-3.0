package com.arify.config;

import com.arify.domain.containers.cachelibraryservice.ICacheInfrastructure;
import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.domain.interfaces.IFakeApiInfrastructure;
import com.arify.fakeapiinfra.queries.FakeApiCommand;
import com.arify.httpclientbuilder.HttpClientConnector;
import com.arify.redisinfra.general.RedisCacheInfrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import redis.clients.jedis.JedisPooled;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

/* ********************************************************************************************************
 * Copyright © 2026 Arify Labs - All rights reserved.
 *
 * Info                  : Composition Root - Infrastructure adapters wiring (FakeApiCommand, RedisCacheInfrastructure).
 *
 * By                    : Victor Jhampier Caxi Maquera
 * Email/Mobile/Phone    : victorjhampier@gmail.com | 968991*14
 *
 * Creation date         : 22/06/2026 3:05h
 **********************************************************************************************************/

@ApplicationScoped
public class InfrastructureStartUp {
    private static final Logger LOGGER = Logger.getLogger(InfrastructureStartUp.class.getName());

    @Produces
    @ApplicationScoped
    public IFakeApiInfrastructure fakeApiInfrastructure(MicroserviceCallMemoryQueue memoryQueue, HttpClientConnector httpClientConnector) {
        LOGGER.info("services.AddSingleton<IFakeApiInfrastructure, FakeApiCommand>()");
        return new FakeApiCommand(memoryQueue, httpClientConnector);
    }

    @Produces
    @ApplicationScoped
    public ICacheInfrastructure cacheInfrastructure(JedisPooled jedisPooled, @Named("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        LOGGER.info("services.AddSingleton<ICacheInfrastructure, RedisCacheInfrastructure>()");
        return new RedisCacheInfrastructure(jedisPooled, virtualThreadExecutor);
    }
}
