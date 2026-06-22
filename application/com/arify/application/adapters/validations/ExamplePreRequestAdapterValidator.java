package com.arify.application.adapters.validations;

import com.arify.application.adapters.ExamplePreRequestAdapter;
import com.arify.application.internals.validators.AbstractValidator;

public class ExamplePreRequestAdapterValidator extends AbstractValidator<ExamplePreRequestAdapter> {
    public ExamplePreRequestAdapterValidator() {
        ruleFor(ExamplePreRequestAdapter::previewMessageIdentification)
                .notNull().withErrorCode("21001").withMessage("Cannot be null")
                .notEmpty().withErrorCode("21002").withMessage("Cannot be empty")
                .minLength(5).withErrorCode("21004").withMessage("Allowed minimum length")
                .maxLength(22).withErrorCode("21005").withMessage("Allowed maximum length");

        ruleFor(ExamplePreRequestAdapter::identificationForAccount)
                .notNull().withErrorCode("21001").withMessage("Cannot be null")
                .notEmpty().withErrorCode("21002").withMessage("Cannot be empty")
                .minLength(5).withErrorCode("21004").withMessage("Allowed minimum length")
                .maxLength(22).withErrorCode("21005").withMessage("Allowed maximum length");
    }
}
