package com.arify.config;

import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.eventlistener.frommemory.queries.MicroserviceCallMemoryListener;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;

import java.util.logging.Logger;

/**
 * Configuración de componentes de presentación y lifecycle de la aplicación.
 * 
 * <p><strong>Símil .NET:</strong> Equivale a registrar un {@code IHostedService} 
 * o {@code BackgroundService} que se arranca al inicio de la aplicación y 
 * se detiene al cerrar ({@code StartupEvent}/{@code ShutdownEvent}).</p>
 */
@ApplicationScoped
public class PresentationStartUp {
    private static final Logger LOGGER = Logger.getLogger(PresentationStartUp.class.getName());

    @Produces
    @ApplicationScoped
    public MicroserviceCallMemoryListener memoryListener(MicroserviceCallMemoryQueue memoryQueue) {
        LOGGER.info("services.AddSingleton<MicroserviceCallMemoryListener>()");
        return new MicroserviceCallMemoryListener(memoryQueue);
    }

    void onStart(@Observes StartupEvent ev, MicroserviceCallMemoryListener listener) {
        LOGGER.info(">>> Iniciando MicroserviceCallMemoryListener (Background Task)...");
        listener.startAsync();
    }

    void onStop(@Observes ShutdownEvent ev, MicroserviceCallMemoryListener listener) {
        LOGGER.info(">>> Deteniendo MicroserviceCallMemoryListener...");
        listener.stopAsync();
    }
}
