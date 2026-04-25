package com.arify.application.internals.adapters;

public final class TraceIdentifierAdapter {

    private final String deviceIdentifier;
    private final String messageIdentifier;
    private final String channelIdentifier;

    public TraceIdentifierAdapter(String deviceIdentifier, String messageIdentifier, String channelIdentifier) {
        this.deviceIdentifier = deviceIdentifier;
        this.messageIdentifier = messageIdentifier;
        this.channelIdentifier = channelIdentifier;
    }

    public String getDeviceIdentifier() {
        return deviceIdentifier;
    }

    public String getMessageIdentifier() {
        return messageIdentifier;
    }

    public String getChannelIdentifier() {
        return channelIdentifier;
    }
}
