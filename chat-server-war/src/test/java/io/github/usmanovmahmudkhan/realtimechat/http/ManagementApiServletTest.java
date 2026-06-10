package io.github.usmanovmahmudkhan.realtimechat.http;

import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManagementApiServletTest {

    @Test
    void mapsAuthenticationFailuresToUnauthorizedStatus() {
        assertEquals(401, ManagementApiServlet.statusFor(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void mapsPermissionFailuresToForbiddenStatus() {
        assertEquals(403, ManagementApiServlet.statusFor(ErrorCode.UNAUTHORIZED));
        assertEquals(403, ManagementApiServlet.statusFor(ErrorCode.TENANT_MISMATCH));
    }

    @Test
    void mapsRateLimitToTooManyRequestsStatus() {
        assertEquals(429, ManagementApiServlet.statusFor(ErrorCode.RATE_LIMITED));
    }

    @Test
    void mapsValidationFailuresToBadRequestStatus() {
        assertEquals(400, ManagementApiServlet.statusFor(ErrorCode.INVALID_EVENT));
        assertEquals(400, ManagementApiServlet.statusFor(ErrorCode.INVALID_MESSAGE));
    }

    @Test
    void mapsServerSideFailuresToServerStatuses() {
        assertEquals(409, ManagementApiServlet.statusFor(ErrorCode.DUPLICATE_REQUEST));
        assertEquals(503, ManagementApiServlet.statusFor(ErrorCode.SERVICE_UNAVAILABLE));
        assertEquals(500, ManagementApiServlet.statusFor(ErrorCode.INTERNAL_ERROR));
    }
}
