package io.github.usmanovmahmudkhan.realtimechat.core.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class ProtocolCodec {
    private final ObjectMapper mapper;

    public ProtocolCodec() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public ClientEvent decodeClient(String json) {
        try {
            return mapper.readValue(json, ClientEvent.class);
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new ProtocolException(ErrorCode.INVALID_EVENT, "Invalid v2 event", exception);
        }
    }

    public String encodeServer(ServerEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not encode server event", exception);
        }
    }

    public ServerEvent decodeServer(String json) {
        try {
            return mapper.readValue(json, ServerEvent.class);
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new ProtocolException(ErrorCode.INVALID_EVENT, "Invalid server event", exception);
        }
    }
}
