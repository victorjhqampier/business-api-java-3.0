package com.arify.application.internals.executors;

import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.adapters.ValidationResultAdapter;

import java.util.ArrayList;
import java.util.List;

public final class FluentValidationExecutor {

    private FluentValidationExecutor() {
    }

    public static List<ValidationResultAdapter> validate(TraceIdentifierAdapter traceIdentifier) {
        List<ValidationResultAdapter> errors = new ArrayList<>();
        validateField("deviceIdentifier", traceIdentifier.getDeviceIdentifier(), errors);
        validateField("messageIdentifier", traceIdentifier.getMessageIdentifier(), errors);
        validateField("channelIdentifier", traceIdentifier.getChannelIdentifier(), errors);
        return List.copyOf(errors);
    }

    private static void validateField(String fieldName, String value, List<ValidationResultAdapter> errors) {
        if (value == null || value.isBlank()) {
            errors.add(new ValidationResultAdapter("21002", "Field is required", fieldName));
            return;
        }

        if (value.length() < 5) {
            errors.add(new ValidationResultAdapter("21004", "Field must have at least 5 characters", fieldName));
        }

        if (value.length() > 42) {
            errors.add(new ValidationResultAdapter("21005", "Field must have at most 42 characters", fieldName));
        }
    }
}
