package com.arify.controllers.nonbianexample;

import com.arify.application.adapters.CreateExampleAdapter;
import com.arify.application.adapters.ExampleRequestAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.executors.EasyResult;
import com.arify.application.ports.ExamplePort;
import com.arify.domain.commons.CancellationReason;
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

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/business-api-b/v1/no-bian")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class NoBianExampleController {
    public static final String OPERATION_NAME = "obtener-cliente";
    // Equivalente a CancellationTokenSource con timeout de 9s en C# .NET.
    // Este timeout se alinea con el estándar del proyecto C#.
    public static final Duration HTTP_TIMEOUT = Duration.ofSeconds(9);
    public static final int CLIENT_CLOSED_STATUS = 499;
    public static final int REQUEST_TIMEOUT_STATUS = 408;

    public final Logger logger = Logger.getLogger(NoBianExampleController.class.getName());

    @Inject
    ExamplePort exampleUseCase;

    @Inject
    MicroserviceCallMemoryQueue memoryQueue;

    // Equivalente a un endpoint [HttpPost] en C# .NET con [FromHeader] y [FromBody].
    // @RunOnVirtualThread permite que el .join() sea no bloqueante para platform threads,
    // similar al comportamiento de async/await en C#.
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
        // Equivalente a construir TraceIdentifierAdapter desde headers en C# .NET.
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

        // Equivalente a CancellationTokenSource en C# .NET con timeout de 9s.
        // Se crea en presentación porque aquí nace el request HTTP.
        CancellationToken cancellationToken = CancellationToken.withTimeout(HTTP_TIMEOUT);

        // Equivalente al bloque try en C# ExampleController.Retrieve.
        // Manejo centralizado de excepciones de ciclo de vida del request.
        try {
            // Como este endpoint usa @RunOnVirtualThread, hacer join() aquí es aceptable,
            // porque no bloquea un platform thread tradicional.
            // Equivalente a: var result = await _exampleUsecase.ShowExampleAsync(headers, linkedCts.Token);
            EasyResult<CreateExampleAdapter> result = exampleUseCase
                    .getDataAsync(trace, body, cancellationToken)
                    .join();

            // Equivalente a registrar auditoría/traza después de la ejecución del caso de uso.
            traceHandler.pushSuccess(
                    uriInfo.getRequestUri().toString(),
                    "POST",
                    body,
                    result,
                    result.status()
            );

            // Equivalente a la lógica de manejo de EasyResult en C# .NET.
            // Orden: validación (422) -> no content (204) -> éxito (200)

            // Si hay errores de validación, retornar 422 con los errores.
            // Equivalente a: if (!result.IsSuccess) return StatusCode(result.Status, WarningResponse(...))
            if (!result.isSuccess()) {
                logger.warning("Validation failed");
                return EasyResponseHelper.warningResponse(
                        result.validationValues(),
                        result.status()
                );
            }

            // Si el status es 204, retornar NoContent.
            // Equivalente a: if (result.Status == 204) return NoContent();
            if (result.status() == 204) {
                logger.warning("No content found");
                return Response.status(204).build();
            }

            // Si llegamos aquí, es éxito.
            // Equivalente a: return Ok(SuccessResponse(result.SuccessValue!));
            return EasyResponseHelper.successResponse(result.successValue());

        } catch (CompletionException completionException) {
            // Desempaquetar la causa real de la excepción
            Throwable cause = completionException.getCause() != null
                    ? completionException.getCause()
                    : completionException;

            // Equivalente a: catch (OperationCanceledException) when (timeoutCts.IsCancellationRequested)
            // Captura TimeoutException que viene de .orTimeout() en el Use Case.
            if (cause instanceof TimeoutException) {
                cancellationToken.cancel(CancellationReason.TIMEOUT);
                logger.warning(String.format("Operation cancelled by timeout (%ds)", HTTP_TIMEOUT.getSeconds()));

                traceHandler.pushError(
                        uriInfo.getRequestUri().toString(),
                        "POST",
                        body,
                        REQUEST_TIMEOUT_STATUS,
                        "Request Timeout"
                );

                return Response.status(REQUEST_TIMEOUT_STATUS).build();
            }

            // Equivalente a: catch (OperationCanceledException) when (ct.IsCancellationRequested)
            // Captura CancellationException por cancelación del cliente.
            if (cause instanceof CancellationException) {
                // Verificar si fue timeout o cancelación del cliente usando el CancellationToken
                if (cancellationToken.cancellationReason().orElse(null) == CancellationReason.TIMEOUT) {
                    logger.warning(String.format("Operation cancelled by timeout (%ds)", HTTP_TIMEOUT.getSeconds()));

                    traceHandler.pushError(
                            uriInfo.getRequestUri().toString(),
                            "POST",
                            body,
                            REQUEST_TIMEOUT_STATUS,
                            "Request Timeout"
                    );

                    return Response.status(REQUEST_TIMEOUT_STATUS).build();
                } else {
                    logger.warning("Operation cancelled by client");

                    traceHandler.pushError(
                            uriInfo.getRequestUri().toString(),
                            "POST",
                            body,
                            CLIENT_CLOSED_STATUS,
                            "Client Closed Request"
                    );

                    return Response.status(CLIENT_CLOSED_STATUS).build();
                }
            }

            // Para cualquier otra excepción dentro del CompletionException, tratarla como error 500
            // Equivalente a: catch (Exception ex) en C# .NET
            logger.log(Level.SEVERE, cause.getMessage(), cause);
            traceHandler.pushError(
                    uriInfo.getRequestUri().toString(),
                    "POST",
                    body,
                    500,
                    cause.getMessage()
            );

            return EasyResponseHelper.errorResponse(
                    "500",
                    "Internal Server Error",
                    500
            );

        } catch (CancellationException cancellationException) {
            // Captura directa de CancellationException (sin CompletionException wrapper)
            // Equivalente a: catch (OperationCanceledException) en C# .NET
            if (cancellationToken.cancellationReason().orElse(null) == CancellationReason.TIMEOUT) {
                logger.warning(String.format("Operation cancelled by timeout (%ds)", HTTP_TIMEOUT.getSeconds()));

                traceHandler.pushError(
                        uriInfo.getRequestUri().toString(),
                        "POST",
                        body,
                        REQUEST_TIMEOUT_STATUS,
                        "Request Timeout"
                );

                return Response.status(REQUEST_TIMEOUT_STATUS).build();
            } else {
                logger.warning("Operation cancelled by client");

                traceHandler.pushError(
                        uriInfo.getRequestUri().toString(),
                        "POST",
                        body,
                        CLIENT_CLOSED_STATUS,
                        "Client Closed Request"
                );

                return Response.status(CLIENT_CLOSED_STATUS).build();
            }

        } catch (Exception exception) {
            // Captura genérica para cualquier otra excepción no manejada.
            // Equivalente a: catch (Exception ex) en C# .NET
            logger.log(Level.SEVERE, exception.getMessage(), exception);
            traceHandler.pushError(
                    uriInfo.getRequestUri().toString(),
                    "POST",
                    body,
                    500,
                    exception.getMessage()
            );

            return EasyResponseHelper.errorResponse(
                    "500",
                    "Internal Server Error",
                    500
            );
        }
    }
}