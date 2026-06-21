package com.arify;

import com.arify.domain.commons.CancellationReason;
import com.arify.domain.commons.CancellationToken;
import com.arify.httpclientbuilder.HttpClientConnector;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpClientConnectorCancellationTest {

    @Test
    void cancelledTokenCancelsInFlightHttpRequest() throws Exception {
        HttpClientConnector connector = new HttpClientConnector(Duration.ofSeconds(5), Duration.ofSeconds(1));
        CancellationToken token = CancellationToken.withTimeout(Duration.ofSeconds(5));

        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch accepted = new CountDownLatch(1);
            ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
            serverExecutor.submit(() -> respondSlowly(server, accepted));

            String url = "http://127.0.0.1:" + server.getLocalPort() + "/";
            CompletableFuture<Throwable> request = CompletableFuture.supplyAsync(() -> executeRequest(connector, token, url));

            assertTrue(accepted.await(1, TimeUnit.SECONDS));
            long startedAt = System.nanoTime();
            token.cancel(CancellationReason.CLIENT_DISCONNECTED);
            Throwable thrown = request.get(1, TimeUnit.SECONDS);
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

            serverExecutor.shutdownNow();

            assertNotNull(thrown);
            assertInstanceOf(CancellationException.class, thrown);
            assertTrue(elapsedMillis < 1_000);
        }
    }

    private Throwable executeRequest(HttpClientConnector connector, CancellationToken token, String url) {
        try {
            connector.requestAsync("GET", url, null, null, null, Duration.ofSeconds(5), token);
            return null;
        } catch (Throwable exception) {
            return exception;
        }
    }

    private void respondSlowly(ServerSocket server, CountDownLatch accepted) {
        try (Socket socket = server.accept()) {
            accepted.countDown();
            Thread.sleep(2_000);
            byte[] response = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK"
                    .getBytes(StandardCharsets.UTF_8);
            socket.getOutputStream().write(response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            if (!server.isClosed()) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
