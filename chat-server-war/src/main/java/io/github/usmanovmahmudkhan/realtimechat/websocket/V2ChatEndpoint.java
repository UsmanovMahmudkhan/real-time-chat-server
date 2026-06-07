package io.github.usmanovmahmudkhan.realtimechat.websocket;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.AuthenticatedUser;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ErrorCode;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ProtocolException;
import io.github.usmanovmahmudkhan.realtimechat.core.util.UuidV7;
import io.github.usmanovmahmudkhan.realtimechat.runtime.ChatRuntime;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.UUID;
import io.github.usmanovmahmudkhan.realtimechat.observability.StructuredLog;

@ServerEndpoint(value = "/v2/tenants/{tenantId}/rooms/{roomId}/chat", configurator = V2EndpointConfigurator.class)
public final class V2ChatEndpoint {
    @OnOpen
    public void onOpen(Session session, EndpointConfig endpointConfig,
                       @PathParam("tenantId") String tenantId, @PathParam("roomId") String roomId) {
        Object failure = endpointConfig.getUserProperties().get(V2EndpointConfigurator.AUTHENTICATION_ERROR);
        Object identity = endpointConfig.getUserProperties().get(V2EndpointConfigurator.AUTHENTICATED_USER);
        if (failure != null || !(identity instanceof AuthenticatedUser user)) {
            close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "Authentication failed");
            return;
        }
        try {
            session.setMaxTextMessageBufferSize(ChatRuntime.get().config().maximumMessageLength() * 2);
            ChatRuntime.get().sessions().join(session, user, UUID.fromString(tenantId), UUID.fromString(roomId),
                    UuidV7.next().toString());
        } catch (ProtocolException | IllegalArgumentException exception) {
            close(session, CloseReason.CloseCodes.VIOLATED_POLICY, exception.getMessage());
        }
    }

    @OnMessage
    public void onMessage(Session session, String json) {
        ChatRuntime.get().sessions().handle(session, json);
    }

    @OnClose
    public void onClose(Session session) {
        ChatRuntime.get().sessions().leave(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        StructuredLog.error("websocket_session_error", java.util.Map.of("sessionId", session.getId()), error);
        ChatRuntime.get().sessions().leave(session);
    }

    private void close(Session session, CloseReason.CloseCode code, String message) {
        try {
            session.close(new CloseReason(code, message));
        } catch (IOException | RuntimeException ignored) {
            // Container may already have closed the session.
        }
    }
}
