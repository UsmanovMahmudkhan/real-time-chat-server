package io.github.usmanovmahmudkhan.realtimechat.runtime;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.AuthenticatedUser;
import io.github.usmanovmahmudkhan.realtimechat.core.port.CoordinationService;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.*;
import io.github.usmanovmahmudkhan.realtimechat.core.service.MessageCommandService;
import io.github.usmanovmahmudkhan.realtimechat.core.util.UuidV7;
import io.github.usmanovmahmudkhan.realtimechat.observability.StructuredLog;
import io.micrometer.core.instrument.Counter;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

public final class V2SessionManager implements AutoCloseable {
    private final MessageCommandService messages;
    private final CoordinationService coordination;
    private final ProtocolCodec codec = new ProtocolCodec();
    private final ConcurrentHashMap<Session, SessionState> sessions = new ConcurrentHashMap<>();
    private final int maximumQueueSize;
    private final Counter delivered;
    private final Counter rejected;
    private final Counter slowConsumers;
    private final Tracer tracer = GlobalOpenTelemetry.getTracer("real-time-chat-server");

    public V2SessionManager(MessageCommandService messages, CoordinationService coordination,
                            PrometheusMeterRegistry metrics, int maximumQueueSize) {
        this.messages = messages;
        this.coordination = coordination;
        this.maximumQueueSize = maximumQueueSize;
        this.delivered = Counter.builder("realtimechat_messages_delivered_total").register(metrics);
        this.rejected = Counter.builder("realtimechat_events_rejected_total").register(metrics);
        this.slowConsumers = Counter.builder("realtimechat_slow_consumers_total").register(metrics);
        metrics.gauge("realtimechat_active_sessions", sessions, ConcurrentHashMap::size);
    }

    public void join(Session session, AuthenticatedUser user, UUID tenantId, UUID roomId, String correlationId) {
        if (!coordination.allow("connection-user", user.userId().toString(), 20, Duration.ofMinutes(1))
                || !coordination.allow("connection-tenant", tenantId.toString(), 1000, Duration.ofMinutes(1))
                || !coordination.allow("connection-room", roomId.toString(), 500, Duration.ofMinutes(1))) {
            throw new ProtocolException(ErrorCode.RATE_LIMITED, "Connection rate limit exceeded");
        }
        if (!messages.canJoin(user, tenantId, roomId)) {
            throw new ProtocolException(ErrorCode.UNAUTHORIZED, "Room join permission denied");
        }
        SessionState state = new SessionState(session, user, tenantId, roomId);
        try {
            state.subscription = coordination.subscribe(tenantId, roomId, event -> enqueue(state, event));
        } catch (Exception exception) {
            throw new ProtocolException(ErrorCode.SERVICE_UNAVAILABLE, "Live coordination unavailable", exception);
        }
        sessions.put(session, state);
        coordination.touchPresence(tenantId, roomId, user.userId(), Duration.ofSeconds(45));
        enqueue(state, new ServerEvent(2, UuidV7.next(), ServerEventType.CONNECTED, tenantId, roomId,
                Instant.now(), correlationId, null, null, null, "Connected"));
    }

