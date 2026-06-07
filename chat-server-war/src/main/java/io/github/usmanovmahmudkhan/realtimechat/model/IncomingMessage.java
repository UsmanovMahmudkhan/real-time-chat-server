package io.github.usmanovmahmudkhan.realtimechat.model;

/**
 * Message accepted from a WebSocket client.
 *
 * @param type message type; only {@link MessageType#CHAT} is accepted from clients
 * @param content message body
 */
public record IncomingMessage(MessageType type, String content) {
}
