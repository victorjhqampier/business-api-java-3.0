package com.arify.config;

import com.arify.application.ports.ExamplePort;
import com.arify.application.usecases.exampleusecase.ExampleUseCase;
import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.domain.interfaces.IFakeApiInfrastructure;
import com.arify.fakeapiinfra.queries.FakeApiCommand;
import com.arify.httpclientbuilder.HttpClientConnector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Duration;

@ApplicationScoped
public class AppConfiguration {
    @Produces
    @ApplicationScoped
    public MicroserviceCallMemoryQueue memoryQueue() {
        return new MicroserviceCallMemoryQueue(1500);
    }

    @Produces
    @ApplicationScoped
    public HttpClientConnector httpClientConnector() {
        return new HttpClientConnector(Duration.ofSeconds(5), Duration.ofSeconds(1));
    }

    @Produces
    @ApplicationScoped
    public IFakeApiInfrastructure fakeApiInfrastructure(
            MicroserviceCallMemoryQueue memoryQueue,
            HttpClientConnector httpClientConnector) {
        return new FakeApiCommand(memoryQueue, httpClientConnector);
    }

    @Produces
    @ApplicationScoped
    public ExamplePort exampleUseCase(IFakeApiInfrastructure fakeApiInfrastructure) {
        return new ExampleUseCase(fakeApiInfrastructure);
    }
}
