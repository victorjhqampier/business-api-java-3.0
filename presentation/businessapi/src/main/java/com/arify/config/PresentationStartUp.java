package com.arify.config;

import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.eventlistener.frommemory.queries.MicroserviceCallMemoryListener;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import java.util.logging.Logger;

/* ********************************************************************************************************
 * Copyright © 2026 Arify Labs - All rights reserved.
 *
 * Info                  : Composition Root - Presentation lifecycle (startup/shutdown hooks, background services).
 *
 * By                    : Victor Jhampier Caxi Maquera
 * Email/Mobile/Phone    : victorjhampier@gmail.com | 968991*14
 *
 * Creation date         : 22/06/2026 3:05h
 **********************************************************************************************************/

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
