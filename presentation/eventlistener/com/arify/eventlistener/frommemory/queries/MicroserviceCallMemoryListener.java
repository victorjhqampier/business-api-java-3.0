package com.arify.eventlistener.frommemory.queries;

import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.domain.entities.MicroserviceCallTraceEntity;
import com.arify.eventlistener.EventListenerLogger;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
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
                List<MicroserviceCallTraceEntity> events = container.readAllAsync(10, 1.0);

                if (!events.isEmpty()) {
                    for (MicroserviceCallTraceEntity event : events) {
                        Thread.sleep(Duration.ofSeconds(ThreadLocalRandom.current().nextInt(0, 2)));
                        logger.warning(EventListenerLogger.format(
                                "WARNING",
                                "Memory event detected",
                                event.traceId(),
                                "{\"request_url\":\"" + event.requestUrl() + "\","
                                        + "\"request_datetime\":\"" + event.requestDatetime() + "\","
                                        + "\"response_datetime\":\"" + event.responseDatetime() + "\"}",
                                "null"));
                    }
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
