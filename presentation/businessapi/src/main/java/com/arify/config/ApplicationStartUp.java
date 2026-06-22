package com.arify.config;

import com.arify.application.ports.ExamplePort;
import com.arify.application.usecases.exampleusecase.ExampleUseCase;
import com.arify.domain.containers.cachelibraryservice.CacheLibraryService;
import com.arify.domain.containers.cachelibraryservice.ICacheInfrastructure;
import com.arify.domain.interfaces.IFakeApiInfrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

@ApplicationScoped
public class ApplicationStartUp {
    private static final Logger LOGGER = Logger.getLogger(ApplicationStartUp.class.getName());

    @Produces
    @ApplicationScoped
    public CacheLibraryService cacheLibraryService(ICacheInfrastructure cacheInfrastructure) {
        LOGGER.info("services.AddSingleton<CacheLibraryService>()");
        return new CacheLibraryService(cacheInfrastructure);
    }

    @Produces
    @ApplicationScoped
    public ExamplePort exampleUseCase(
            IFakeApiInfrastructure fakeApiInfrastructure, 
            CacheLibraryService cacheLibraryService,
            @Named("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        LOGGER.info("services.AddSingleton<ExamplePort, ExampleUseCase>()");
        return new ExampleUseCase(fakeApiInfrastructure, cacheLibraryService, virtualThreadExecutor);
    }
}
