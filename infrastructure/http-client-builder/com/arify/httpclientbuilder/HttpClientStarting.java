package com.arify.httpclientbuilder;

import java.time.Duration;
import java.util.concurrent.ExecutorService;

/* ********************************************************************************************************
 * Copyright © 2026 Arify Labs - All rights reserved.
 *
 * Info                  : Factory for HttpClientConnector initialization (reads ENV timeouts, injects virtual thread executor).
 *
 * By                    : Victor Jhampier Caxi Maquera
 * Email/Mobile/Phone    : victorjhampier@gmail.com | 968991*14
 *
 * Creation date         : 22/06/2026 3:05h
 **********************************************************************************************************/

public final class HttpClientStarting {
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
        }

        if (connectTimeoutStr == null || connectTimeoutStr.isBlank()) {
            connectTimeoutStr = DEFAULT_CONNECT_TIMEOUT_SECONDS;
        }

        Duration requestTimeout;
        Duration connectTimeout;

        try {
            requestTimeout = Duration.ofSeconds(Long.parseLong(requestTimeoutStr));
        } catch (NumberFormatException ex) {
            requestTimeout = Duration.ofSeconds(Long.parseLong(DEFAULT_REQUEST_TIMEOUT_SECONDS));
        }

        try {
            connectTimeout = Duration.ofSeconds(Long.parseLong(connectTimeoutStr));
        } catch (NumberFormatException ex) {
            connectTimeout = Duration.ofSeconds(Long.parseLong(DEFAULT_CONNECT_TIMEOUT_SECONDS));
        }

        return new HttpClientConnector(requestTimeout, connectTimeout, executor);
    }
}
