package io.github.usmanovmahmudkhan.realtimechat.runtime;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public final class ChatRuntimeListener implements ServletContextListener {
    private ChatRuntime runtime;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        runtime = ChatRuntime.start();
        event.getServletContext().setAttribute(ChatRuntime.class.getName(), runtime);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (runtime != null) {
            runtime.close();
        }
    }
}
