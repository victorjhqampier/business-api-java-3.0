package com.arify.controllers.nonbianexample;

import com.arify.application.adapters.CreateExampleAdapter;
import com.arify.application.adapters.ExampleRequestAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.executors.EasyResult;
import com.arify.application.ports.ExamplePort;
import com.arify.domain.commons.CancellationToken;
import com.arify.domain.containers.memoryevents.MicroserviceCallMemoryQueue;
import com.arify.handlers.MicroserviceTraceHandler;
import com.arify.helpers.EasyResponseHelper;
import io.smallrye.common.annotation.RunOnVirtualThread;
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
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/business-api-b/v1/no-bian")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class NoBianExampleController {
    public static final String OPERATION_NAME = "obtener-cliente";

    public final Logger logger = Logger.getLogger(NoBianExampleController.class.getName());

    @Inject
    ExamplePort exampleUseCase;

    @Inject
    MicroserviceCallMemoryQueue memoryQueue;

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
        CancellationToken cancellationToken = CancellationToken.withDefault();
        TraceIdentifierAdapter trace = new TraceIdentifierAdapter(deviceIdentifier, messageIdentifier, channelIdentifier);
        MicroserviceTraceHandler traceHandler = new MicroserviceTraceHandler(
                memoryQueue, OPERATION_NAME, customerId, messageIdentifier, channelIdentifier, deviceIdentifier);
        String requestUrl = uriInfo.getRequestUri().toString();

        try {
            EasyResult<CreateExampleAdapter> result = exampleUseCase.getDataAsync(cancellationToken, trace, body);
            traceHandler.pushSuccess(requestUrl, "POST", body, result, result.status());
            Thread.sleep(5000);
            if (result.status() == 204) {
                return Response.status(204).build();
            }

            if (result.status() == 408) {
                return Response.status(408).build();
            }

            if (!result.isSuccess()) {
                logger.warning("Validation failed");
                return EasyResponseHelper.warningResponse(result.validationValues(), result.status());
            }

            return EasyResponseHelper.successResponse(result.successValue());
        } catch (Exception exception) {
            logger.log(Level.SEVERE, exception.getMessage(), exception);
            traceHandler.pushError(requestUrl, "POST", body, 500, exception.getMessage());
            return EasyResponseHelper.errorResponse("99", "No es de tu lado, es nuestro error", 500);
        }
    }
}
