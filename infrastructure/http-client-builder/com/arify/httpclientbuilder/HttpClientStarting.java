package com.arify.httpclientbuilder;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

/**
 * Factory para inicialización de HttpClientConnector.
 * 
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Leer configuraciones de timeouts desde variables de entorno (priorizando ENV sobre defaults).</li>
 *   <li>Inyectar el ExecutorService (Virtual Threads) al conector HTTP.</li>
 *   <li>Proveer un punto único de inicialización para el Composition Root.</li>
 * </ul>
 * 
 * <p>Patrón homologado con RedisStarting y FakeApiStarting.</p>
 */
public final class HttpClientStarting {
    private static final Logger LOGGER = Logger.getLogger(HttpClientStarting.class.getName());
    
    private static final String DEFAULT_REQUEST_TIMEOUT_SECONDS = "5";
    private static final String DEFAULT_CONNECT_TIMEOUT_SECONDS = "1";

    private HttpClientStarting() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Inicializa el HttpClientConnector con Virtual Threads.
     * 
     * @param executor ExecutorService de hilos virtuales para operaciones I/O.
     * @return HttpClientConnector configurado.
     */
    public static HttpClientConnector init(ExecutorService executor) {
        // Priorizar variables de entorno sobre valores por defecto
        String requestTimeoutStr = System.getenv("HTTP_CLIENT_REQUEST_TIMEOUT");
        String connectTimeoutStr = System.getenv("HTTP_CLIENT_CONNECT_TIMEOUT");

        // Fallback a valores por defecto si no están en el environment
        if (requestTimeoutStr == null || requestTimeoutStr.isBlank()) {
            requestTimeoutStr = DEFAULT_REQUEST_TIMEOUT_SECONDS;
            LOGGER.warning("HTTP_CLIENT_REQUEST_TIMEOUT not found in environment, using default: " 
                    + DEFAULT_REQUEST_TIMEOUT_SECONDS + "s");
        }

        if (connectTimeoutStr == null || connectTimeoutStr.isBlank()) {
            connectTimeoutStr = DEFAULT_CONNECT_TIMEOUT_SECONDS;
            LOGGER.warning("HTTP_CLIENT_CONNECT_TIMEOUT not found in environment, using default: " 
                    + DEFAULT_CONNECT_TIMEOUT_SECONDS + "s");
        }

        Duration requestTimeout;
        Duration connectTimeout;

        try {
            requestTimeout = Duration.ofSeconds(Long.parseLong(requestTimeoutStr));
        } catch (NumberFormatException ex) {
            LOGGER.warning("HTTP_CLIENT_REQUEST_TIMEOUT invalid format, using default: " 
                    + DEFAULT_REQUEST_TIMEOUT_SECONDS + "s");
            requestTimeout = Duration.ofSeconds(Long.parseLong(DEFAULT_REQUEST_TIMEOUT_SECONDS));
        }

        try {
            connectTimeout = Duration.ofSeconds(Long.parseLong(connectTimeoutStr));
        } catch (NumberFormatException ex) {
            LOGGER.warning("HTTP_CLIENT_CONNECT_TIMEOUT invalid format, using default: " 
                    + DEFAULT_CONNECT_TIMEOUT_SECONDS + "s");
            connectTimeout = Duration.ofSeconds(Long.parseLong(DEFAULT_CONNECT_TIMEOUT_SECONDS));
        }

        LOGGER.info(String.format(
                "Initializing HttpClientConnector: requestTimeout=%ds, connectTimeout=%ds, executor=VirtualThreads",
                requestTimeout.getSeconds(), connectTimeout.getSeconds()));

        return new HttpClientConnector(requestTimeout, connectTimeout, executor);
    }
}
