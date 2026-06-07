package io.github.usmanovmahmudkhan.realtimechat.http;

import io.github.usmanovmahmudkhan.realtimechat.runtime.ChatRuntime;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/metrics")
public final class MetricsServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/plain; version=0.0.4; charset=utf-8");
        response.getWriter().write(ChatRuntime.get().metrics().scrape());
    }
}
