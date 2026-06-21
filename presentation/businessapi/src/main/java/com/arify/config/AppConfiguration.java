package com.arify.config;

import com.arify.application.ports.ExamplePort;
import com.arify.application.usecases.exampleusecase.ExampleUseCase;
import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.domain.interfaces.IFakeApiInfrastructure;
import com.arify.fakeapiinfra.queries.FakeApiCommand;
import com.arify.httpclientbuilder.HttpClientConnector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    // Equivalente al .NET ThreadPool optimizado para I/O-bound tasks mediante Virtual Threads (Java 21).
    // Cada tarea asíncrona en la capa de aplicación usará hilos ligeros en lugar del ForkJoinPool común,
    // permitiendo escalar a miles de operaciones concurrentes sin bloquear platform threads.
    @Produces
    @ApplicationScoped
    @Named("virtualThreadExecutor")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    public void shutdownVirtualThreadExecutor(
            @Disposes @Named("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        virtualThreadExecutor.shutdown();
    }

    @Produces
    @ApplicationScoped
    public ExamplePort exampleUseCase(
            IFakeApiInfrastructure fakeApiInfrastructure,
            @Named("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        return new ExampleUseCase(fakeApiInfrastructure, virtualThreadExecutor);
    }
}
