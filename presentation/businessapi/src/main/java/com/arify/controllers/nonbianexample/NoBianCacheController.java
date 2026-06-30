package com.arify.controllers.nonbianexample;

import com.arify.application.adapters.RetrieveExampleAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.executors.EasyResult;
import com.arify.application.ports.ExampleCachePort;
import com.arify.domain.commons.CancellationToken;
import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.handlers.MicroserviceTraceHandler;
import com.arify.helpers.EasyResponseHelper;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.resteasy.reactive.RestHeader;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/business-api-b/v1/no-bian")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class NoBianCacheController {
    private static final String OPERATION_NAME = "retrieve-cache";
    private static final String KEYWORD = "cache";
    private static final Duration RESPONSE_HTTP_TIMEOUT = Duration.ofSeconds(9);
    private static final Logger LOGGER = Logger.getLogger(NoBianCacheController.class.getName());

    @Inject
    ExampleCachePort exampleCacheUseCase;

    @Inject
    MicroserviceCallMemoryQueue memoryQueue;

    @GET
    @Path("/cache")
    @RunOnVirtualThread
    public Response retrieve(
            @RestHeader("x-device-identifier")
            @Parameter(description = "Cod Dispositivo", required = true)
            String deviceIdentifier,

            @RestHeader("x-message-identifier")
            @Parameter(description = "Cod Mensaje", required = true)
            String messageIdentifier,

            @RestHeader("x-channel-identifier")
            @Parameter(description = "Cod Canal", required = true)
            String channelIdentifier,

            @Context UriInfo uriInfo
    ) {
        CancellationToken cancellationToken = CancellationToken.withTimeout(RESPONSE_HTTP_TIMEOUT);

        TraceIdentifierAdapter trace = new TraceIdentifierAdapter(
                deviceIdentifier,
                messageIdentifier,
                channelIdentifier
        );

        MicroserviceTraceHandler traceHandler = new MicroserviceTraceHandler(
                memoryQueue,
                OPERATION_NAME,
                KEYWORD,
                messageIdentifier,
                channelIdentifier,
                deviceIdentifier
        );

        try {
            EasyResult<RetrieveExampleAdapter> result = exampleCacheUseCase.showExampleAsync(trace, cancellationToken);
            traceHandler.pushSuccess(uriInfo.getRequestUri().toString(), "GET", trace, result, result.status());

            if (!result.isSuccess()) {
                LOGGER.fine("Validation failed");
                return EasyResponseHelper.warningResponse(result.validationValues(), result.status());
            }

            if (result.status() == 204) {
                LOGGER.fine("No content found");
                return EasyResponseHelper.noContent(204);
            }

            return EasyResponseHelper.successResponse(result.successValue());

        } catch (CancellationException cancellationException) {
            LOGGER.fine("Operation cancelled by client");
            traceHandler.pushError(uriInfo.getRequestUri().toString(), "GET", trace, 499, "Client Closed Request");
            return EasyResponseHelper.noContent(499);

        } catch (CompletionException completionException) {
            LOGGER.severe(String.format("Operation cancelled by timeout (%ds)", RESPONSE_HTTP_TIMEOUT.getSeconds()));
            traceHandler.pushError(uriInfo.getRequestUri().toString(), "GET", trace, 408, "Request Timeout");
            return EasyResponseHelper.noContent(408);

        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, exception.getMessage(), exception);
            traceHandler.pushError(uriInfo.getRequestUri().toString(), "GET", trace, 500, exception.getMessage());
            return EasyResponseHelper.errorResponse("500", "Internal Server Error");
        }
    }
}
