package io.github.usmanovmahmudkhan.realtimechat.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationUtilTest {

    @Test
    void validatesRoomNames() {
        assertTrue(ValidationUtil.isValidRoom("general_room-2"));
        assertFalse(ValidationUtil.isValidRoom(null));
        assertFalse(ValidationUtil.isValidRoom(" "));
        assertFalse(ValidationUtil.isValidRoom("general room"));
        assertFalse(ValidationUtil.isValidRoom("a".repeat(51)));
    }

    @Test
    void validatesUsernames() {
        assertTrue(ValidationUtil.isValidUsername("mahmud_2"));
        assertFalse(ValidationUtil.isValidUsername(null));
        assertFalse(ValidationUtil.isValidUsername(""));
        assertFalse(ValidationUtil.isValidUsername("mahmud!"));
        assertFalse(ValidationUtil.isValidUsername("a".repeat(31)));
    }

    @Test
    void validatesMessages() {
        assertTrue(ValidationUtil.isValidMessage("Hello everyone"));
        assertFalse(ValidationUtil.isValidMessage(null));
        assertFalse(ValidationUtil.isValidMessage(" \n "));
        assertFalse(ValidationUtil.isValidMessage("a".repeat(1001)));
    }
}
