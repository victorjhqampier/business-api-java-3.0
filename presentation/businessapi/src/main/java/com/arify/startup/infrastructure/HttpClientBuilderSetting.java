package com.arify.startup.infrastructure;

import com.arify.httpclientbuilder.HttpClientConnector;
import com.arify.httpclientbuilder.HttpClientStarting;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import java.util.concurrent.ExecutorService;

@ApplicationScoped
public class HttpClientBuilderSetting {
    @Produces
    @ApplicationScoped
    public HttpClientConnector httpClientConnector(@Named("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        /*LOGGER.info("services.AddSingleton<HttpClientConnector>()");*/
        return HttpClientStarting.init(virtualThreadExecutor);
    }
}
