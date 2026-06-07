package io.github.usmanovmahmudkhan.realtimechat.service;

import io.github.usmanovmahmudkhan.realtimechat.model.ChatMessage;
import io.github.usmanovmahmudkhan.realtimechat.util.JsonUtil;
import jakarta.websocket.Session;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry and broadcaster for active chat rooms.
 */
public final class ChatRoomManager {

    private static final ChatRoomManager INSTANCE = new ChatRoomManager();

    private final ConcurrentHashMap<String, Set<Session>> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Session, Participant> participants = new ConcurrentHashMap<>();

    /**
     * Creates an independent room manager.
     */
    public ChatRoomManager() {
    }

    /**
     * Returns the process-wide manager used by the WebSocket endpoint.
     */
    public static ChatRoomManager getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a session and announces its arrival.
     */
    public void joinRoom(String room, String username, Session session) {
        Participant previous = participants.put(session, new Participant(room, username));
        if (previous != null) {
            removeFromRoom(previous.room(), session);
        }

        rooms.computeIfAbsent(room, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        broadcast(room, ChatMessage.system(room, username + " joined the room"));
    }

    /**
     * Removes a registered session and announces its departure.
     */
    public void leaveRoom(Session session) {
        Participant participant = participants.remove(session);
        if (participant == null) {
            return;
        }

        removeFromRoom(participant.room(), session);
        broadcast(participant.room(),
                ChatMessage.system(participant.room(), participant.username() + " left the room"));
    }

    /**
     * Broadcasts content using the participant identity associated with a session.
     */
    public void sendChatMessage(Session session, String content) {
        Participant participant = participants.get(session);
        if (participant == null) {
            sendError(session, "Session is not joined to a room");
            return;
        }

        broadcast(participant.room(),
                ChatMessage.chat(participant.room(), participant.username(), content));
    }

    /**
     * Broadcasts a message only to sessions registered in the given room.
     */
    public void broadcast(String room, ChatMessage message) {
        Set<Session> sessions = rooms.get(room);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String json = JsonUtil.toJson(message);
        for (Session session : sessions) {
            sendSafely(session, json);
        }
    }

    /**
     * Sends an error directly to one session.
     */
    public void sendError(Session session, String message) {
        Participant participant = participants.get(session);
        String room = participant == null ? null : participant.room();
        sendSafely(session, JsonUtil.toJson(ChatMessage.error(room, message)));
    }

    /**
     * Returns the current number of tracked sessions in a room.
     */
    public int getUserCount(String room) {
        Set<Session> sessions = rooms.get(room);
        return sessions == null ? 0 : sessions.size();
    }

    /**
     * Returns an immutable snapshot of non-empty room names.
     */
    public Set<String> getActiveRooms() {
        return Collections.unmodifiableSet(Set.copyOf(rooms.keySet()));
    }

    private void sendSafely(Session session, String json) {
        if (!session.isOpen()) {
            removeDeadSession(session);
            return;
        }

        try {
            session.getAsyncRemote().sendText(json, result -> {
                if (!result.isOK()) {
                    removeDeadSession(session);
                }
            });
        } catch (RuntimeException exception) {
            removeDeadSession(session);
        }
    }

    private void removeDeadSession(Session session) {
        Participant participant = participants.remove(session);
        if (participant != null) {
            removeFromRoom(participant.room(), session);
        }
    }

    private void removeFromRoom(String room, Session session) {
        rooms.computeIfPresent(room, (ignored, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    private record Participant(String room, String username) {
    }
}
