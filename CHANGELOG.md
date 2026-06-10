# Changelog

All notable changes to this project will be documented in this file.

## 2.1.1 - 2026-06-10

- Pinned Netty to 4.2.15.Final (above the version managed by Lettuce) to fix
  CVE-2026-44249, CVE-2026-45416, CVE-2026-45674, and CVE-2026-47691 in the
  transitive `netty-handler` and `netty-resolver-dns` dependencies.

## 2.1.0 - 2026-06-10

- Management API now returns accurate HTTP statuses: 401 for authentication
  failures, 429 for rate limits, 409 for duplicate requests, 503/500 for
  server-side failures (previously every protocol error returned 403, and
  internal failures were mislabeled as 400). Documented the error contract in
  `docs/openapi.yaml`.
- Internal management API failures are now logged with the correlation id
  instead of being silently reported as client errors.
- Configuration loading rejects non-numeric integer settings with a clear
  message instead of an unexplained `NumberFormatException`.
- Added `issueManagement` to the parent POM so Maven Central links to the
  GitHub issue tracker.
- The Central publisher now excludes `chat-server-integration-tests` from the
  upload bundle via `excludeArtifacts`; the module also skips GitHub Packages
  deployment.
- README gained Maven Central badges, installation snippets, a published
  artifact matrix, and contribution/support links.
- Added unit tests for the WebSocket handshake origin validation and tenant
  path extraction, and for the management API status mapping.

## 2.0.0 - 2026-06-07

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
