package io.github.usmanovmahmudkhan.realtimechat.http;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.AuthenticatedUser;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ErrorCode;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ProtocolException;
import io.github.usmanovmahmudkhan.realtimechat.core.util.UuidV7;
import io.github.usmanovmahmudkhan.realtimechat.observability.StructuredLog;
import io.github.usmanovmahmudkhan.realtimechat.runtime.ChatRuntime;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.RoomVisibility;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.TenantRole;

@WebServlet("/api/v2/*")
public final class ManagementApiServlet extends HttpServlet {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String correlationId = correlationId(request);
        try {
            UUID tenantId = UUID.fromString(requiredParameter(request, "tenantId"));
            AuthenticatedUser user = authenticate(request, tenantId);
            if ("/me".equals(request.getPathInfo())) {
                writeJson(response, HttpServletResponse.SC_OK, user);
                return;
            }
            if ("/messages".equals(request.getPathInfo())) {
                UUID roomId = UUID.fromString(requiredParameter(request, "roomId"));
                String after = request.getParameter("afterMessageId");
                writeJson(response, HttpServletResponse.SC_OK,
                        ChatRuntime.get().messages().resume(user, tenantId, roomId,
                                after == null || after.isBlank() ? null : UUID.fromString(after)));
                return;
            }
            problem(response, HttpServletResponse.SC_NOT_FOUND, "not-found", "Resource not found", correlationId);
        } catch (ProtocolException exception) {
            problem(response, statusFor(exception.code()), exception.code().name(),
                    exception.getMessage(), correlationId);
        } catch (IllegalArgumentException exception) {
            problem(response, HttpServletResponse.SC_BAD_REQUEST, "invalid-request",
                    "Request parameters are invalid", correlationId);
        } catch (RuntimeException exception) {
            StructuredLog.error("management_request_failed", Map.of("correlationId", correlationId), exception);
            problem(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "internal-error",
                    "The request could not be processed", correlationId);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String correlationId = correlationId(request);
        try {
            UUID tenantId = UUID.fromString(requiredParameter(request, "tenantId"));
            AuthenticatedUser user = authenticate(request, tenantId);
            var body = mapper.readTree(request.getInputStream());
            Object result;
            switch (request.getPathInfo()) {
                case "/rooms" -> result = Map.of("roomId", ChatRuntime.get().management().createRoom(user, tenantId,
                        body.required("name").asText(), RoomVisibility.valueOf(body.required("visibility").asText()),
                        enumSet(body, "requiredRoles"), correlationId));
                case "/tenant-memberships" -> {
                    ChatRuntime.get().management().upsertTenantMembership(user, tenantId,
                            UUID.fromString(body.required("userId").asText()), enumSet(body, "roles"), correlationId);
                    result = Map.of("status", "updated");
                }
                case "/room-memberships" -> {
                    ChatRuntime.get().management().upsertRoomMembership(user, tenantId,
                            UUID.fromString(body.required("roomId").asText()),
                            UUID.fromString(body.required("userId").asText()), correlationId);
                    result = Map.of("status", "updated");
                }
                case "/retention-policy" -> {
                    ChatRuntime.get().management().setRetentionPolicy(user, tenantId,
                            body.required("retentionDays").asInt(), correlationId);
                    result = Map.of("status", "updated");
                }
                case "/legal-holds" -> result = Map.of("holdId", ChatRuntime.get().management().createLegalHold(
                        user, tenantId, optionalUuid(body.path("roomId").asText(null)),
                        body.required("reason").asText(), correlationId));
                case "/message-tombstones" -> {
                    ChatRuntime.get().management().tombstoneMessage(user, tenantId,
                            UUID.fromString(body.required("messageId").asText()), correlationId);
                    result = Map.of("status", "tombstoned");
                }
                default -> {
                    problem(response, HttpServletResponse.SC_NOT_FOUND, "not-found",
                            "Resource not found", correlationId);
                    return;
                }
            }
            writeJson(response, HttpServletResponse.SC_OK, result);
        } catch (ProtocolException exception) {
            problem(response, statusFor(exception.code()), exception.code().name(),
                    exception.getMessage(), correlationId);
        } catch (IllegalArgumentException | NullPointerException | JacksonException exception) {
            problem(response, HttpServletResponse.SC_BAD_REQUEST, "invalid-request",
                    "Request body is invalid", correlationId);
        } catch (Exception exception) {
            StructuredLog.error("management_request_failed", Map.of("correlationId", correlationId), exception);
            problem(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "internal-error",
                    "The request could not be processed", correlationId);
        }
    }

    static int statusFor(ErrorCode code) {
        return switch (code) {
            case UNAUTHENTICATED -> HttpServletResponse.SC_UNAUTHORIZED;
            case UNAUTHORIZED, TENANT_MISMATCH -> HttpServletResponse.SC_FORBIDDEN;
            case RATE_LIMITED -> 429;
            case INVALID_EVENT, INVALID_MESSAGE -> HttpServletResponse.SC_BAD_REQUEST;
            case DUPLICATE_REQUEST -> HttpServletResponse.SC_CONFLICT;
            case SERVICE_UNAVAILABLE -> HttpServletResponse.SC_SERVICE_UNAVAILABLE;
            case INTERNAL_ERROR -> HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        };
    }

    private AuthenticatedUser authenticate(HttpServletRequest request, UUID tenantId) {
        AuthenticatedUser user = ChatRuntime.get().authenticator().authenticate(request.getHeader("Authorization"), tenantId);
        if (!ChatRuntime.get().sessions().allowRestRequest(user)) {
            throw new ProtocolException(ErrorCode.RATE_LIMITED, "REST rate limit exceeded");
        }
        return user;
    }

    private String requiredParameter(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing parameter");
        }
        return value;
    }

    private String correlationId(HttpServletRequest request) {
        String provided = request.getHeader("X-Correlation-ID");
        return provided == null || provided.isBlank() ? UuidV7.next().toString() : provided;
    }

    private void problem(HttpServletResponse response, int status, String type, String detail,
                         String correlationId) throws IOException {
        response.setContentType("application/problem+json");
        writeJson(response, status, Map.of(
                "type", URI.create("https://github.com/usmanovmahmudkhan/real-time-chat-server/problems/" + type),
                "title", type,
                "status", status,
                "detail", detail,
                "correlationId", correlationId
        ));
    }

    private void writeJson(HttpServletResponse response, int status, Object value) throws IOException {
        response.setStatus(status);
        if (response.getContentType() == null) {
            response.setContentType("application/json");
        }
        mapper.writeValue(response.getWriter(), value);
    }

    private Set<TenantRole> enumSet(com.fasterxml.jackson.databind.JsonNode body, String field) {
        if (!body.has(field)) {
            return Set.of();
        }
        return java.util.stream.StreamSupport.stream(body.get(field).spliterator(), false)
                .map(node -> TenantRole.valueOf(node.asText())).collect(Collectors.toUnmodifiableSet());
    }

    private UUID optionalUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
