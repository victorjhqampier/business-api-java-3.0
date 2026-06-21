package com.arify.httpclientbuilder;

import com.arify.domain.commons.CancellationReason;
import com.arify.domain.commons.CancellationToken;
import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.domain.entities.HttpResponseEntity;
import com.arify.domain.entities.MicroserviceCallTraceEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HttpClientBuilder {
    public static final int MAX_TRACE_PAYLOAD_LENGTH = 4096;

    public final HttpClientConnector apiClient;
    public final Logger logger;
    public final ObjectMapper objectMapper;
    public final OffsetDateTime startDatetime;

    public String baseUrl;
    public String endpoint;
    public final Map<String, String> headers;
    public final Map<String, String> pathParams;
    public final Map<String, String> query;
    public boolean memoryEnabled;
    public MicroserviceCallMemoryQueue container;
    public String operationName;
    public String keyword;
    public Duration timeout;

    public HttpClientBuilder(HttpClientConnector apiClient, Logger logger) {
        this.apiClient = apiClient;
        this.logger = logger;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.startDatetime = OffsetDateTime.now();
        this.baseUrl = "";
        this.endpoint = "";
        this.headers = new HashMap<>();
        this.pathParams = new HashMap<>();
        this.query = new HashMap<>();
        this.memoryEnabled = false;
        this.container = null;
        this.operationName = "";
        this.keyword = null;
        this.timeout = null;
    }

    public HttpClientBuilder timeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("HTTP timeout must be positive.");
        }
        this.timeout = timeout;
        return this;
    }

    public HttpClientBuilder http(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        return this;
    }

    public HttpClientBuilder endpoint(String endpoint) {
        this.endpoint = endpoint.replaceAll("^/+", "");
        return this;
    }

    public HttpClientBuilder header(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    public HttpClientBuilder authorization(String key, String value) {
        this.headers.put("Authorization", key + " " + value);
        return this;
    }

    public HttpClientBuilder headers(Map<String, String> headers) {
        this.headers.putAll(headers);
        return this;
    }

    public HttpClientBuilder param(String key, String value) {
        this.pathParams.put(key, value);
        return this;
    }

    public HttpClientBuilder params(Map<String, String> params) {
        this.pathParams.putAll(params);
        return this;
    }

    public HttpClientBuilder query(String key, String value) {
        this.query.put(key, value);
        return this;
    }

    public HttpClientBuilder queries(Map<String, String> queries) {
        this.query.putAll(queries);
        return this;
    }

    public HttpClientBuilder withMemoryQueue(MicroserviceCallMemoryQueue queue, String operationName, String keyword) {
        this.memoryEnabled = true;
        this.container = queue;
        this.operationName = operationName;
        this.keyword = keyword;
        return this;
    }

    public CompletableFuture<HttpResponseEntity> get() {
        return send("GET", null);
    }

    public CompletableFuture<HttpResponseEntity> get(CancellationToken cancellationToken) {
        return send("GET", null, cancellationToken);
    }

    public CompletableFuture<HttpResponseEntity> post(Map<String, Object> body) {
        return send("POST", body);
    }

    public CompletableFuture<HttpResponseEntity> post(Map<String, Object> body, CancellationToken cancellationToken) {
        return send("POST", body, cancellationToken);
    }

    public CompletableFuture<HttpResponseEntity> put(Map<String, Object> body) {
        return send("PUT", body);
    }

    public CompletableFuture<HttpResponseEntity> put(Map<String, Object> body, CancellationToken cancellationToken) {
        return send("PUT", body, cancellationToken);
    }

    public void close() {
        apiClient.close();
    }

    public String buildFinalUrl() {
        String path = baseUrl + "/" + endpoint;

        for (Map.Entry<String, String> entry : pathParams.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (!path.contains(placeholder)) {
                throw new IllegalArgumentException(
                        String.format("Path param '%s' no tiene placeholder en endpoint. Use '{%s}' o envíelo como query param.",
                                entry.getKey(), entry.getKey()));
            }
            path = path.replace(placeholder, URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        return path;
    }

    public void ensureDefaultHeaders() {
        if (headers.isEmpty()) {
            headers.put("Content-Type", "application/json");
        }
    }

    public CompletableFuture<HttpResponseEntity> send(String method, Map<String, Object> body) {
        return send(method, body, null);
    }

    // Retorna CompletableFuture sin bloquear, permitiendo composición asíncrona.
    // Equivalente a retornar Task<HttpResponseMessage> en C#.
    public CompletableFuture<HttpResponseEntity> send(String method, Map<String, Object> body, CancellationToken cancellationToken) {
        ensureDefaultHeaders();
        String finalUrl = buildFinalUrl();

        if (cancellationToken != null && cancellationToken.isCancellationRequested()) {
            return CompletableFuture.failedFuture(new CancellationException("HTTP request cancelled before dispatch."));
        }

        return apiClient.requestAsync(
                        method,
                        finalUrl,
                        body,
                        query.isEmpty() ? null : query,
                        headers.isEmpty() ? null : headers,
                        timeout,
                        cancellationToken)
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        // Capturar traza de error
                        Throwable cause = throwable instanceof java.util.concurrent.CompletionException
                                ? throwable.getCause()
                                : throwable;

                        int statusCode = cancellationStatusCode(cancellationToken);
                        
                        logger.log(Level.WARNING, InfrastructureLogger.format(
                                "WARNING",
                                cause.getMessage(),
                                null,
                                "{\"operation\":\"HttpClientBuilder.send\",\"method\":\"" + method + "\",\"url\":\"" + finalUrl + "\"}",
                                "\"" + cause + "\""));

                        captureErrorTrace(method, body, cause.getMessage(), statusCode);
                    } else {
                        // Capturar traza exitosa
                        captureCompleteTrace(method, body, response);
                    }
                });
    }

    public void resetMemoryState() {
        this.memoryEnabled = false;
        this.container = null;
        this.operationName = "";
        this.keyword = null;
    }

    public String serializePayload(Object payload) {
        if (payload == null) {
            return null;
        }

        String serialized;
        try {
            if (payload instanceof Map || payload instanceof Iterable) {
                serialized = objectMapper.writeValueAsString(payload);
            } else {
                serialized = payload.toString();
            }
        } catch (JsonProcessingException exception) {
            serialized = payload.toString();
        }

        if (serialized.length() <= MAX_TRACE_PAYLOAD_LENGTH) {
            return serialized;
        }

        return serialized.substring(0, MAX_TRACE_PAYLOAD_LENGTH) + "...[truncated]";
    }

    public void captureCompleteTrace(String method, Map<String, Object> body, HttpResponseEntity response) {
        if (!memoryEnabled || container == null) {
            return;
        }

        MicroserviceCallTraceEntity traceEntity = new MicroserviceCallTraceEntity(
                UUID.randomUUID().toString(),
                headers.getOrDefault("message-identification", ""),
                headers.getOrDefault("channel-identification", ""),
                headers.getOrDefault("device-identification", ""),
                keyword != null ? keyword : "",
                method,
                "BusinessAPI2.0",
                operationName,
                response.url(),
                serializePayload(body),
                startDatetime,
                response.statusCode(),
                serializePayload(response.body()),
                OffsetDateTime.now());

        if (!container.tryPush(traceEntity)) {
            logger.warning(InfrastructureLogger.format(
                    "WARNING",
                    "Memory trace queue is full",
                    traceEntity.traceId(),
                    "{\"entity\":\"" + traceEntity + "\"}",
                    "null"));
        }
    }

    public void captureErrorTrace(String method, Map<String, Object> body, String errorMessage) {
        captureErrorTrace(method, body, errorMessage, 408);
    }

    public void captureErrorTrace(String method, Map<String, Object> body, String errorMessage, int statusCode) {
        if (!memoryEnabled || container == null) {
            return;
        }

        String requestUrl = buildFinalUrl();
        if (!query.isEmpty()) {
            String queryString = query.entrySet().stream()
                    .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                            + "="
                            + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            requestUrl = requestUrl + "?" + queryString;
        }

        String errorPayload;
        try {
            Map<String, String> errorMap = new HashMap<>();
            errorMap.put("error", errorMessage);
            errorPayload = objectMapper.writeValueAsString(errorMap);
        } catch (JsonProcessingException exception) {
            errorPayload = "{\"error\":\"" + errorMessage + "\"}";
        }

        MicroserviceCallTraceEntity traceEntity = new MicroserviceCallTraceEntity(
                UUID.randomUUID().toString(),
                headers.getOrDefault("message-identification", ""),
                headers.getOrDefault("channel-identification", ""),
                headers.getOrDefault("device-identification", ""),
                keyword != null ? keyword : "",
                method,
                "BusinessAPI2.0",
                operationName,
                requestUrl,
                serializePayload(body),
                startDatetime,
                statusCode,
                errorPayload,
                OffsetDateTime.now());

        if (!container.tryPush(traceEntity)) {
            logger.warning(InfrastructureLogger.format(
                    "WARNING",
                    "Memory trace queue is full",
                    traceEntity.traceId(),
                    "{\"entity\":\"" + traceEntity + "\"}",
                "null"));
        }
    }

    private int cancellationStatusCode(CancellationToken cancellationToken) {
        if (cancellationToken == null) {
            return 408;
        }

        return cancellationToken.cancellationReason()
                .map(reason -> reason == CancellationReason.TIMEOUT ? 408 : 499)
                .orElse(408);
    }
}
