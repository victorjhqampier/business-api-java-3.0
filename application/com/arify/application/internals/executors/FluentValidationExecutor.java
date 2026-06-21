package com.arify.application.internals.executors;

import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapterValidator;
import com.arify.application.internals.adapters.ValidationResultAdapter;
import com.arify.application.internals.validators.AbstractValidator;
import com.arify.application.internals.validators.ArifyValidationRuleResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Ejecutor de validaciones basado en el patrón Fluent Validation de C# .NET.
 * 
 * Coordina la ejecución de validadores que heredan de AbstractValidator<T>.
 * Convierte los errores de validación internos (ArifyValidationRuleResponse)
 * a DTOs de aplicación (ValidationResultAdapter).
 * 
 * Equivalente al comportamiento interno de FluentValidation en C# .NET.
 */
public final class FluentValidationExecutor {
    private static final TraceIdentifierAdapterValidator TRACE_IDENTIFIER_VALIDATOR = new TraceIdentifierAdapterValidator();

    private FluentValidationExecutor() {
        // Utility class - no se permite instanciación
    }

    /**
     * Ejecuta un validador contra una instancia y retorna los errores encontrados.
     * 
     * La diferencia con la versión anterior es que ahora el validador se instancia
     * una sola vez (no recibe el objeto en el constructor) y puede reutilizarse
     * para validar múltiples instancias.
     * 
     * Equivalente a validator.Validate(instance) en C# FluentValidation.
     * 
     * @param <T> Tipo del objeto a validar
     * @param inputObj Instancia del objeto a validar
     * @param validatorSupplier Función que crea una instancia del validador (ej. MyValidator::new)
     * @return Lista inmutable de errores de validación (vacía si no hay errores)
     */
    public static <T> List<ValidationResultAdapter> execute(T inputObj, Supplier<AbstractValidator<T>> validatorSupplier) {
        try {
            return execute(inputObj, validatorSupplier.get());
        } catch (Exception exception) {
            return validationExecutorError(exception);
        }
    }

    public static <T> List<ValidationResultAdapter> execute(T inputObj, AbstractValidator<T> validator) {
        try {
            List<ArifyValidationRuleResponse> errors = validator.validate(inputObj);

            List<ValidationResultAdapter> result = new ArrayList<>();
            for (ArifyValidationRuleResponse error : errors) {
                String code = sanitize(error.errorCode(), "VALIDATION_ERROR");
                String message = sanitize(error.message(), "Invalid value");
                String field = sanitize(error.fieldName(), null);

                result.add(new ValidationResultAdapter(code, message, field));
            }

            return List.copyOf(result);
        } catch (Exception exception) {
            return validationExecutorError(exception);
        }
    }

    private static List<ValidationResultAdapter> validationExecutorError(Exception exception) {
        return List.of(new ValidationResultAdapter(
                "VALIDATION_EXECUTOR_ERROR",
                "Error during validation: " + exception.getMessage(),
                null));
    }

    /**
     * Método de conveniencia para validar TraceIdentifierAdapter.
     * Mantiene compatibilidad con código existente.
     * 
     * @param traceIdentifier Objeto a validar
     * @return Lista de errores de validación
     */
    public static List<ValidationResultAdapter> validate(TraceIdentifierAdapter traceIdentifier) {
        return execute(traceIdentifier, TRACE_IDENTIFIER_VALIDATOR);
    }

    /**
     * Limpia y valida un string, retornando un fallback si está vacío o nulo.
     * 
     * @param value String a sanitizar
     * @param fallback Valor por defecto si value es nulo o vacío
     * @return String sanitizado o fallback
     */
    public static String sanitize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }

        return trimmed;
    }
}
