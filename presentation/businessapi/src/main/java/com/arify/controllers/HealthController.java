package com.arify.controllers;
import com.arify.models.HealthChecks;
import com.arify.models.HealthInfoResponse;
import com.arify.models.HealthStateResponse;
import com.arify.models.PingResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.lang.management.ManagementFactory;
import java.time.OffsetDateTime;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class HealthController {
    @GET
    public HealthInfoResponse info() {
        return new HealthInfoResponse(
            "businessapi",
            "Service Running",
            OffsetDateTime.now().toString(),
            resolveEnvironment(),
            resolveVersion()
        );
    }

    @GET
    @Path("/ping")
    public PingResponse ping() {
        return new PingResponse("pong", OffsetDateTime.now().toString());
    }

    @GET
    @Path("/health")
    public HealthStateResponse health() {
        return new HealthStateResponse(
            "UP",
            new HealthChecks("UP", "UP"),
            OffsetDateTime.now().toString(),
            ManagementFactory.getRuntimeMXBean().getUptime(),
            resolveEnvironment(),
            resolveVersion(),
            null
        );
    }

    @GET
    @Path("/health/live")
    public HealthStateResponse live() {
        return new HealthStateResponse(
            "UP",
            null,
            OffsetDateTime.now().toString(),
            null,
            resolveEnvironment(),
            resolveVersion(),
            "liveness"
        );
    }

    @GET
    @Path("/health/ready")
    public HealthStateResponse ready() {
        return new HealthStateResponse(
            "UP",
            null,
            OffsetDateTime.now().toString(),
            null,
            resolveEnvironment(),
            resolveVersion(),
            "readiness"
        );
    }

    private String resolveEnvironment() {
        String profile = System.getenv("QUARKUS_PROFILE");
        return (profile == null || profile.isBlank()) ? "dev" : profile;
    }

    private String resolveVersion() {
        Package appPackage = getClass().getPackage();
        String version = appPackage != null ? appPackage.getImplementationVersion() : null;
        return (version == null || version.isBlank()) ? "1.0.0-SNAPSHOT" : version;
    }
}
