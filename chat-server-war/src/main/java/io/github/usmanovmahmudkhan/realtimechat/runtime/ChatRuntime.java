package io.github.usmanovmahmudkhan.realtimechat.runtime;

import io.github.usmanovmahmudkhan.realtimechat.config.ChatServerConfig;
import io.github.usmanovmahmudkhan.realtimechat.core.port.OriginRepository;
import io.github.usmanovmahmudkhan.realtimechat.core.service.AuthorizationService;
import io.github.usmanovmahmudkhan.realtimechat.core.service.MessageCommandService;
import io.github.usmanovmahmudkhan.realtimechat.postgres.*;
import io.github.usmanovmahmudkhan.realtimechat.redis.RedisCoordinationService;
import io.github.usmanovmahmudkhan.realtimechat.security.OidcAuthenticator;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.github.usmanovmahmudkhan.realtimechat.observability.StructuredLog;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChatRuntime implements AutoCloseable {
    private static final AtomicReference<ChatRuntime> INSTANCE = new AtomicReference<>();

    private final ChatServerConfig config;
    private final PostgresDatabase database;
    private final RedisCoordinationService coordination;
    private final OidcAuthenticator authenticator;
    private final OriginRepository origins;
    private final MessageCommandService messages;
    private final PrometheusMeterRegistry metrics;
    private final V2SessionManager sessions;
    private final JdbcOutboxPublisher outbox;
    private final JdbcRetentionService retention;
    private final JdbcManagementService management;
    private final ScheduledExecutorService jobs;

    private ChatRuntime(ChatServerConfig config) {
        this.config = config;
        this.database = new PostgresDatabase(config.jdbcUrl(), config.databaseUsername(),
                config.databasePassword(), config.databasePoolSize());
        database.migrate();
        var messageRepository = new JdbcMessageRepository(database.dataSource());
        var accessRepository = new JdbcAccessRepository(database.dataSource());
        var identities = new JdbcIdentityRepository(database.dataSource());
        this.origins = new JdbcOriginRepository(database.dataSource());
        this.coordination = new RedisCoordinationService(config.redisUri(), config.environment());
        this.authenticator = new OidcAuthenticator(config.oidcIssuers(), config.oidcAudience(), identities);
        this.messages = new MessageCommandService(messageRepository, accessRepository, coordination,
                new AuthorizationService(), config.maximumMessageLength(), config.historyPageSize());
        this.metrics = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        this.sessions = new V2SessionManager(messages, coordination, metrics, config.outboundQueueSize());
        this.outbox = new JdbcOutboxPublisher(database.dataSource(), coordination);
        this.retention = new JdbcRetentionService(database.dataSource());
        this.management = new JdbcManagementService(database.dataSource());
        AtomicInteger jobNumber = new AtomicInteger();
        this.jobs = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "realtimechat-job-" + jobNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        jobs.scheduleWithFixedDelay(() -> runSafely(() -> outbox.publishBatch(100)),
                1, 1, TimeUnit.SECONDS);
        jobs.scheduleWithFixedDelay(() -> runSafely(() -> retention.tombstoneExpiredMessages(500)),
                1, 60, TimeUnit.MINUTES);
        metrics.gauge("realtimechat_outbox_backlog", outbox, JdbcOutboxPublisher::backlogCount);
        StructuredLog.info("chat_runtime_started", java.util.Map.of("environment", config.environment()));
    }

    public static ChatRuntime start() {
        ChatRuntime runtime = new ChatRuntime(ChatServerConfig.load());
        if (!INSTANCE.compareAndSet(null, runtime)) {
            runtime.close();
            throw new IllegalStateException("Chat runtime already started");
        }
        return runtime;
    }

    public static ChatRuntime get() {
        ChatRuntime runtime = INSTANCE.get();
        if (runtime == null) {
            throw new IllegalStateException("Chat runtime is not started");
        }
        return runtime;
    }

    public ChatServerConfig config() { return config; }
    public OidcAuthenticator authenticator() { return authenticator; }
    public OriginRepository origins() { return origins; }
    public MessageCommandService messages() { return messages; }
    public JdbcManagementService management() { return management; }
    public PrometheusMeterRegistry metrics() { return metrics; }
    public V2SessionManager sessions() { return sessions; }
    public boolean ready() { return database.isReady() && coordination.isReady(); }

    @Override
    public void close() {
        INSTANCE.compareAndSet(this, null);
        jobs.shutdownNow();
        sessions.close();
        coordination.close();
        database.close();
        metrics.close();
        StructuredLog.info("chat_runtime_stopped", java.util.Map.of("environment", config.environment()));
    }

    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            StructuredLog.error("chat_background_job_failed", java.util.Map.of(), exception);
        }
    }
}
