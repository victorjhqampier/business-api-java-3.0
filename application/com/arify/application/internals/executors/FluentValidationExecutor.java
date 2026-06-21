package com.arify.application.internals.executors;

import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.adapters.TraceIdentifierAdapterValidator;
import com.arify.application.internals.adapters.ValidationResultAdapter;
import com.arify.application.internals.validators.ArifyValidationRuleResponse;
import com.arify.application.internals.validators.ArifyValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class FluentValidationExecutor {

    private FluentValidationExecutor() {
    }

    public static <T> List<ValidationResultAdapter> execute(T inputObj, Function<T, ArifyValidator> validatorFactory) {
        try {
            ArifyValidator validator = validatorFactory.apply(inputObj);
            List<ArifyValidationRuleResponse> errors = validator.validate();

            List<ValidationResultAdapter> result = new ArrayList<>();
            for (ArifyValidationRuleResponse error : errors) {
                String code = sanitize(error.errorCode(), "VALIDATION_ERROR");
                String message = sanitize(error.message(), "Invalid value");
                String field = sanitize(error.fieldName(), null);

                result.add(new ValidationResultAdapter(code, message, field));
            }

            return List.copyOf(result);
        } catch (Exception exception) {
            return List.of(new ValidationResultAdapter(
                    "VALIDATION_EXECUTOR_ERROR",
                    "Error during validation: " + exception.getMessage(),
                    null));
        }
    }

    public static List<ValidationResultAdapter> validate(TraceIdentifierAdapter traceIdentifier) {
        return execute(traceIdentifier, TraceIdentifierAdapterValidator::new);
    }

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
