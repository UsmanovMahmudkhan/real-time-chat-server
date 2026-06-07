package io.github.usmanovmahmudkhan.realtimechat.websocket;

import io.github.usmanovmahmudkhan.realtimechat.model.IncomingMessage;
import io.github.usmanovmahmudkhan.realtimechat.model.MessageType;
import io.github.usmanovmahmudkhan.realtimechat.service.ChatRoomManager;
import io.github.usmanovmahmudkhan.realtimechat.util.JsonUtil;
import io.github.usmanovmahmudkhan.realtimechat.util.ValidationUtil;
import io.github.usmanovmahmudkhan.realtimechat.runtime.ChatRuntime;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;

/**
 * WebSocket transport adapter for room-based chat.
 */
@ServerEndpoint("/chat/{room}/{username}")
public final class ChatEndpoint {

    private static final ChatRoomManager ROOM_MANAGER = ChatRoomManager.getInstance();

    /**
     * Creates an endpoint instance for the WebSocket container.
     */
    public ChatEndpoint() {
    }

    /**
     * Validates and joins a newly opened session.
     */
    @OnOpen
    public void onOpen(
            Session session,
            @PathParam("room") String room,
            @PathParam("username") String username
    ) {
        if (!ChatRuntime.get().config().v1Enabled()) {
            closePolicyViolation(session, "Version 1 endpoint is disabled");
            return;
        }
        if (!ValidationUtil.isValidRoom(room) || !ValidationUtil.isValidUsername(username)) {
            ROOM_MANAGER.sendError(session, "Invalid room or username");
            closePolicyViolation(session, "Invalid room or username");
            return;
        }

        ROOM_MANAGER.joinRoom(room, username, session);
    }

    /**
     * Validates and delegates an incoming client message.
     */
    @OnMessage
    public void onMessage(String json, Session session) {
        try {
            IncomingMessage incoming = JsonUtil.fromJson(json);
            if (incoming.type() != MessageType.CHAT || !ValidationUtil.isValidMessage(incoming.content())) {
                ROOM_MANAGER.sendError(session, "Invalid message format");
                return;
            }

            ROOM_MANAGER.sendChatMessage(session, incoming.content());
        } catch (IllegalArgumentException exception) {
            ROOM_MANAGER.sendError(session, "Invalid message format");
        }
    }

    /**
     * Removes a normally closed session.
     */
    @OnClose
    public void onClose(Session session) {
        ROOM_MANAGER.leaveRoom(session);
    }

    /**
     * Removes a session after a transport or container error.
     */
    @OnError
    public void onError(Session session, Throwable error) {
        ROOM_MANAGER.leaveRoom(session);
    }

    private void closePolicyViolation(Session session, String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
        } catch (IOException | RuntimeException ignored) {
            // The container may already have closed a rejected connection.
        }
    }
}
