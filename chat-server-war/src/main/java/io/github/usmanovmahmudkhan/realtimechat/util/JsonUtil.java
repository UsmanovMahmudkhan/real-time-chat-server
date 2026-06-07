package io.github.usmanovmahmudkhan.realtimechat.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.usmanovmahmudkhan.realtimechat.model.ChatMessage;
import io.github.usmanovmahmudkhan.realtimechat.model.IncomingMessage;

/**
 * Central JSON codec for the chat protocol.
 */
public final class JsonUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonUtil() {
    }

    /**
     * Parses a client message or throws a clear protocol-level exception.
     */
    public static IncomingMessage fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Invalid JSON message");
        }

        try {
            return OBJECT_MAPPER.readValue(json, IncomingMessage.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid JSON message", exception);
        }
    }

    /**
     * Serializes a server message.
     */
    public static String toJson(ChatMessage message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize chat message", exception);
        }
    }
}
