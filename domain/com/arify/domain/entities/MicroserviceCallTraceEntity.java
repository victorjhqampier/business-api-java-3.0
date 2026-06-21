package com.arify.domain.entities;

import java.time.OffsetDateTime;

public record MicroserviceCallTraceEntity(
        String identity,
        String traceId,
        String channelId,
        String deviceId,
        String keyword,
        String method,
        String microserviceName,
        String operationName,
        String requestUrl,
        String requestPayload,
        OffsetDateTime requestDatetime,
        int responseStatusCode,
        String responsePayload,
        OffsetDateTime responseDatetime) {

    public MicroserviceCallTraceEntity {
        microserviceName = microserviceName == null ? "BusinessAPI2.0" : microserviceName;
        requestDatetime = requestDatetime == null ? OffsetDateTime.now() : requestDatetime;
        responseDatetime = responseDatetime == null ? OffsetDateTime.now() : responseDatetime;
    }
}
