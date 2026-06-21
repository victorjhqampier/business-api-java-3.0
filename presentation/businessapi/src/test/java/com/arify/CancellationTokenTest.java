package com.arify;

import com.arify.domain.commons.CancellationReason;
import com.arify.domain.commons.CancellationToken;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancellationTokenTest {

    @Test
    void withDefaultUsesNineSecondTimeout() {
        CancellationToken token = CancellationToken.withDefault();

        assertEquals(CancellationToken.DEFAULT_TIMEOUT, token.timeout());
        assertFalse(token.isCancellationRequested());
    }

    @Test
    void invalidTimeoutFallsBackToDefault() {
        CancellationToken token = CancellationToken.withTimeout(Duration.ZERO);

        assertEquals(CancellationToken.DEFAULT_TIMEOUT, token.timeout());
    }

    @Test
    void cancelIsIdempotentAndRunsCallbacksOnce() {
        CancellationToken token = CancellationToken.withDefault();
        AtomicInteger callbackCount = new AtomicInteger();

        token.onCancel(callbackCount::incrementAndGet);

        assertTrue(token.cancel(CancellationReason.CLIENT_DISCONNECTED));
        assertFalse(token.cancel(CancellationReason.TIMEOUT));
        assertEquals(1, callbackCount.get());
        assertTrue(token.isCancellationRequested());
        assertEquals(CancellationReason.CLIENT_DISCONNECTED, token.cancellationReason().orElseThrow());
    }

    @Test
    void callbackRegisteredAfterCancellationRunsImmediately() {
        CancellationToken token = CancellationToken.withDefault();
        AtomicInteger callbackCount = new AtomicInteger();

        token.cancel(CancellationReason.INTERRUPTED);
        token.onCancel(callbackCount::incrementAndGet);

        assertEquals(1, callbackCount.get());
    }

    @Test
    void expiredTokenReturnsZeroRemainingTimeout() throws InterruptedException {
        CancellationToken token = CancellationToken.withTimeout(Duration.ofMillis(1));

        Thread.sleep(5);

        assertEquals(Duration.ZERO, token.remainingTimeout());
        assertTrue(token.isCancellationRequested());
        assertEquals(CancellationReason.TIMEOUT, token.cancellationReason().orElseThrow());
    }
}
