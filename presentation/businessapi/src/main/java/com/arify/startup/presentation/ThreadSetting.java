package com.arify.startup.presentation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
public class ThreadSetting {
    // Virtual Threads (Java 21) - Equivalente al .NET ThreadPool para I/O-bound
    @Produces
    @Named("virtualThreadExecutor")
    public ExecutorService virtualThreadExecutor() {
        /*LOGGER.info("services.AddSingleton<ExecutorService>(virtualThreadExecutor)");*/
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    public void shutdownVirtualThreadExecutor(@Disposes @Named("virtualThreadExecutor") ExecutorService executor) {
        executor.shutdown();
    }
}