    public void handle(Session session, String json) {
        SessionState state = requireState(session);
        var span = tracer.spanBuilder("chat.websocket.event").startSpan();
        try {
            span.setAttribute("chat.tenant_id", state.tenantId.toString());
            span.setAttribute("chat.room_id", state.roomId.toString());
            ClientEvent event = codec.decodeClient(json);
            if (event.protocolVersion() != 2 || event.type() == null || event.correlationId() == null) {
                throw new ProtocolException(ErrorCode.INVALID_EVENT, "Unsupported or incomplete event");
            }
            coordination.touchPresence(state.tenantId, state.roomId, state.user.userId(), Duration.ofSeconds(45));
            switch (event.type()) {
                case CHAT -> messages.send(state.user, state.tenantId, state.roomId,
                        event.idempotencyKey(), event.content(), event.correlationId());
                case RESUME -> {
                    messages.resume(state.user, state.tenantId, state.roomId, event.afterMessageId())
                            .forEach(message -> enqueue(state, ServerEvent.chat(message, event.correlationId())));
                    enqueue(state, new ServerEvent(2, UuidV7.next(), ServerEventType.RESUME_COMPLETE,
                            state.tenantId, state.roomId, Instant.now(), event.correlationId(),
                            null, null, null, "Resume complete"));
                }
                case PING -> enqueue(state, new ServerEvent(2, UuidV7.next(), ServerEventType.PONG,
                        state.tenantId, state.roomId, Instant.now(), event.correlationId(),
                        null, null, null, null));
                case ACK -> { }
            }
        } catch (ProtocolException exception) {
            span.recordException(exception);
            rejected.increment();
            state.violations++;
            enqueue(state, ServerEvent.error(state.tenantId, state.roomId, UuidV7.next().toString(),
                    exception.code(), exception.getMessage()));
            if (state.violations >= 3) {
                close(state, CloseReason.CloseCodes.VIOLATED_POLICY, "Protocol violation threshold exceeded");
            }
        } catch (RuntimeException exception) {
            span.recordException(exception);
            rejected.increment();
            StructuredLog.error("websocket_event_failed", Map.of(
                    "tenantId", state.tenantId,
                    "roomId", state.roomId,
                    "sessionId", state.session.getId()
            ), exception);
            enqueue(state, ServerEvent.error(state.tenantId, state.roomId, UuidV7.next().toString(),
                    ErrorCode.INTERNAL_ERROR, "The event could not be processed"));
            close(state, CloseReason.CloseCodes.UNEXPECTED_CONDITION, "Internal processing failure");
        } finally {
            span.end();
        }
    }

    public void leave(Session session) {
        SessionState state = sessions.remove(session);
        if (state != null && state.subscription != null) {
            try {
                state.subscription.close();
            } catch (Exception ignored) {
                // Subscription cleanup is best effort during disconnect.
            }
        }
    }

    public boolean allowRestRequest(AuthenticatedUser user) {
        return coordination.allow("rest-user", user.userId().toString(), 100, Duration.ofMinutes(1))
                && coordination.allow("rest-tenant", user.tenantId().toString(), 5000, Duration.ofMinutes(1));
    }

    private void enqueue(SessionState state, ServerEvent event) {
        synchronized (state) {
            if (state.closed || !state.session.isOpen()) {
                return;
            }
            if (state.queue.size() >= maximumQueueSize) {
                slowConsumers.increment();
                close(state, CloseReason.CloseCodes.TRY_AGAIN_LATER, "Slow consumer");
                return;
            }
            state.queue.add(codec.encodeServer(event));
            if (!state.sending) {
                state.sending = true;
                sendNext(state);
            }
        }
    }

    private void sendNext(SessionState state) {
        String next;
        synchronized (state) {
            next = state.queue.poll();
            if (next == null) {
                state.sending = false;
                return;
            }
        }
        state.session.getAsyncRemote().sendText(next, result -> {
            if (!result.isOK()) {
                close(state, CloseReason.CloseCodes.UNEXPECTED_CONDITION, "Send failed");
                return;
            }
            delivered.increment();
            sendNext(state);
        });
    }

    private void close(SessionState state, CloseReason.CloseCode code, String reason) {
        synchronized (state) {
            if (state.closed) {
                return;
            }
            state.closed = true;
        }
        leave(state.session);
        try {
            state.session.close(new CloseReason(code, reason));
        } catch (IOException | RuntimeException ignored) {
            // Container may already have closed the session.
        }
    }

    private SessionState requireState(Session session) {
        SessionState state = sessions.get(session);
        if (state == null) {
            throw new ProtocolException(ErrorCode.UNAUTHENTICATED, "Session is not authenticated");
        }
        return state;
    }

    @Override
    public void close() {
        sessions.values().forEach(state -> close(state, CloseReason.CloseCodes.GOING_AWAY, "Server shutdown"));
    }

    private static final class SessionState {
        private final Session session;
        private final AuthenticatedUser user;
        private final UUID tenantId;
        private final UUID roomId;
        private final ArrayDeque<String> queue = new ArrayDeque<>();
        private AutoCloseable subscription;
        private boolean sending;
        private boolean closed;
        private int violations;

        private SessionState(Session session, AuthenticatedUser user, UUID tenantId, UUID roomId) {
            this.session = session;
            this.user = user;
            this.tenantId = tenantId;
            this.roomId = roomId;
        }
    }
}
