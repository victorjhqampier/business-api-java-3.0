package com.arify.startup.application;

import com.arify.application.ports.ExampleCachePort;
import com.arify.application.ports.ExampleIdempotencyPort;
import com.arify.application.ports.ExamplePort;
import com.arify.application.usecases.exampleusecase.ExampleIdempotencyUsecase;
import com.arify.application.usecases.exampleusecase.ExampleRedisUsecase;
import com.arify.application.usecases.exampleusecase.ExampleUseCase;
import com.arify.domain.containers.cachelibraryservice.CacheLibraryService;
import com.arify.domain.containers.cachelibraryservice.ICacheInfrastructure;
import com.arify.domain.interfaces.IFakeApiInfrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ApplicationSetting {

    @Produces
    @ApplicationScoped
    public ExamplePort exampleUseCase(
            IFakeApiInfrastructure fakeApiInfrastructure,
            CacheLibraryService cacheLibraryService
    ) {
        /*LOGGER.info("services.AddSingleton<ExamplePort, ExampleUseCase>()");*/
        return new ExampleUseCase(fakeApiInfrastructure, cacheLibraryService);
    }

    @Produces
    @ApplicationScoped
    public ExampleCachePort exampleCacheUseCase(
            IFakeApiInfrastructure fakeApiInfrastructure,
            ICacheInfrastructure redisProvider
    ) {
        /*LOGGER.info("services.AddSingleton<ExampleCachePort, ExampleRedisUsecase>()");*/
        return new ExampleRedisUsecase(fakeApiInfrastructure, redisProvider);
    }

    @Produces
    @ApplicationScoped
    public ExampleIdempotencyPort exampleIdempotencyUseCase(
            IFakeApiInfrastructure fakeApiInfrastructure,
            CacheLibraryService cacheLibraryService
    ) {
        /*LOGGER.info("services.AddSingleton<ExampleIdempotencyPort, ExampleIdempotencyUsecase>()");*/
        return new ExampleIdempotencyUsecase(fakeApiInfrastructure, cacheLibraryService);
    }
}
