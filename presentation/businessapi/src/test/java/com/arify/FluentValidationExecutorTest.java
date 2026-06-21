package com.arify;

import com.arify.application.internals.adapters.TraceIdentifierAdapter;
import com.arify.application.internals.adapters.ValidationResultAdapter;
import com.arify.application.internals.executors.FluentValidationExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluentValidationExecutorTest {

    @Test
    void validTraceIdentifierReturnsNoErrors() {
        TraceIdentifierAdapter traceIdentifier = new TraceIdentifierAdapter("device-123", "message-123", "channel-123");

        List<ValidationResultAdapter> errors = FluentValidationExecutor.validate(traceIdentifier);

        assertTrue(errors.isEmpty());
    }

    @Test
    void blankFieldsReturnRequiredErrors() {
        TraceIdentifierAdapter traceIdentifier = new TraceIdentifierAdapter("", " ", null);

        List<ValidationResultAdapter> errors = FluentValidationExecutor.validate(traceIdentifier);

        assertEquals(5, errors.size());
        assertTrue(errors.stream().anyMatch(error -> "21002".equals(error.code())));
        assertTrue(errors.stream().anyMatch(error -> "21001".equals(error.code())));
        assertTrue(errors.stream().anyMatch(error -> "21004".equals(error.code())));
    }

    @Test
    void shortFieldsReturnMinLengthErrors() {
        TraceIdentifierAdapter traceIdentifier = new TraceIdentifierAdapter("dev", "msg", "chn");

        List<ValidationResultAdapter> errors = FluentValidationExecutor.validate(traceIdentifier);

        assertEquals(3, errors.size());
        assertTrue(errors.stream().allMatch(error -> "21004".equals(error.code())));
    }

    @Test
    void longFieldsReturnMaxLengthErrors() {
        String longValue = "x".repeat(43);
        TraceIdentifierAdapter traceIdentifier = new TraceIdentifierAdapter(longValue, longValue, longValue);

        List<ValidationResultAdapter> errors = FluentValidationExecutor.validate(traceIdentifier);

        assertEquals(3, errors.size());
        assertTrue(errors.stream().allMatch(error -> "21005".equals(error.code())));
    }
}
