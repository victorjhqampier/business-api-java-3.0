package com.arify.application.adapters;

import com.arify.application.internals.validators.ArifyValidator;
import com.arify.domain.entities.InternalApiMessage;

public class ExampleRequestAdapterValidator extends ArifyValidator {
    public ExampleRequestAdapterValidator(ExampleRequestAdapter exampleRequest) {
        super();

        addRules(
                field("channelIdentification", exampleRequest.channelIdentification())
                        .notNull()
                        .notEmpty()
                        .minLength(7)
                        .maxLength(30)
                        .withCode("122")
                        .withMessage("Corrija el campo caray")
                        .validate());

        addRules(
                field("messageIdentification", exampleRequest.messageIdentification())
                        .notNull()
                        .notEmpty()
                        .minLength(7)
                        .maxLength(30)
                        .withCode("2")
                        .validate());

        addRules(
                field("deviceIdentifier", exampleRequest.deviceIdentifier())
                        .notNull()
                        .notEmpty()
                        .minLength(7)
                        .maxLength(40)
                        .withCode("3")
                        .validate());

        addRules(
                field("customerIdentificationNumber", exampleRequest.customerIdentificationNumber())
                        .notNull()
                        .notEmpty()
                        .minLength(1)
                        .maxLength(10)
                        .isNumeric()
                        .withCode("15091")
                        .withMessage(InternalApiMessage._15091.value)
                        .validate());

        addRules(
                field("identificationForAccount", exampleRequest.identificationForAccount())
                        .notNull()
                        .notEmpty()
                        .minLength(10)
                        .maxLength(30)
                        .withCode("15091")
                        .validate());
    }
}
