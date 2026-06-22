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

/* ********************************************************************************************************
 * Copyright © 2026 Arify Labs - All rights reserved.
 *
 * Info                  : Composition Root - Application layer wiring (CacheLibraryService, use cases).
 *
 * By                    : Victor Jhampier Caxi Maquera
 * Email/Mobile/Phone    : victorjhampier@gmail.com | 968991*14
 *
 * Creation date         : 22/06/2026 3:05h
 **********************************************************************************************************/

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
