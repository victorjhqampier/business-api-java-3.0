package com.arify.application.adapters;

public record ExampleRequestAdapter(
        String channelIdentification,
        String messageIdentification,
        String deviceIdentifier,
        String customerIdentificationNumber,
        String identificationForAccount) {
}
