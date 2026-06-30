package com.arify.fakeapiinfra.queries;

import com.arify.domain.commons.CancellationToken;
import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.domain.entities.FakeApiEntity;
import com.arify.domain.interfaces.IFakeApiInfrastructure;
import com.arify.fakeapiinfra.FakeApiStarting;
import com.arify.httpclientbuilder.HttpClientBuilder;
import com.arify.httpclientbuilder.HttpClientConnector;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

// Implementa la interfaz de dominio IFakeApiInfrastructure.
// Retorna CompletableFuture<Optional<T>> para permitir composición asíncrona,
// equivalente a Task<T> en C# .NET.
public class FakeApiCommand implements IFakeApiInfrastructure {
    private static final Logger _logger = Logger.getLogger(FakeApiCommand.class.getName());

    private final MicroserviceCallMemoryQueue _queue;
    private final HttpClientConnector _httpConnector;

    public FakeApiCommand(MicroserviceCallMemoryQueue queue, HttpClientConnector httpConnector) {
        this._queue = queue;
        this._httpConnector = httpConnector;
    }

    @Override
    public CompletableFuture<Optional<FakeApiEntity>> getUserAsync(int id, CancellationToken ctx) {
        if (ctx.isCancellationRequested()) {return CompletableFuture.completedFuture(Optional.empty());}

        var httpClient = new HttpClientBuilder(_httpConnector, _logger);
        httpClient.http(FakeApiStarting.EXAMPLE_HOST_BASE)
                .endpoint("todos/" + id)
                .timeout(FakeApiStarting.TIMEOUT)
                .withMemoryQueue(_queue, FakeApiStarting.GET_USER_OPERATION, FakeApiStarting.USER_KEYWORD);

        return httpClient.get(ctx).thenApply(response -> {
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            var body = response.body();
            if (body == null || body.isNull() || body.isMissingNode()) {
                return Optional.empty();
            }
            return Optional.of(new FakeApiEntity(
                body.path("userId").asInt(0),
                body.path("id").asInt(0),
                body.path("title").asText(""),
                body.path("completed").asBoolean(false)
            ));
        });
    }

    @Override
    public CompletableFuture<Optional<FakeApiEntity>> getTitleAsync(int id, CancellationToken ctx) {
        if (ctx.isCancellationRequested()) {return CompletableFuture.completedFuture(Optional.empty());}

        var httpClient = new HttpClientBuilder(_httpConnector, _logger);
        httpClient.http(FakeApiStarting.EXAMPLE_TITLE_BASE)
                .endpoint("/api/v2/{myparam}")
                .params(Map.of("myparam", "addresses"))
                .queries(Map.of(
                        "_quantity", String.valueOf(id),
                        "_type", "JQ89uGoIjUBHtNPvqJT4dtBlsPgvo0QyxO+US/gIE2w="))
                .timeout(FakeApiStarting.TIMEOUT)
                .withMemoryQueue(_queue, FakeApiStarting.GET_TITLE_OPERATION, FakeApiStarting.TITLE_KEYWORD);

        return httpClient.get(ctx).thenApply(response -> {
                    if (response.statusCode() != 200) {
                        return Optional.empty();
                    }
                    // El body ya viene parseado como JsonNode desde el connector
                    var json = response.body();
                    int code = json.path("code").asInt(0);
                    String locale = "";

                    var dataNode = json.path("data");
                    if (dataNode.isArray() && !dataNode.isEmpty()) {
                        var firstElement = dataNode.get(0);
                        locale = firstElement.path("locale").asText("");
                        
                        if (locale.isEmpty()) {
                            locale = firstElement.path("country").asText("");
                        }
                        if (locale.isEmpty()) {
                            locale = firstElement.path("countryCode").asText("");
                        }
                    }

                    return Optional.of(new FakeApiEntity(0, code, locale, true));
                });
    }
}
