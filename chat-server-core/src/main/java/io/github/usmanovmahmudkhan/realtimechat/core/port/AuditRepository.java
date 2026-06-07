package io.github.usmanovmahmudkhan.realtimechat.core.port;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.AuditEvent;

public interface AuditRepository {
    void append(AuditEvent event);
}
