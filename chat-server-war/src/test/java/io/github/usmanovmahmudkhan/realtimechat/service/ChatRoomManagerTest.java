package io.github.usmanovmahmudkhan.realtimechat.service;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRoomManagerTest {

    @Test
    void joinsAndLeavesRoom() {
        ChatRoomManager manager = new ChatRoomManager();
        TestSession testSession = new TestSession();
        Session session = testSession.session();

        manager.joinRoom("general", "mahmud", session);

        assertEquals(1, manager.getUserCount("general"));
        assertTrue(manager.getActiveRooms().contains("general"));

        manager.leaveRoom(session);

        assertEquals(0, manager.getUserCount("general"));
        assertFalse(manager.getActiveRooms().contains("general"));
    }

    @Test
    void tracksCountsIndependentlyByRoom() {
        ChatRoomManager manager = new ChatRoomManager();

        manager.joinRoom("general", "mahmud", new TestSession().session());
        manager.joinRoom("general", "alex", new TestSession().session());
        manager.joinRoom("support", "sam", new TestSession().session());

        assertEquals(2, manager.getUserCount("general"));
        assertEquals(1, manager.getUserCount("support"));
        assertEquals(0, manager.getUserCount("missing"));
    }

    @Test
    void removesClosedSessionDuringBroadcast() {
        ChatRoomManager manager = new ChatRoomManager();
        TestSession testSession = new TestSession();
        Session session = testSession.session();
        manager.joinRoom("general", "mahmud", session);
        testSession.close();

        manager.broadcast("general",
                io.github.usmanovmahmudkhan.realtimechat.model.ChatMessage.system("general", "check"));

        assertEquals(0, manager.getUserCount("general"));
    }

    @Test
    void broadcastsUsingAsyncRemote() {
        ChatRoomManager manager = new ChatRoomManager();
        TestSession testSession = new TestSession();

        manager.joinRoom("general", "mahmud", testSession.session());

        assertEquals(1, testSession.sendCount());
    }

    private static final class TestSession {

        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicInteger sendCount = new AtomicInteger();
        private final RemoteEndpoint.Async async = (RemoteEndpoint.Async) Proxy.newProxyInstance(
                RemoteEndpoint.Async.class.getClassLoader(),
                new Class<?>[]{RemoteEndpoint.Async.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("sendText")) {
                        sendCount.incrementAndGet();
                        if (arguments.length == 2 && arguments[1] instanceof SendHandler handler) {
                            handler.onResult(new SendResult());
                        }
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        private final Session session = (Session) Proxy.newProxyInstance(
                Session.class.getClassLoader(),
                new Class<?>[]{Session.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isOpen" -> open.get();
                    case "getAsyncRemote" -> async;
                    case "close" -> {
                        open.set(false);
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "TestSession";
                    default -> defaultValue(method.getReturnType());
                }
        );

        Session session() {
            return session;
        }

        int sendCount() {
            return sendCount.get();
        }

        void close() {
            open.set(false);
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == char.class) {
                return '\0';
            }
            return 0;
        }
    }
}
