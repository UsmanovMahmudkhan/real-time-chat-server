package io.github.usmanovmahmudkhan.realtimechat.redis;

import io.github.usmanovmahmudkhan.realtimechat.core.port.CoordinationService;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ProtocolCodec;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ServerEvent;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class RedisCoordinationService implements CoordinationService {
    private static final String RATE_SCRIPT = """
            local now = redis.call('TIME')
            local now_ms = now[1] * 1000 + math.floor(now[2] / 1000)
            local capacity = tonumber(ARGV[1])
            local window_ms = tonumber(ARGV[2])
            local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'updated')
            local tokens = tonumber(bucket[1]) or capacity
            local updated = tonumber(bucket[2]) or now_ms
            tokens = math.min(capacity, tokens + ((now_ms - updated) * capacity / window_ms))
            local allowed = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            end
            redis.call('HSET', KEYS[1], 'tokens', tokens, 'updated', now_ms)
            redis.call('PEXPIRE', KEYS[1], window_ms * 2)
            return allowed
            """;

    private final String namespace;
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> commands;
    private final StatefulRedisPubSubConnection<String, String> pubSub;
    private final ProtocolCodec codec = new ProtocolCodec();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<ServerEvent>>> consumers =
            new ConcurrentHashMap<>();

    public RedisCoordinationService(String redisUri, String environment) {
        this.namespace = "realtimechat:" + requireSafe(environment) + ":";
        this.client = RedisClient.create(redisUri);
        this.commands = client.connect();
        this.pubSub = client.connectPubSub();
        this.pubSub.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                var listeners = consumers.get(channel);
                if (listeners == null) {
                    return;
                }
                ServerEvent event = codec.decodeServer(message);
                listeners.forEach(listener -> listener.accept(event));
            }
        });
    }

    @Override
    public void publish(ServerEvent event) {
        commands.sync().publish(channel(event.tenantId(), event.roomId()), codec.encodeServer(event));
    }

    @Override
    public AutoCloseable subscribe(UUID tenantId, UUID roomId, Consumer<ServerEvent> consumer) {
        String channel = channel(tenantId, roomId);
        consumers.computeIfAbsent(channel, ignored -> new CopyOnWriteArrayList<>()).add(consumer);
        pubSub.sync().subscribe(channel);
        return () -> {
            var listeners = consumers.get(channel);
            if (listeners != null) {
                listeners.remove(consumer);
                if (listeners.isEmpty()) {
                    consumers.remove(channel, listeners);
                    pubSub.sync().unsubscribe(channel);
                }
            }
        };
    }

    @Override
    public boolean allow(String dimension, String key, int limit, Duration window) {
        String redisKey = namespace + "rate:" + requireSafe(dimension) + ":" + requireSafe(key);
        Long result = commands.sync().eval(RATE_SCRIPT, ScriptOutputType.INTEGER,
                new String[]{redisKey}, Integer.toString(limit), Long.toString(window.toMillis()));
        return result != null && result == 1L;
    }

    @Override
    public void touchPresence(UUID tenantId, UUID roomId, UUID userId, Duration lease) {
        String key = namespace + "presence:" + tenantId + ":" + roomId + ":" + userId;
        commands.sync().setex(key, Math.max(1, lease.toSeconds()), "1");
    }

    @Override
    public boolean isReady() {
        try {
            return "PONG".equals(commands.sync().ping());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void close() {
        pubSub.close();
        commands.close();
        client.shutdown();
    }

    private String channel(UUID tenantId, UUID roomId) {
        return namespace + "events:" + tenantId + ":" + roomId;
    }

    private static String requireSafe(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Unsafe Redis namespace component");
        }
        return value;
    }
}
