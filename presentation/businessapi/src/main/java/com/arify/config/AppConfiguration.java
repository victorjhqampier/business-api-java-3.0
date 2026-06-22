package com.arify.config;

import com.arify.application.ports.ExamplePort;
import com.arify.application.usecases.exampleusecase.ExampleUseCase;
import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.domain.interfaces.IFakeApiInfrastructure;
import com.arify.eventlistener.frommemory.queries.MicroserviceCallMemoryListener;
import com.arify.fakeapiinfra.queries.FakeApiCommand;
import com.arify.httpclientbuilder.HttpClientConnector;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

@ApplicationScoped
public class AppConfiguration {
    private static final Logger LOGGER = Logger.getLogger(AppConfiguration.class.getName());

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

    /**
     * Inicializa el MicroserviceCallMemoryListener como un Singleton.
     * Este componente es el encargado de procesar las trazas que la infraestructura
     * deposita en la cola de memoria.
     */
    @Produces
    @ApplicationScoped
    public MicroserviceCallMemoryListener memoryListener(MicroserviceCallMemoryQueue memoryQueue) {
        return new MicroserviceCallMemoryListener(memoryQueue);
    }

    /**
     * Punto de entrada de inicio de la aplicación (Quarkus Startup).
     * Invoca el método startAsync() para arrancar el Hilo Virtual que procesa los logs.
     */
    void onStart(@Observes StartupEvent ev, MicroserviceCallMemoryListener listener) {
        LOGGER.info(">>> Iniciando MicroserviceCallMemoryListener (Background Task)...");
        listener.startAsync();
    }

    /**
     * Punto de salida de la aplicación (Quarkus Shutdown).
     * Asegura que el hilo virtual de procesamiento de eventos se detenga de forma limpia.
     */
    void onStop(@Observes ShutdownEvent ev, MicroserviceCallMemoryListener listener) {
        LOGGER.info(">>> Deteniendo MicroserviceCallMemoryListener...");
        listener.stopAsync();
    }
}
