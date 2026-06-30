package com.arify.httpclientbuilder;

import com.arify.domain.commons.CancellationReason;
import com.arify.domain.commons.CancellationToken;
import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.domain.entities.MicroserviceCallTraceEntity;
import com.arify.httpclientbuilder.entities.HttpResponseEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLEncoder;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public class HttpClientBuilder {
    private static final int MAX_TRACE_PAYLOAD_LENGTH = 4096;
    private static final int STATUS_OK = 200;
    private static final int STATUS_REQUEST_TIMEOUT = 408;
    private static final int STATUS_CLIENT_CLOSED_REQUEST = 499;
    private static final int STATUS_INTERNAL_SERVER_ERROR = 500;
    private static final int STATUS_SERVICE_UNAVAILABLE = 503;

    private final HttpClientConnector apiClient;
    private final Logger logger;
    private final OffsetDateTime startDatetime;

    private String baseUrl;
    private String endpoint;
    private final Map<String, String> headers;
    private final Map<String, String> pathParams;
    private final Map<String, String> query;
    private boolean memoryEnabled;
    private MicroserviceCallMemoryQueue container;
    private String operationName;
    private String keyword;
    private Duration timeout;

    public HttpClientBuilder(HttpClientConnector apiClient, Logger logger) {
        this.apiClient = Objects.requireNonNull(apiClient, "apiClient cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
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

    public CompletableFuture<HttpResponseEntity> delete() {
        return send("DELETE", null);
    }

    public CompletableFuture<HttpResponseEntity> delete(CancellationToken cancellationToken) {
        return send("DELETE", null, cancellationToken);
    }

    public CompletableFuture<HttpResponseEntity> send(String method, Map<String, Object> body) {
        return send(method, body, null);
    }

    public CompletableFuture<HttpResponseEntity> send(String method, Map<String, Object> body, CancellationToken cancellationToken) {
        headers.putIfAbsent("Content-Type", "application/json");

        String finalUrl;
        try {
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
            finalUrl = apiClient.buildUrlWithParams(path, query.isEmpty() ? null : query);
        } catch (Exception exception) {
            HttpResponseEntity response = errorResponse("", STATUS_INTERNAL_SERVER_ERROR, exception);
            captureMemoryEvent(method, "", body, response, exception);
            return CompletableFuture.completedFuture(response);
        }

        if (cancellationToken != null && cancellationToken.isCancellationRequested()) {
            HttpResponseEntity response = errorResponse(finalUrl, cancellationStatus(cancellationToken),
                    new CancellationException("HTTP request cancelled before dispatch."));
            captureMemoryEvent(method, finalUrl, body, response, null);
            return CompletableFuture.completedFuture(response);
        }

        return apiClient.requestAsync(
                        method,
                        finalUrl,
                        body,
                        null,
                        headers.isEmpty() ? null : headers,
                        timeout,
                        cancellationToken)
                .handle((response, throwable) -> {
                    if (throwable == null) {
                        HttpResponseEntity safeResponse = response.url() == null || response.url().isBlank()
                                ? new HttpResponseEntity(response.statusCode(), response.body(), response.headers(), finalUrl)
                                : response;
                        captureMemoryEvent(method, finalUrl, body, safeResponse, null);
                        return safeResponse;
                    }

                    Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                            ? throwable.getCause()
                            : throwable;
                    HttpResponseEntity errorResponse = errorResponse(finalUrl, errorStatus(cause, cancellationToken), cause);
                    captureMemoryEvent(method, finalUrl, body, errorResponse, cause);
                    return errorResponse;
                });
    }

    private void captureMemoryEvent(String method, String requestUrl, Map<String, Object> body, HttpResponseEntity response, Throwable throwable) {
        if (!memoryEnabled || container == null) {
            return;
        }

        String responseBody = serializePayload(response.body());
        String responseStatusCode = String.valueOf(response.statusCode());

        MicroserviceCallTraceEntity traceEntity = new MicroserviceCallTraceEntity(
                UUID.randomUUID().toString(),
                headers.getOrDefault("message-identification", ""),
                headers.getOrDefault("channel-identification", ""),
                headers.getOrDefault("device-identification", ""),
                keyword != null ? keyword : "",
                method,
                "BusinessAPI2.0",
                operationName,
                response.url() == null ? requestUrl : response.url(),
                serializePayload(body),
                startDatetime,
                response.statusCode(),
                responseBody,
                OffsetDateTime.now());

        container.tryPush(traceEntity);

        if (response.statusCode() != STATUS_OK) {
            InfrastructureLogger.logMemoryEvent(
                    logger,
                    operationName,
                    keyword,
                    method,
                    response.url() == null ? requestUrl : response.url(),
                    serializePayload(headers),
                    serializePayload(body),
                    responseBody,
                    responseStatusCode,
                    throwable == null ? null : throwable.toString());
        }
    }

    private HttpResponseEntity errorResponse(String finalUrl, int statusCode, Throwable throwable) {
        ObjectNode body = HttpClientConnector.getObjectMapper().createObjectNode();
        body.put("error", throwable == null || throwable.getMessage() == null ? "HTTP request failed" : throwable.getMessage());
        return new HttpResponseEntity(statusCode, body, Map.of(), finalUrl == null ? "" : finalUrl);
    }

    private int errorStatus(Throwable throwable, CancellationToken cancellationToken) {
        if (cancellationToken != null && cancellationToken.isCancellationRequested()) {
            return cancellationStatus(cancellationToken);
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CancellationException) {
                return STATUS_CLIENT_CLOSED_REQUEST;
            }
            if (current instanceof TimeoutException || current instanceof HttpTimeoutException) {
                return STATUS_REQUEST_TIMEOUT;
            }
            current = current.getCause();
        }

        return STATUS_SERVICE_UNAVAILABLE;
    }

    private int cancellationStatus(CancellationToken cancellationToken) {
        return cancellationToken.cancellationReason()
                .map(reason -> reason == CancellationReason.TIMEOUT ? STATUS_REQUEST_TIMEOUT : STATUS_CLIENT_CLOSED_REQUEST)
                .orElse(STATUS_CLIENT_CLOSED_REQUEST);
    }

    private String serializePayload(Object payload) {
        if (payload == null) {
            return null;
        }

        String serialized;
        try {
            serialized = HttpClientConnector.getObjectMapper().writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            serialized = payload.toString();
        }

        if (serialized.length() <= MAX_TRACE_PAYLOAD_LENGTH) {
            return serialized;
        }

        return serialized.substring(0, MAX_TRACE_PAYLOAD_LENGTH) + "...[truncated]";
    }
}
