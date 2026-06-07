package io.github.usmanovmahmudkhan.realtimechat.model;

/**
 * Message categories supported by the chat protocol.
 */
public enum MessageType {
    /** A user-authored chat message. */
    CHAT,
    /** A server-authored room event. */
    SYSTEM,
    /** A server-authored validation or protocol error. */
    ERROR
}
