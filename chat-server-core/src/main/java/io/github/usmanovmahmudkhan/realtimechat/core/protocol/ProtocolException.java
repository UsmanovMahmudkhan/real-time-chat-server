package io.github.usmanovmahmudkhan.realtimechat.core.protocol;

public final class ProtocolException extends RuntimeException {
    private final ErrorCode code;

    public ProtocolException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ProtocolException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
