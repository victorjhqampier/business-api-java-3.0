package com.arify.application.internals.adapters;

import com.arify.application.internals.validators.ArifyValidator;

public class TraceIdentifierAdapterValidator extends ArifyValidator {
    public TraceIdentifierAdapterValidator(TraceIdentifierAdapter traceIdentifier) {
        super();

        addRules(
                field("deviceIdentifier", traceIdentifier.deviceIdentifier())
                        .notNull()
                        .withCode("21001")
                        .withMessage("Cannot be null")
                        .notEmpty()
                        .withCode("21002")
                        .withMessage("Cannot be empty")
                        .minLength(5)
                        .withCode("21004")
                        .withMessage("Allowed minimum length")
                        .maxLength(42)
                        .withCode("21005")
                        .withMessage("Allowed maximum length")
                        .validate());

        addRules(
                field("messageIdentifier", traceIdentifier.messageIdentifier())
                        .notNull()
                        .withCode("21001")
                        .withMessage("Cannot be null")
                        .notEmpty()
                        .withCode("21002")
                        .withMessage("Cannot be empty")
                        .minLength(5)
                        .withCode("21004")
                        .withMessage("Allowed minimum length")
                        .maxLength(42)
                        .withCode("21005")
                        .withMessage("Allowed maximum length")
                        .validate());

        addRules(
                field("channelIdentifier", traceIdentifier.channelIdentifier())
                        .notNull()
                        .withCode("21001")
                        .withMessage("Cannot be null")
                        .notEmpty()
                        .withCode("21002")
                        .withMessage("Cannot be empty")
                        .minLength(5)
                        .withCode("21004")
                        .withMessage("Allowed minimum length")
                        .maxLength(42)
                        .withCode("21005")
                        .withMessage("Allowed maximum length")
                        .validate());
    }
}
