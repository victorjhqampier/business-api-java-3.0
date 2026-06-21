package com.arify.application.adapters;

import com.arify.application.internals.validators.AbstractValidator;
import com.arify.domain.entities.InternalApiMessage;

public record ExampleRequestAdapter(
        String channelIdentification,
        String messageIdentification,
        String deviceIdentifier,
        String customerIdentificationNumber,
        String identificationForAccount) {
}