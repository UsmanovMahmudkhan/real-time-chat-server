package io.github.usmanovmahmudkhan.realtimechat.util;

import io.github.usmanovmahmudkhan.realtimechat.model.ChatMessage;
import io.github.usmanovmahmudkhan.realtimechat.model.IncomingMessage;
import io.github.usmanovmahmudkhan.realtimechat.model.MessageType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUtilTest {

    @Test
    void serializesChatMessage() {
        ChatMessage message = new ChatMessage(
                MessageType.CHAT,
                "general",
                "mahmud",
                "Hello everyone",
                Instant.parse("2026-06-07T12:00:00Z")
        );

        String json = JsonUtil.toJson(message);

        assertTrue(json.contains("\"type\":\"CHAT\""));
        assertTrue(json.contains("\"room\":\"general\""));
        assertTrue(json.contains("\"sender\":\"mahmud\""));
        assertTrue(json.contains("\"content\":\"Hello everyone\""));
        assertTrue(json.contains("\"timestamp\":\"2026-06-07T12:00:00Z\""));
    }

    @Test
    void deserializesIncomingMessage() {
        IncomingMessage message = JsonUtil.fromJson("""
                {"type":"CHAT","content":"Hello everyone"}
                """);

        assertEquals(MessageType.CHAT, message.type());
        assertEquals("Hello everyone", message.content());
    }

    @Test
    void rejectsInvalidJson() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JsonUtil.fromJson("{not-json}")
        );

        assertEquals("Invalid JSON message", exception.getMessage());
    }
}
