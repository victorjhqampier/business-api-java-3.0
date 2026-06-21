package com.arify.application.internals.validators;

public record ArifyValidationRuleResponse(
        String fieldName,
        String errorCode,
        String message) {
}
