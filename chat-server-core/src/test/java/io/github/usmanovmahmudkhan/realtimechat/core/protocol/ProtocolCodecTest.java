package io.github.usmanovmahmudkhan.realtimechat.core.protocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolCodecTest {
    private final ProtocolCodec codec = new ProtocolCodec();

    @Test
    void decodesChatEvent() {
        UUID key = UUID.randomUUID();
        ClientEvent event = codec.decodeClient("""
                {"protocolVersion":2,"type":"CHAT","correlationId":"request-1",
                 "idempotencyKey":"%s","content":"hello"}
                """.formatted(key));
        assertEquals(ClientEventType.CHAT, event.type());
        assertEquals(key, event.idempotencyKey());
    }

    @Test
    void rejectsUnknownFields() {
        assertThrows(ProtocolException.class,
                () -> codec.decodeClient("{\"protocolVersion\":2,\"type\":\"PING\",\"unknown\":true}"));
    }
}
