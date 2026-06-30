package com.arify.controllers.bianexample;

import com.arify.application.adapters.CreateExampleAdapter;
import com.arify.application.adapters.ExampleRequestAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.executors.EasyResult;
import com.arify.application.ports.ExamplePort;
import com.arify.domain.commons.CancellationToken;
import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.handlers.MicroserviceTraceHandler;
import com.arify.helpers.EasyBianResponseHelper;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestPath;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/business-api-b/v1/bian")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class BianExampleController {
    private static final String OPERATION_NAME = "obtener-cliente";
    // Equivalente a CancellationTokenSource con timeout de 9s en C# .NET.
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(9);
    private static final Logger LOGGER = Logger.getLogger(BianExampleController.class.getName());

    @Inject
    ExamplePort exampleUseCase;

    @Inject
    MicroserviceCallMemoryQueue memoryQueue;

    // @RunOnVirtualThread permite ejecutar flujos I/O-bound imperativos sin bloquear platform threads.
    @POST
    @Path("/{customer_id}/create")
    @RunOnVirtualThread
    public Response postDataAsync(
            @RestPath("customer_id")
            @Parameter(description = "Cod cliente", required = true)
            String customerId,

            ExampleRequestAdapter body,

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

        MicroserviceTraceHandler traceHandler = new MicroserviceTraceHandler(
                memoryQueue,
                OPERATION_NAME,
                customerId,
                messageIdentifier,
                channelIdentifier,
                deviceIdentifier
        );

        // Equivalente a CancellationTokenSource en C# .NET con timeout de 9s. Se crea en presentación porque aquí nace el request HTTP.
        CancellationToken cancellationToken = CancellationToken.withTimeout(HTTP_TIMEOUT);

        try {
            EasyResult<CreateExampleAdapter> result = exampleUseCase.getDataAsync(trace, body, cancellationToken);
            traceHandler.pushSuccess(uriInfo.getRequestUri().toString(), "POST", body, result, result.status());

            if (!result.isSuccess()) {
                LOGGER.fine("Validation failed");
                return EasyBianResponseHelper.warningResponse(result.validationValues(), result.status());
            }

            if (result.status() == 204) {
                LOGGER.fine("No content found");
                return EasyBianResponseHelper.noContent(204);
            }

            return EasyBianResponseHelper.successResponse(result.successValue());

        } catch (CompletionException completionException) {
            LOGGER.severe(String.format("Operation cancelled by timeout (%ds)", HTTP_TIMEOUT.getSeconds()));
            traceHandler.pushError(uriInfo.getRequestUri().toString(), "POST", body, 408, "Request Timeout");
            return EasyBianResponseHelper.noContent(408);

        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, exception.getMessage(), exception);
            traceHandler.pushError(uriInfo.getRequestUri().toString(),"POST", body, 500, exception.getMessage());
            return EasyBianResponseHelper.errorResponse("500",  "Internal Server Error");
        }
    }
}
