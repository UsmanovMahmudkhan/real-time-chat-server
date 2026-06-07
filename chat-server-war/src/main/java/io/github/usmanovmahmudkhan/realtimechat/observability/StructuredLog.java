package io.github.usmanovmahmudkhan.realtimechat.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StructuredLog {
    private static final Logger LOGGER = Logger.getLogger("io.github.usmanovmahmudkhan.realtimechat");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StructuredLog() {
    }

    public static void info(String event, Map<String, ?> fields) {
        log(Level.INFO, event, fields, null);
    }

    public static void error(String event, Map<String, ?> fields, Throwable error) {
        log(Level.SEVERE, event, fields, error);
    }

    private static void log(Level level, String event, Map<String, ?> fields, Throwable error) {
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("timestamp", Instant.now().toString());
        safe.put("event", event);
        safe.putAll(fields);
        if (error != null) {
            safe.put("errorType", error.getClass().getSimpleName());
        }
        try {
            LOGGER.log(level, MAPPER.writeValueAsString(safe));
        } catch (JsonProcessingException exception) {
            LOGGER.log(level, event);
        }
    }
}
