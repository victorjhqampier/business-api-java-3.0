package com.arify.handlers;

import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.domain.entities.MicroserviceCallTraceEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.logging.Logger;

public class MicroserviceTraceHandler {
    public static final int MAX_REQUEST_PAYLOAD_LENGTH = 1000;
    public static final int MAX_RESPONSE_PAYLOAD_LENGTH = 2000;

    public final MicroserviceCallMemoryQueue queue;
    public final String operationName;
    public final String keyword;
    public final String messageIdentifier;
    public final String channelIdentifier;
    public final String deviceIdentifier;
    public final ObjectMapper objectMapper;
    public final Logger logger;

    public MicroserviceTraceHandler(
            MicroserviceCallMemoryQueue queue,
            String operationName,
            String keyword,
            String messageIdentifier,
            String channelIdentifier,
            String deviceIdentifier) {
        this.queue = queue;
        this.operationName = operationName;
        this.keyword = keyword;
        this.messageIdentifier = messageIdentifier == null ? "" : messageIdentifier;
        this.channelIdentifier = channelIdentifier == null ? "" : channelIdentifier;
        this.deviceIdentifier = deviceIdentifier == null ? "" : deviceIdentifier;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.logger = Logger.getLogger(MicroserviceTraceHandler.class.getName());
    }

    public void pushSuccess(String requestUrl, String method, Object requestPayload, Object responsePayload, int statusCode) {
        pushTrace(requestUrl, method, requestPayload, responsePayload, statusCode);
    }

    public void pushError(String requestUrl, String method, Object requestPayload, int statusCode, String errorMessage) {
        pushTrace(requestUrl, method, requestPayload, errorMessage, statusCode);
    }

    public void pushTrace(String requestUrl, String method, Object requestPayload, Object responsePayload, int statusCode) {
        MicroserviceCallTraceEntity traceEntity = new MicroserviceCallTraceEntity(
                UUID.randomUUID().toString(),
                messageIdentifier,
                channelIdentifier,
                deviceIdentifier,
                keyword,
                method,
                "BusinessAPI2.0",
                operationName,
                requestUrl,
                serializePayload(requestPayload, MAX_REQUEST_PAYLOAD_LENGTH),
                OffsetDateTime.now(),
                statusCode,
                serializePayload(responsePayload, MAX_RESPONSE_PAYLOAD_LENGTH),
                OffsetDateTime.now());

        if (!queue.tryPush(traceEntity)) {
            logger.warning("Microservice trace queue is full");
        }
    }

    public String serializePayload(Object payload, int maxLength) {
        if (payload == null) {
            return null;
        }

        String serialized;
        try {
            serialized = payload instanceof String text ? text : objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            serialized = payload.toString();
        }

        return serialized.length() <= maxLength ? serialized : serialized.substring(0, maxLength) + "...[truncated]";
    }
}
