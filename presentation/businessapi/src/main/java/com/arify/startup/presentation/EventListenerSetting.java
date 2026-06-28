package com.arify.startup.presentation;

import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.eventlistener.frommemory.queries.MicroserviceCallMemoryListener;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class EventListenerSetting {

    @Produces
    @ApplicationScoped
    public MicroserviceCallMemoryQueue memoryQueue() {
        /*LOGGER.info("services.AddSingleton<MicroserviceCallMemoryQueue>()");*/
        return new MicroserviceCallMemoryQueue(1500);
    }

    @Produces
    @ApplicationScoped
    public MicroserviceCallMemoryListener memoryListener(MicroserviceCallMemoryQueue memoryQueue) {
        /*LOGGER.info("services.AddSingleton<MicroserviceCallMemoryListener>()");*/
        return new MicroserviceCallMemoryListener(memoryQueue);
    }

    void onStart(@Observes StartupEvent ev, MicroserviceCallMemoryListener listener) {
        listener.startAsync();
    }

    void onStop(@Observes ShutdownEvent ev, MicroserviceCallMemoryListener listener) {
        listener.stopAsync();
    }
}
