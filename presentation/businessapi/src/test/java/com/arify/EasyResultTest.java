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
        assertEquals(200, result.status());
        assertEquals("ok", result.successValue());
        assertTrue(result.validationValues().isEmpty());
    }

    @Test
    void failureContainsStatusAndValidationErrors() {
        List<ValidationResultAdapter> errors = List.of(
                new ValidationResultAdapter("21002", "Field is required", "deviceIdentifier"));

        EasyResult<String> result = EasyResult.failure(422, errors);

        assertFalse(result.isSuccess());
        assertEquals(422, result.status());
        assertNull(result.successValue());
        assertEquals(errors, result.validationValues());
    }

    @Test
    void validationErrorsAreImmutable() {
        EasyResult<String> result = EasyResult.failure(
                422,
                List.of(new ValidationResultAdapter("21002", "Field is required", "channelIdentifier")));

        assertThrows(UnsupportedOperationException.class,
                () -> result.validationValues().add(new ValidationResultAdapter("X", "Y", "Z")));
    }

    @Test
    void emptyIsSuccessfulWithoutValue() {
        EasyResult<String> result = EasyResult.empty();

        assertTrue(result.isSuccess());
        assertEquals(204, result.status());
        assertNull(result.successValue());
        assertTrue(result.validationValues().isEmpty());
    }
}
