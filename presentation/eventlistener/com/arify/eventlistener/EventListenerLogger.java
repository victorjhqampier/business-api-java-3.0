package com.arify.eventlistener;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.logging.Logger;

public class EventListenerLogger {
    public static final ThreadLocal<String> eventListenerTraceIdContext = new ThreadLocal<>();

    public static String setTraceId() {
        return setTraceId(null);
    }

    public static String setTraceId(String traceId) {
        String currentTraceId = traceId == null ? UUID.randomUUID().toString() : traceId;
        eventListenerTraceIdContext.set(currentTraceId);
        return currentTraceId;
    }

    public static Logger setLogger() {
        return Logger.getLogger("presentation.eventlistener");
    }

    public static String format(String level, String message, String traceId, String payload, String stacktrace) {
        String currentTraceId = traceId != null ? traceId : eventListenerTraceIdContext.get();
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
