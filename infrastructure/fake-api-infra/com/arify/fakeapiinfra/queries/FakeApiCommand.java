package com.arify.fakeapiinfra.queries;

import com.arify.domain.commons.CancellationToken;
import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.domain.entities.FakeApiEntity;
import com.arify.domain.entities.HttpResponseEntity;
import com.arify.domain.interfaces.IFakeApiInfrastructure;
import com.arify.fakeapiinfra.FakeApiStarting;
import com.arify.httpclientbuilder.HttpClientBuilder;
import com.arify.httpclientbuilder.HttpClientConnector;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Logger;

// Implementa la interfaz de dominio IFakeApiInfrastructure.
// Retorna CompletableFuture<Optional<T>> para permitir composición asíncrona,
// equivalente a Task<T> en C# .NET.
public class FakeApiCommand implements IFakeApiInfrastructure {
    public final MicroserviceCallMemoryQueue queue;
    public final HttpClientConnector httpConnector;
    public final Logger logger;
    public final ObjectMapper objectMapper;

    public FakeApiCommand(MicroserviceCallMemoryQueue queue, HttpClientConnector httpConnector) {
        this.queue = queue;
        this.httpConnector = httpConnector;
        this.logger = Logger.getLogger(FakeApiCommand.class.getName());
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public CompletableFuture<Optional<FakeApiEntity>> getUserAsync(int id, CancellationToken cancellationToken) {
        // Validación temprana de cancelación, similar a ThrowIfCancellationRequested en C#
        if (cancellationToken.isCancellationRequested()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        HttpClientBuilder httpClient = new HttpClientBuilder(httpConnector, logger);
        httpClient.http(FakeApiStarting.EXAMPLE_HOST_BASE)
                .endpoint("todos/" + id)
                .timeout(FakeApiStarting.TIMEOUT)
                .withMemoryQueue(queue, FakeApiStarting.GET_USER_OPERATION, FakeApiStarting.USER_KEYWORD);

        // Retorna CompletableFuture sin bloquear, permitiendo composición asíncrona.
        // Equivalente a retornar Task<Optional<T>> en C#.
        return httpClient.get(cancellationToken)
                .thenApply(result -> handleResponse(result, FakeApiStarting.GET_USER_OPERATION, content -> new FakeApiEntity(
                        intValue(content, "userId", 0),
                        intValue(content, "id", 0),
                        textValue(content, "title", "No Title"),
                        booleanValue(content, "completed", false))));
    }

    @Override
    public CompletableFuture<Optional<FakeApiEntity>> getTitleAsync(int id, CancellationToken cancellationToken) {
        if (cancellationToken.isCancellationRequested()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        HttpClientBuilder httpClient = new HttpClientBuilder(httpConnector, logger);
        httpClient.http(FakeApiStarting.EXAMPLE_TITLE_BASE)
                .endpoint("/api/v2/{myparam}")
                .params(Map.of("myparam", "addresses"))
                .queries(Map.of(
                        "_quantity", String.valueOf(id),
                        "_type", "JQ89uGoIjUBHtNPvqJT4dtBlsPgvo0QyxO+US/gIE2w="))
                .timeout(FakeApiStarting.TIMEOUT)
                .withMemoryQueue(queue, FakeApiStarting.GET_TITLE_OPERATION, FakeApiStarting.TITLE_KEYWORD);

        return httpClient.get(cancellationToken)
                .thenApply(result -> handleResponse(result, FakeApiStarting.GET_TITLE_OPERATION, content -> new FakeApiEntity(
                        0,
                        intValue(content, "code", 0),
                        textValue(content, "locale", "No Title"),
                        true)));
    }

    public Optional<FakeApiEntity> handleResponse(
            HttpResponseEntity result,
            String operation,
            Function<JsonNode, FakeApiEntity> mapper) {
        if (result.statusCode() == 500) {
            logWarning("Fake API responded with server error", operation, result);
            return Optional.empty();
        }

        if (result.statusCode() != 200 || result.body() == null || result.body().isBlank()) {
            logWarning("Fake API returned non-success or empty content", operation, result);
            return Optional.empty();
        }

        try {
            JsonNode content = objectMapper.readTree(result.body());
            return Optional.of(mapper.apply(content));
        } catch (JsonProcessingException exception) {
            logger.warning(String.format(
                    "Fake API returned invalid JSON. Operation=[%s] StatusCode=[%d] Url=[%s] Content=[%s] Error=[%s]",
                    operation,
                    result.statusCode(),
                    result.url(),
                    result.body(),
                    exception.getMessage()));
            return Optional.empty();
        }
    }

    public void logWarning(String message, String operation, HttpResponseEntity result) {
        logger.warning(String.format(
                "%s. Operation=[%s] StatusCode=[%d] Url=[%s] Content=[%s]",
                message,
                operation,
                result.statusCode(),
                result.url(),
                result.body()));
    }

    public int intValue(JsonNode content, String fieldName, int defaultValue) {
        JsonNode value = content.get(fieldName);
        return value == null || value.isNull() ? defaultValue : value.asInt(defaultValue);
    }

    public String textValue(JsonNode content, String fieldName, String defaultValue) {
        JsonNode value = content.get(fieldName);
        return value == null || value.isNull() ? defaultValue : value.asText(defaultValue);
    }

    public boolean booleanValue(JsonNode content, String fieldName, boolean defaultValue) {
        JsonNode value = content.get(fieldName);
        return value == null || value.isNull() ? defaultValue : value.asBoolean(defaultValue);
    }
}
