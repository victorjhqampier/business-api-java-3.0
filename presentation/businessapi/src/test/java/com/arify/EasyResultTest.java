package com.arify;

import com.arify.application.internals.adapters.ValidationResultAdapter;
import com.arify.application.internals.executors.EasyResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasyResultTest {

    @Test
    void successContainsValueAndNoErrors() {
        EasyResult<String> result = EasyResult.success("ok");

        assertTrue(result.isSuccess());
        assertEquals("ok", result.getValue());
        assertNull(result.getErrorCode());
        assertTrue(result.getValidationErrors().isEmpty());
    }

    @Test
    void failureContainsErrorCodeAndValidationErrors() {
        List<ValidationResultAdapter> errors = List.of(
                new ValidationResultAdapter("21002", "Field is required", "deviceIdentifier"));

        EasyResult<String> result = EasyResult.failure("VALIDATION_FAILED", errors);

        assertFalse(result.isSuccess());
        assertNull(result.getValue());
        assertEquals("VALIDATION_FAILED", result.getErrorCode());
        assertEquals(errors, result.getValidationErrors());
    }

    @Test
    void validationErrorsAreImmutable() {
        EasyResult<String> result = EasyResult.failure(
                "VALIDATION_FAILED",
                List.of(new ValidationResultAdapter("21002", "Field is required", "channelIdentifier")));

        assertThrows(UnsupportedOperationException.class,
                () -> result.getValidationErrors().add(new ValidationResultAdapter("X", "Y", "Z")));
    }

    @Test
    void emptyIsSuccessfulWithoutValue() {
        EasyResult<String> result = EasyResult.empty();

        assertTrue(result.isSuccess());
        assertNull(result.getValue());
        assertNull(result.getErrorCode());
        assertTrue(result.getValidationErrors().isEmpty());
    }
}
