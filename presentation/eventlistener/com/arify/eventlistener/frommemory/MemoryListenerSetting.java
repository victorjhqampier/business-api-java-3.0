package com.arify.eventlistener.frommemory;

import com.arify.eventlistener.frommemory.queries.MicroserviceCallMemoryListener;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class MemoryListenerSetting {
    public final MicroserviceCallMemoryListener listener;

    public MemoryListenerSetting(MicroserviceCallMemoryListener listener) {
        this.listener = listener;
    }

    public MicroserviceCallMemoryListener addServices() {
        listener.startAsync();
        return listener;
    }

    void onStart(@Observes StartupEvent event) {
        addServices();
    }

    void onStop(@Observes ShutdownEvent event) {
        listener.stopAsync();
    }
}
