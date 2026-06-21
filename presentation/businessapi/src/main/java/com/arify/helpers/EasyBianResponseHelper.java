package com.arify.helpers;

import com.arify.application.internals.adapters.ValidationResultAdapter;
import com.arify.models.internals.BianErrorInternalModel;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/**
 * Helper para construir respuestas BIAN-compliant con nombres de campo dinámicos.
 * Equivalente a SystemAPI/Helpers/EasyBianResponseHelper.cs
 * 
 * Diferencia con EasyResponseHelper:
 * - EasyResponseHelper usa NoBianResponseModel con campo fijo "response"
 * - EasyBianResponseHelper usa nombres de campo dinámicos derivados del tipo (ej: "CreateExampleResponse")
 * 
 * Ejemplo de output JSON:
 * Success: { "CreateExampleResponse": { "name": "...", "age": 0 } }
 * Error:   { "errors": [ { "Status_code": "1099", "Message": "..." } ] }
 */
public final class EasyBianResponseHelper {
    private static final String ERRORS_KEY = "errors";
    private static final String DEFAULT_ERROR_CODE = "1099";
    private static final String DEFAULT_ERROR_MESSAGE = "No es un problema de tu lado. Estamos experimentando dificultades técnicas";
    private static final String GENERAL_FIELD = "General";
    private static final String IN_SEPARATOR = " in ";

    private EasyBianResponseHelper() {
    }

    /**
     * Respuesta exitosa con campo dinámico basado en el tipo de dato.
     * 
     * @param data Objeto de respuesta (típicamente un adapter de application)
     * @return Response 200 con entity BIAN { "XxxResponse": data }
     */
    public static Response successResponse(Object data) {
        String fieldName = BianResponseNameHelper.getResponseFieldNameFromObject(data);
        Map<String, Object> entity = Map.of(fieldName, data);
        return Response.ok(entity).build();
    }

    /**
     * Respuesta de validación con errores BIAN.
     * 
     * @param validationErrors Lista de errores de validación
     * @param statusCode Código HTTP (típicamente 422 para validación)
     * @return Response con statusCode y entity { "errors": [ {...} ] }
     */
    public static Response warningResponse(List<ValidationResultAdapter> validationErrors, int statusCode) {
        BianErrorInternalModel[] errors = new BianErrorInternalModel[validationErrors.size()];
        int index = 0;
        
        for (ValidationResultAdapter error : validationErrors) {
            String fieldName = (error.field() == null || error.field().isEmpty()) 
                    ? GENERAL_FIELD 
                    : error.field();
            
            // Formato BIAN: concatena mensaje con campo usando " in "
            errors[index++] = new BianErrorInternalModel(
                    error.code(),
                    error.message() + IN_SEPARATOR + fieldName
            );
        }
        
        Map<String, Object> entity = Map.of(ERRORS_KEY, errors);
        return Response.status(statusCode).entity(entity).build();
    }

    /**
     * Respuesta de error genérica.
     * 
     * @param errorCode Código de error BIAN (no HTTP status)
     * @param message Mensaje de error
     * @return Response 500 con entity { "errors": [ {...} ] }
     */
    public static Response errorResponse(String errorCode, String message) {
        BianErrorInternalModel[] errors = new BianErrorInternalModel[]{
                new BianErrorInternalModel(errorCode, message)
        };
        
        Map<String, Object> entity = Map.of(ERRORS_KEY, errors);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(entity).build();
    }

    /**
     * Respuesta de error con valores por defecto.
     * 
     * @return Response 500 con error BIAN genérico
     */
    public static Response errorResponse() {
        return errorResponse(DEFAULT_ERROR_CODE, DEFAULT_ERROR_MESSAGE);
    }

    /**
     * Respuesta sin contenido (204 No Content o 408 Request Timeout).
     * 
     * @param statusCode Código HTTP
     * @return Response con statusCode y sin body
     */
    public static Response noContent(int statusCode) {
        return Response.status(statusCode).build();
    }

    // ========== Aliases para compatibilidad con API C# ==========

    /**
     * Alias de successResponse. Mantiene compatibilidad con API C# EasySuccessRespond.
     */
    public static Response easySuccessRespond(Object data) {
        return successResponse(data);
    }

    /**
     * Alias de warningResponse. Mantiene compatibilidad con API C# EasyValidationErrorRespond.
     */
    public static Response easyValidationErrorRespond(List<ValidationResultAdapter> validationErrors, int statusCode) {
        return warningResponse(validationErrors, statusCode);
    }

    /**
     * Alias de errorResponse. Mantiene compatibilidad con API C# EasyErrorRespond.
     */
    public static Response easyErrorRespond(String errorCode, String message) {
        return errorResponse(errorCode, message);
    }

    /**
     * Alias de errorResponse. Mantiene compatibilidad con API C# EasyErrorRespond.
     */
    public static Response easyErrorRespond() {
        return errorResponse();
    }
}
