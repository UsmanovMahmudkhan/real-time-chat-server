package io.github.usmanovmahmudkhan.realtimechat.websocket;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.AuthenticatedUser;
import io.github.usmanovmahmudkhan.realtimechat.runtime.ChatRuntime;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.net.URI;

public final class V2EndpointConfigurator extends ServerEndpointConfig.Configurator {
    public static final String AUTHENTICATED_USER = V2EndpointConfigurator.class.getName() + ".user";
    public static final String AUTHENTICATION_ERROR = V2EndpointConfigurator.class.getName() + ".error";

    @Override
    public boolean checkOrigin(String originHeaderValue) {
        ChatRuntime runtime = ChatRuntime.get();
        if (originHeaderValue == null) {
            return runtime.config().allowMissingOrigin();
        }
        try {
            URI origin = URI.create(originHeaderValue);
            return "https".equalsIgnoreCase(origin.getScheme()) && origin.getHost() != null
                    && !originHeaderValue.contains("*");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
        try {
            UUID tenantId = tenantFromPath(request.getRequestURI().getPath());
            String token = first(request.getHeaders(), "Authorization");
            String origin = first(request.getHeaders(), "Origin");
            ChatRuntime runtime = ChatRuntime.get();
            Set<String> tenantOrigins = runtime.origins().findAllowedOrigins(tenantId);
            if (origin != null && !tenantOrigins.contains(origin)
                    && !runtime.config().globalAllowedOrigins().contains(origin)) {
                throw new SecurityException("Origin is not allowed for tenant");
            }
            AuthenticatedUser user = runtime.authenticator().authenticate(token, tenantId);
            config.getUserProperties().put(AUTHENTICATED_USER, user);
        } catch (RuntimeException exception) {
            config.getUserProperties().put(AUTHENTICATION_ERROR, exception.getMessage());
        }
    }

    private static UUID tenantFromPath(String path) {
        String[] parts = path.split("/");
        for (int index = 0; index < parts.length - 1; index++) {
            if ("tenants".equals(parts[index])) {
                return UUID.fromString(parts[index + 1]);
            }
        }
        throw new IllegalArgumentException("Tenant path is invalid");
    }

    private static String first(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream()).findFirst().orElse(null);
    }
}
