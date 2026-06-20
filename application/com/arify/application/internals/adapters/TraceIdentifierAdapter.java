package com.arify.application.internals.adapters;

public record TraceIdentifierAdapter(
        String deviceIdentifier,
        String messageIdentifier,
        String channelIdentifier) {
}
