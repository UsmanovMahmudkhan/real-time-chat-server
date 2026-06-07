package io.github.usmanovmahmudkhan.realtimechat.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Message broadcast by the server.
 *
 * @param type message type
 * @param room destination room
 * @param sender display name of the sender
 * @param content message body
 * @param timestamp creation time in UTC
 */
public record ChatMessage(
        MessageType type,
        String room,
        String sender,
        String content,
        Instant timestamp
) {
    /**
     * Checks required protocol fields.
     */
    public ChatMessage {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    /**
     * Creates a user chat message using the current time.
     */
    public static ChatMessage chat(String room, String sender, String content) {
        return new ChatMessage(MessageType.CHAT, room, sender, content, Instant.now());
    }

    /**
     * Creates a server system message using the current time.
     */
    public static ChatMessage system(String room, String content) {
        return new ChatMessage(MessageType.SYSTEM, room, "SERVER", content, Instant.now());
    }

    /**
     * Creates a server error message using the current time.
     */
    public static ChatMessage error(String room, String content) {
        return new ChatMessage(MessageType.ERROR, room, "SERVER", content, Instant.now());
    }
}
