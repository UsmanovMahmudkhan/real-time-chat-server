package io.github.usmanovmahmudkhan.realtimechat.http;

import io.github.usmanovmahmudkhan.realtimechat.runtime.ChatRuntime;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(urlPatterns = {"/health/live", "/health/ready"})
public final class HealthServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean readiness = request.getServletPath().endsWith("/ready");
        boolean healthy = !readiness || ChatRuntime.get().ready();
        response.setStatus(healthy ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json");
        response.getWriter().write(healthy ? "{\"status\":\"UP\"}" : "{\"status\":\"DOWN\"}");
    }
}
