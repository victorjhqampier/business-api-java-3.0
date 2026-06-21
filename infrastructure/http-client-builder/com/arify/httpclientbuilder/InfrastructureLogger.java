package com.arify.httpclientbuilder;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.logging.Logger;

public class InfrastructureLogger {
    public static final ThreadLocal<String> infrastructureTraceIdContext = new ThreadLocal<>();

    public static String setTraceId() {
        return setTraceId(null);
    }

    public static String setTraceId(String traceId) {
        String currentTraceId = traceId == null ? UUID.randomUUID().toString() : traceId;
        infrastructureTraceIdContext.set(currentTraceId);
        return currentTraceId;
    }

    public static Logger setLogger() {
        return Logger.getLogger("infrastructure.httpclientbuilder");
    }

    public static String format(String level, String message, String traceId, String payload, String stacktrace) {
        String currentTraceId = traceId != null ? traceId : infrastructureTraceIdContext.get();
        if (currentTraceId == null) {
            currentTraceId = UUID.randomUUID().toString();
        }

        return "{"
                + "\"timestamp\":\"" + OffsetDateTime.now() + "\","
                + "\"log_level\":\"" + level + "\","
                + "\"trace_id\":\"" + currentTraceId + "\","
                + "\"message\":\"" + message + "\","
                + "\"payload\":" + payload + ","
                + "\"stacktrace\":" + stacktrace
                + "}";
    }
}
