package com.arify.httpclientbuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class InfrastructureLogger {
    private InfrastructureLogger() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void logMemoryEvent(
            Logger logger,
            String operationName,
            String keyword,
            String requestVerb,
            String requestUrl,
            String requestHeader,
            String requestBody,
            String responseBody,
            String responseStatusCode,
            String stacktrace) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("request_verb", requestVerb);
        payload.put("request_url", requestUrl);
        payload.put("request_header", requestHeader);
        payload.put("request_body", requestBody);
        payload.put("response_body", responseBody);
        payload.put("response_statuscode", responseStatusCode);

        Map<String, Object> source = new HashMap<>();
        source.put("library", "http-client-builder");
        source.put("caller", logger.getName());
        source.put("operation", operationName);
        source.put("keyword", keyword);

        Map<String, Object> log = new HashMap<>();
        log.put("timestamp", OffsetDateTime.now().toString());
        log.put("log_level", "ERROR");
        log.put("trace_id", UUID.randomUUID().toString());
        log.put("message", "Memory event detected");
        log.put("source", source);
        log.put("payload", payload);
        log.put("stacktrace", stacktrace);

        try {
            logger.severe(HttpClientConnector.getObjectMapper().writeValueAsString(log));
        } catch (JsonProcessingException exception) {
            logger.severe("{\"timestamp\":\"" + OffsetDateTime.now() + "\",\"log_level\":\"ERROR\",\"trace_id\":\""
                    + UUID.randomUUID() + "\",\"message\":\"Memory event detected\",\"payload\":null,\"stacktrace\":\""
                    + exception.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }
}
