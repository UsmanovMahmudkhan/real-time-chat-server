package io.github.usmanovmahmudkhan.realtimechat.config;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

public record ChatServerConfig(
        String environment,
        String jdbcUrl,
        String databaseUsername,
        String databasePassword,
        String redisUri,
        Set<URI> oidcIssuers,
        String oidcAudience,
        Set<String> globalAllowedOrigins,
        boolean allowMissingOrigin,
        boolean v1Enabled,
        int maximumMessageLength,
        int historyPageSize,
        int outboundQueueSize,
        int databasePoolSize
) {
    public static ChatServerConfig load() {
        return new ChatServerConfig(
                required("REALTIMECHAT_ENVIRONMENT"),
                required("REALTIMECHAT_JDBC_URL"),
                required("REALTIMECHAT_DATABASE_USERNAME"),
                required("REALTIMECHAT_DATABASE_PASSWORD"),
                required("REALTIMECHAT_REDIS_URI"),
                issuerUris(),
                required("REALTIMECHAT_OIDC_AUDIENCE"),
                csv("REALTIMECHAT_ALLOWED_ORIGINS"),
                bool("REALTIMECHAT_ALLOW_MISSING_ORIGIN", false),
                bool("REALTIMECHAT_V1_ENABLED", false),
                integer("REALTIMECHAT_MAX_MESSAGE_LENGTH", 1000, 1, 100_000),
                integer("REALTIMECHAT_HISTORY_PAGE_SIZE", 100, 1, 1000),
                integer("REALTIMECHAT_OUTBOUND_QUEUE_SIZE", 100, 1, 10_000),
                integer("REALTIMECHAT_DATABASE_POOL_SIZE", 20, 2, 200)
        );
    }

    private static String required(String name) {
        String value = value(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required configuration is missing: " + name);
        }
        return value.trim();
    }

    private static Set<String> csv(String name) {
        String value = value(name);
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isEmpty())
                .peek(ChatServerConfig::validateOrigin).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<URI> issuerUris() {
        String configured = value("REALTIMECHAT_OIDC_ISSUERS");
        if (configured == null || configured.isBlank()) {
            configured = required("REALTIMECHAT_OIDC_ISSUER");
        }
        Set<URI> issuers = new LinkedHashSet<>();
        for (String value : configured.split(",")) {
            URI issuer = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(issuer.getScheme()) || issuer.getHost() == null) {
                throw new IllegalStateException("OIDC issuers must use HTTPS");
            }
            issuers.add(issuer);
        }
        return Set.copyOf(issuers);
    }

    private static void validateOrigin(String origin) {
        URI uri = URI.create(origin);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())
                || origin.contains("*")) {
            throw new IllegalStateException("Allowed origins must be exact HTTPS origins: " + origin);
        }
    }

    private static boolean bool(String name, boolean fallback) {
        String value = value(name);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int integer(String name, int fallback, int minimum, int maximum) {
        String value = value(name);
        int parsed = value == null ? fallback : Integer.parseInt(value);
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalStateException(name + " must be between " + minimum + " and " + maximum);
        }
        return parsed;
    }

    private static String value(String name) {
        String property = System.getProperty(name.toLowerCase().replace('_', '.'));
        return property != null ? property : System.getenv(name);
    }
}
