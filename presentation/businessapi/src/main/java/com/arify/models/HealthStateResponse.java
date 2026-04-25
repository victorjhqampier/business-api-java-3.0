package com.arify.models;

public record HealthStateResponse(
        String status,
        HealthChecks checks,
        String timestamp,
        Long uptimeMs,
        String environment,
        String version,
        String type) {
}
