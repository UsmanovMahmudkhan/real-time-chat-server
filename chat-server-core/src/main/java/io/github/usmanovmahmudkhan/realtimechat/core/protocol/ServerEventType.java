package io.github.usmanovmahmudkhan.realtimechat.core.protocol;

public enum ServerEventType {
    CONNECTED,
    CHAT,
    SYSTEM,
    ERROR,
    RATE_LIMITED,
    AUTHORIZATION_REVOKED,
    RESUME_COMPLETE,
    PONG
}
