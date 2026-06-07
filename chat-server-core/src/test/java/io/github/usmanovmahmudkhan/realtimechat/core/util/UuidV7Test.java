package io.github.usmanovmahmudkhan.realtimechat.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UuidV7Test {
    @Test
    void generatesVersionSevenRfc4122Identifiers() {
        var id = UuidV7.next();

        assertEquals(7, id.version());
        assertEquals(2, id.variant());
    }
}
