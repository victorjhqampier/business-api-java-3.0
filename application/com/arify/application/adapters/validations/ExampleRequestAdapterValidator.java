package com.arify.application.adapters.validations;

import com.arify.application.adapters.ExampleRequestAdapter;
import com.arify.application.internals.validators.AbstractValidator;
import com.arify.domain.entities.InternalApiMessage;

public class ExampleRequestAdapterValidator extends AbstractValidator<ExampleRequestAdapter> {
    public ExampleRequestAdapterValidator() {
        // Equivalente a: RuleFor(x => x.ChannelIdentification) en C#
        ruleFor(ExampleRequestAdapter::channelIdentification)
                .notNull()
                .notEmpty()
                .minLength(7)
                .maxLength(30).withErrorCode("122").withMessage("Corrija el campo caray");

        ruleFor(ExampleRequestAdapter::messageIdentification)
                .notNull()
                .notEmpty()
                .minLength(7)
                .maxLength(30).withErrorCode("2");

        ruleFor(ExampleRequestAdapter::deviceIdentifier)
                .notNull()
                .notEmpty()
                .minLength(7)
                .maxLength(40).withErrorCode("3");

        ruleFor(ExampleRequestAdapter::customerIdentificationNumber)
                .notNull()
                .notEmpty()
                .minLength(1)
                .maxLength(10)
                .isNumeric().withErrorCode("15091").withMessage(InternalApiMessage._15091.value);

        ruleFor(ExampleRequestAdapter::identificationForAccount)
                .notNull()
                .notEmpty()
                .minLength(10)
                .maxLength(30).withErrorCode("15091");
    }
}