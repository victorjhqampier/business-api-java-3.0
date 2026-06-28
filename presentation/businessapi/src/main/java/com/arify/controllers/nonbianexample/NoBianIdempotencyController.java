package com.arify.controllers.nonbianexample;

import com.arify.application.adapters.ExamplePreRequestAdapter;
import com.arify.application.adapters.RetrieveExampleAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.executors.EasyResult;
import com.arify.application.ports.ExampleIdempotencyPort;
import com.arify.domain.commons.CancellationToken;
import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.handlers.MicroserviceTraceHandler;
import com.arify.helpers.EasyResponseHelper;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
public class NoBianIdempotencyController {
    private static final String RETRIEVE_OPERATION_NAME = "retrieve-idempotency";
    private static final String EXECUTE_OPERATION_NAME = "execute-idempotency";
    private static final String KEYWORD = "idempotency";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(9);
    private static final Logger LOGGER = Logger.getLogger(NoBianIdempotencyController.class.getName());

    @Inject
    ExampleIdempotencyPort exampleIdempotencyUseCase;

    @Inject
    MicroserviceCallMemoryQueue memoryQueue;

    @GET
    @Path("/idempotency")
    @RunOnVirtualThread
    public Response retrieve(
            @RestHeader("device-identifier")
            @Parameter(description = "Cod Dispositivo", required = true)
            String deviceIdentifier,

            @RestHeader("message-identifier")
            @Parameter(description = "Cod Mensaje", required = true)
            String messageIdentifier,

            @RestHeader("channel-identifier")
            @Parameter(description = "Cod Canal", required = true)
            String channelIdentifier,

            @Context UriInfo uriInfo
    ) {
        TraceIdentifierAdapter trace = new TraceIdentifierAdapter(
                deviceIdentifier,
                messageIdentifier,
                channelIdentifier
        );

        MicroserviceTraceHandler traceHandler = newTraceHandler(
                RETRIEVE_OPERATION_NAME,
                messageIdentifier,
                channelIdentifier,
                deviceIdentifier
        );

        CancellationToken cancellationToken = CancellationToken.withTimeout(HTTP_TIMEOUT);

        try {
            EasyResult<RetrieveExampleAdapter> result = exampleIdempotencyUseCase.getDataAsync(trace, cancellationToken).join();
            traceHandler.pushSuccess(uriInfo.getRequestUri().toString(), "GET", trace, result, result.status());

            if (result.status() == 204) {
                LOGGER.fine("No content found");
                return EasyResponseHelper.noContent(204);
            }

            if (!result.isSuccess()) {
                LOGGER.fine("Validation failed");
                return EasyResponseHelper.warningResponse(result.validationValues(), result.status());
            }

            return EasyResponseHelper.successResponse(result.successValue());

        } catch (CancellationException cancellationException) {
            LOGGER.fine("Operation cancelled by client");
            traceHandler.pushError(uriInfo.getRequestUri().toString(), "GET", trace, 499, "Client Closed Request");
            return EasyResponseHelper.noContent(499);

        } catch (CompletionException completionException) {
            LOGGER.severe(String.format("Operation cancelled by timeout (%ds)", HTTP_TIMEOUT.getSeconds()));
            traceHandler.pushError(uriInfo.getRequestUri().toString(), "GET", trace, 408, "Request Timeout");
            return EasyResponseHelper.noContent(408);

        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, exception.getMessage(), exception);
            traceHandler.pushError(uriInfo.getRequestUri().toString(), "GET", trace, 500, exception.getMessage());
            return EasyResponseHelper.errorResponse("500", "Internal Server Error");
        }
    }

    @POST
    @Path("/idempotency")
    @RunOnVirtualThread
    public Response execute(
            ExamplePreRequestAdapter body,

            @RestHeader("device-identifier")
            @Parameter(description = "Cod Dispositivo", required = true)
            String deviceIdentifier,

            @RestHeader("message-identifier")
            @Parameter(description = "Cod Mensaje", required = true)
            String messageIdentifier,

            @RestHeader("channel-identifier")
            @Parameter(description = "Cod Canal", required = true)
            String channelIdentifier,

            @Context UriInfo uriInfo
    ) {
        TraceIdentifierAdapter trace = new TraceIdentifierAdapter(
                deviceIdentifier,
                messageIdentifier,
                channelIdentifier
        );

        MicroserviceTraceHandler traceHandler = newTraceHandler(
                EXECUTE_OPERATION_NAME,
                messageIdentifier,
                channelIdentifier,
                deviceIdentifier
        );

        CancellationToken cancellationToken = CancellationToken.withTimeout(HTTP_TIMEOUT);

        try {
            EasyResult<RetrieveExampleAdapter> result = exampleIdempotencyUseCase.setIdempotencyAsync(trace, body, cancellationToken).join();
            traceHandler.pushSuccess(uriInfo.getRequestUri().toString(), "POST", body, result, result.status());

            if (result.status() == 204) {
                LOGGER.fine("No content found");
                return EasyResponseHelper.noContent(204);
            }

            if (!result.isSuccess()) {
                LOGGER.fine("Validation failed");
                return EasyResponseHelper.warningResponse(result.validationValues(), result.status());
            }

            return EasyResponseHelper.successResponse(result.successValue());

        } catch (CancellationException cancellationException) {
            LOGGER.fine("Operation cancelled by client");
            traceHandler.pushError(uriInfo.getRequestUri().toString(), "POST", body, 499, "Client Closed Request");
            return EasyResponseHelper.noContent(499);

        } catch (CompletionException completionException) {
            LOGGER.severe(String.format("Operation cancelled by timeout (%ds)", HTTP_TIMEOUT.getSeconds()));
            traceHandler.pushError(uriInfo.getRequestUri().toString(), "POST", body, 408, "Request Timeout");
            return EasyResponseHelper.noContent(408);

        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, exception.getMessage(), exception);
            traceHandler.pushError(uriInfo.getRequestUri().toString(), "POST", body, 500, exception.getMessage());
            return EasyResponseHelper.errorResponse("500", "Internal Server Error");
        }
    }

    private MicroserviceTraceHandler newTraceHandler(
            String operationName,
            String messageIdentifier,
            String channelIdentifier,
            String deviceIdentifier) {
        return new MicroserviceTraceHandler(
                memoryQueue,
                operationName,
                KEYWORD,
                messageIdentifier,
                channelIdentifier,
                deviceIdentifier
        );
    }
}
