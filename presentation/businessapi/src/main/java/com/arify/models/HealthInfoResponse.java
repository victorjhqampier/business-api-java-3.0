package com.arify.models;

public record HealthInfoResponse(
        String service,
        String status,
        String timestamp,
        String environment,
        String version) {
}
