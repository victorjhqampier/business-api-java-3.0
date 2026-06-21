package com.arify.application.internals.adapters;

import com.arify.application.internals.validators.AbstractValidator;

public class TraceIdentifierAdapterValidator extends AbstractValidator<TraceIdentifierAdapter> {
    
    public TraceIdentifierAdapterValidator() {
        // Equivalente a: RuleFor(x => x.DeviceIdentifier) en C#
        ruleFor(TraceIdentifierAdapter::deviceIdentifier)
                .notNull().withErrorCode("21001").withMessage("Cannot be null")
                .notEmpty().withErrorCode("21002").withMessage("Cannot be empty")
                .minLength(5).withErrorCode("21004").withMessage("Allowed minimum length")
                .maxLength(42).withErrorCode("21005").withMessage("Allowed maximum length");

        ruleFor(TraceIdentifierAdapter::messageIdentifier)
                .notNull().withErrorCode("21001").withMessage("Cannot be null")
                .notEmpty().withErrorCode("21002").withMessage("Cannot be empty")
                .minLength(5).withErrorCode("21004").withMessage("Allowed minimum length")
                .maxLength(42).withErrorCode("21005").withMessage("Allowed maximum length");

        ruleFor(TraceIdentifierAdapter::channelIdentifier)
                .notNull().withErrorCode("21001").withMessage("Cannot be null")
                .notEmpty().withErrorCode("21002").withMessage("Cannot be empty")
                .minLength(5).withErrorCode("21004").withMessage("Allowed minimum length")
                .maxLength(42).withErrorCode("21005").withMessage("Allowed maximum length");
    }
}
