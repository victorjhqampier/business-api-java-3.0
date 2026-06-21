package com.arify.domain.commons;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class CancellationToken {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(9);

    private final Duration timeout;
    private final long deadlineNanos;
    private final AtomicReference<CancellationReason> cancellationReason;
    private final List<Runnable> cancellationCallbacks;

    public CancellationToken(Duration timeout) {
        this.timeout = normalizeTimeout(timeout);
        this.deadlineNanos = System.nanoTime() + this.timeout.toNanos();
        this.cancellationReason = new AtomicReference<>();
        this.cancellationCallbacks = new CopyOnWriteArrayList<>();
    }

    public static CancellationToken withDefault() {
        return new CancellationToken(DEFAULT_TIMEOUT);
    }

    public static CancellationToken withTimeout(Duration timeout) {
        return new CancellationToken(timeout);
    }

    public Duration timeout() {
        return timeout;
    }

    public Duration remainingTimeout() {
        if (cancellationReason.get() != null) {
            return Duration.ZERO;
        }

        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            cancel(CancellationReason.TIMEOUT);
            return Duration.ZERO;
        }

        return Duration.ofNanos(remainingNanos);
    }

    public boolean isCancellationRequested() {
        remainingTimeout();
        return cancellationReason.get() != null;
    }

    public Optional<CancellationReason> cancellationReason() {
        remainingTimeout();
        return Optional.ofNullable(cancellationReason.get());
    }

    public boolean cancel(CancellationReason reason) {
        Objects.requireNonNull(reason, "Cancellation reason is required.");
        List<Runnable> callbacksToRun;

        synchronized (cancellationCallbacks) {
            if (!cancellationReason.compareAndSet(null, reason)) {
                return false;
            }

            callbacksToRun = List.copyOf(cancellationCallbacks);
            cancellationCallbacks.clear();
        }

        for (Runnable callback : callbacksToRun) {
            callback.run();
        }
        return true;
    }

    public void onCancel(Runnable callback) {
        Objects.requireNonNull(callback, "Cancellation callback is required.");
        boolean runNow;

        synchronized (cancellationCallbacks) {
            if (isCancellationRequested()) {
                runNow = true;
            } else {
                cancellationCallbacks.add(callback);
                runNow = false;
            }
        }

        if (runNow) {
            callback.run();
        }
    }

    private static Duration normalizeTimeout(Duration timeout) {
        return timeout == null || timeout.isNegative() || timeout.isZero()
                ? DEFAULT_TIMEOUT
                : timeout;
    }
}
