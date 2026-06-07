# Changelog

All notable changes to this project will be documented in this file.

## 2.0.0-SNAPSHOT - 2026-06-07

- Converted the project to a multi-module enterprise architecture.
- Added OIDC/JWT authentication with multiple approved issuers.
- Added tenant RBAC, room authorization, PostgreSQL persistence, durable
  outbox/replay, Redis coordination, distributed limits, retention, legal
  holds, audited management APIs, metrics, OCI packaging, and Helm deployment.
- Kept v1 demo endpoint disabled by default.

## 1.0.0 - 2026-06-07

- Added Jakarta WebSocket room-based chat endpoint.
- Added thread-safe in-memory room and session management.
- Added Jackson JSON protocol, validation, and safe browser test client.
- Added unit tests and Maven Central publishing configuration.
