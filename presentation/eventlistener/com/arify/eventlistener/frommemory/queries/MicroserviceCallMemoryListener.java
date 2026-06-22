package com.arify.eventlistener.frommemory.queries;

import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.domain.entities.MicroserviceCallTraceEntity;
import com.arify.eventlistener.EventListenerLogger;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class MicroserviceCallMemoryListener {
    public final MicroserviceCallMemoryQueue container;
    public final Logger logger;
    public Thread task;
    public volatile boolean isRunning;

    public MicroserviceCallMemoryListener(MicroserviceCallMemoryQueue container) {
        this.container = container;
        this.logger = EventListenerLogger.setLogger();
        this.task = null;
        this.isRunning = false;
    }

    public void startAsync() {
        if (isRunning) {
            return;
        }

        isRunning = true;
        task = Thread.ofVirtual().name("microservice-call-memory-listener").start(this::executeAsync);
    }

    public void stopAsync() {
        if (!isRunning) {
            return;
        }

        isRunning = false;
        if (task != null && task.isAlive()) {
            task.interrupt();
        }
    }

    public void executeAsync() {
        while (isRunning) {
            try {
                List<MicroserviceCallTraceEntity> events = container.readAllAsync(100, 1.0);

                for (MicroserviceCallTraceEntity event : events) {
                    logger.warning(EventListenerLogger.format(
                            "WARNING",
                            "Memory event processed",
                            event.traceId(),
                            String.format("{\"method\":\"%s\",\"url\":\"%s\",\"status\":%d,\"operation\":\"%s\",\"req_time\":\"%s\",\"res_time\":\"%s\"}",
                                    event.method(),
                                    event.requestUrl(),
                                    event.responseStatusCode(),
                                    event.operationName(),
                                    event.requestDatetime(),
                                    event.responseDatetime()),
                            "null"));
                }
            } catch (Exception exception) {
                logger.log(Level.SEVERE, EventListenerLogger.format(
                        "SEVERE",
                        exception.getMessage(),
                        null,
                        "{\"operation\":\"MicroserviceCallMemoryListener.executeAsync\"}",
                        "\"" + exception + "\""));
            }
        }
    }
}
