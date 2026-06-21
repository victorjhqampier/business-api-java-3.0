package com.arify.httpclientbuilder;

import com.arify.domain.commons.CancellationReason;
import com.arify.domain.commons.CancellationToken;
import com.arify.domain.entities.HttpResponseEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HttpClientConnector {
    public final HttpClient client;
    public final ObjectMapper objectMapper;
    public final Duration defaultTimeout;
    public final Logger logger;

    public HttpClientConnector(Duration defaultTimeout, Duration connectTimeout) {
        this.defaultTimeout = defaultTimeout;
        this.logger = InfrastructureLogger.setLogger();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void close() {
        // java.net.http.HttpClient no requiere cierre explicito
        // El pool de conexiones se gestiona internamente
    }

    public CompletableFuture<HttpResponseEntity> getAsync(String url, Map<String, String> params, Map<String, String> headers, Duration timeout) {
        return requestAsync("GET", url, null, params, headers, timeout);
    }

    public CompletableFuture<HttpResponseEntity> postAsync(String url, Object data, Map<String, String> params, Map<String, String> headers, Duration timeout) {
        return requestAsync("POST", url, data, params, headers, timeout);
    }

    public CompletableFuture<HttpResponseEntity> putAsync(String url, Object data, Map<String, String> params, Map<String, String> headers, Duration timeout) {
        return requestAsync("PUT", url, data, params, headers, timeout);
    }

    public CompletableFuture<HttpResponseEntity> requestAsync(String method, String url, Object json, Map<String, String> params, Map<String, String> headers, Duration timeout) {
        return requestAsync(method, url, json, params, headers, timeout, null);
    }

    // Equivalente a HttpClient.SendAsync en C# .NET.
    // Retorna CompletableFuture para permitir composición asíncrona sin bloquear platform threads.
    // El CancellationToken se vincula al Future para abortar la conexión si se cancela,
    // similar a pasar un CancellationToken a SendAsync en .NET.
    public CompletableFuture<HttpResponseEntity> requestAsync(
            String method,
            String url,
            Object json,
            Map<String, String> params,
            Map<String, String> headers,
            Duration timeout,
            CancellationToken cancellationToken) {
        
        // Validación temprana de cancelación antes de iniciar el request
        if (cancellationToken != null && cancellationToken.isCancellationRequested()) {
            return CompletableFuture.failedFuture(cancellationException(cancellationToken));
        }

        try {
            String finalUrl = buildUrlWithParams(url, params);
            String jsonBody = serializeJson(json);
            Duration requestTimeout = resolveRequestTimeout(timeout, cancellationToken);

            // Si el timeout ya expiró, fallar inmediatamente
            if (requestTimeout.isZero()) {
                if (cancellationToken != null) {
                    cancellationToken.cancel(CancellationReason.TIMEOUT);
                }
                return CompletableFuture.failedFuture(cancellationException(cancellationToken));
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(finalUrl))
                    .timeout(requestTimeout);

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestBuilder.header(entry.getKey(), entry.getValue());
                }
            }

            HttpRequest.BodyPublisher bodyPublisher = jsonBody != null
                    ? HttpRequest.BodyPublishers.ofString(jsonBody)
                    : HttpRequest.BodyPublishers.noBody();

            requestBuilder.method(method, bodyPublisher);

            HttpRequest request = requestBuilder.build();
            CompletableFuture<HttpResponse<String>> responseFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            
            // Vincular el CancellationToken al Future para abortar el request HTTP si se cancela.
            // Esto es equivalente a pasar el CancellationToken a HttpClient.SendAsync en C#.
            if (cancellationToken != null) {
                cancellationToken.onCancel(() -> responseFuture.cancel(true));
            }

            // Retornamos el Future sin bloquear, permitiendo composición asíncrona.
            // Equivalente a retornar Task<HttpResponseMessage> en C# sin hacer .Result
            return responseFuture
                    .thenApply(response -> {
                        HttpResponseEntity result = buildResponse(response);

                        if (result.statusCode() >= 400) {
                            logger.warning(String.format(
                                    "HTTP request returned non-success status. Method=[%s] Url=[%s] StatusCode=[%d]",
                                    method, result.url(), result.statusCode()));
                        }

                        return result;
                    })
                    .exceptionally(throwable -> {
                        Throwable cause = throwable instanceof CompletionException 
                                ? throwable.getCause() 
                                : throwable;

                        // Si el token ya fue cancelado, lanzar CancellationException
                        if (cancellationToken != null && cancellationToken.isCancellationRequested()) {
                            throw new CompletionException(cancellationException(cancellationToken));
                        }

                        // Si es una CancellationException del Future, propagarla
                        if (cause instanceof CancellationException) {
                            throw new CompletionException(cancellationException(cancellationToken));
                        }

                        // Si es un HttpTimeoutException, marcar el token como timeout y lanzar
                        if (cause instanceof HttpTimeoutException) {
                            if (cancellationToken != null) {
                                cancellationToken.cancel(CancellationReason.TIMEOUT);
                            }
                            logger.warning(String.format("HTTP request timed out. Method=[%s] Url=[%s]", method, url));
                            throw new CompletionException(new java.util.concurrent.TimeoutException("HTTP request timed out"));
                        }

                        // Para cualquier otra excepción, logear y propagar
                        logger.log(Level.WARNING, String.format(
                                "HTTP request failed. Method=[%s] Url=[%s] Error=[%s]",
                                method, url, cause.getMessage()), cause);
                        throw new CompletionException(new RuntimeException("HTTP request failed", cause));
                    });

        } catch (Exception exception) {
            logger.log(Level.WARNING, String.format(
                    "HTTP request failed during setup. Method=[%s] Url=[%s] Error=[%s]",
                    method, url, exception.getMessage()), exception);
            return CompletableFuture.failedFuture(new RuntimeException("HTTP request failed during setup", exception));
        }
    }

    public HttpResponseEntity buildResponse(HttpResponse<String> response) {
        String body = response.body();
        String contentBody = readJsonContent(body, response.headers().firstValue("content-type").orElse(""));

        Map<String, List<String>> headers = new HashMap<>(response.headers().map());

        return new HttpResponseEntity(
                response.statusCode(),
                contentBody,
                headers,
                response.uri().toString());
    }

    public String readJsonContent(String body, String contentType) {
        if (body == null || body.isEmpty()) {
            return null;
        }

        if (!contentType.toLowerCase().contains("json")) {
            return body;
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            if (jsonNode.isArray()) {
                Map<String, JsonNode> wrapper = new HashMap<>();
                wrapper.put("data", jsonNode);
                return objectMapper.writeValueAsString(wrapper);
            }
            return body;
        } catch (JsonProcessingException exception) {
            return body;
        }
    }

    public String buildUrlWithParams(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }

        String queryString = params.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        return url.contains("?") ? url + "&" + queryString : url + "?" + queryString;
    }

    public String serializeJson(Object data) {
        if (data == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("Failed to serialize JSON body", exception);
        }
    }

    public Duration resolveRequestTimeout(Duration timeout, CancellationToken cancellationToken) {
        Duration configuredTimeout = timeout != null ? timeout : defaultTimeout;
        if (cancellationToken == null) {
            return configuredTimeout;
        }

        Duration remainingTimeout = cancellationToken.remainingTimeout();
        if (remainingTimeout.isZero()) {
            return Duration.ZERO;
        }

        return remainingTimeout.compareTo(configuredTimeout) < 0 ? remainingTimeout : configuredTimeout;
    }

    private CancellationException cancellationException(CancellationToken cancellationToken) {
        String reason = cancellationToken == null
                ? "unknown"
                : cancellationToken.cancellationReason()
                .map(Enum::name)
                .orElse("unknown");
        return new CancellationException("HTTP request cancelled. Reason=[" + reason + "]");
    }
}
