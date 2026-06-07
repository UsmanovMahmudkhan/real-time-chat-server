package io.github.usmanovmahmudkhan.realtimechat.util;

import java.util.regex.Pattern;

/**
 * Validation rules for values crossing the WebSocket boundary.
 */
public final class ValidationUtil {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]+");

    private ValidationUtil() {
    }

    /**
     * Returns whether a room name follows the public protocol rules.
     */
    public static boolean isValidRoom(String room) {
        return isValidIdentifier(room, 50);
    }

    /**
     * Returns whether a username follows the public protocol rules.
     */
    public static boolean isValidUsername(String username) {
        return isValidIdentifier(username, 30);
    }

    /**
     * Returns whether a message body follows the public protocol rules.
     */
    public static boolean isValidMessage(String message) {
        return message != null && !message.isBlank() && message.length() <= 1000;
    }

    private static boolean isValidIdentifier(String value, int maximumLength) {
        return value != null
                && !value.isBlank()
                && value.length() <= maximumLength
                && IDENTIFIER.matcher(value).matches();
    }
}
