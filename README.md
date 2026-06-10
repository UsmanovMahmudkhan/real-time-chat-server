# Real-Time Chat Server

[![Maven Central](https://img.shields.io/maven-central/v/io.github.usmanovmahmudkhan/chat-server-core)](https://central.sonatype.com/namespace/io.github.usmanovmahmudkhan)
[![Build](https://github.com/UsmanovMahmudkhan/real-time-chat-server/actions/workflows/verify.yml/badge.svg)](https://github.com/UsmanovMahmudkhan/real-time-chat-server/actions/workflows/verify.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](pom.xml)

An open-source, multi-tenant Jakarta WebSocket chat platform packaged as a
classic Tomcat WAR. Version 2 adds OIDC authentication, tenant-scoped RBAC,
PostgreSQL persistence, Redis coordination, durable replay, distributed rate
limits, operational endpoints, and Kubernetes packaging.

The project is security-conscious, but it must not be represented as
corporation-ready until the release gates in [ENTERPRISE_READINESS.md](ENTERPRISE_READINESS.md)
have been independently verified.

## Installation

The library modules are published to
[Maven Central](https://central.sonatype.com/namespace/io.github.usmanovmahmudkhan)
under the `io.github.usmanovmahmudkhan` namespace and require Java 17+.

Maven:

```xml
<dependency>
    <groupId>io.github.usmanovmahmudkhan</groupId>
    <artifactId>chat-server-core</artifactId>
    <version>2.1.0</version>
</dependency>
```

Gradle:

```gradle
implementation 'io.github.usmanovmahmudkhan:chat-server-core:2.1.0'
```

Published artifacts:

| Artifact | Use it when you need |
| --- | --- |
| `chat-server-core` | Protocol, domain model, RBAC, and infrastructure ports |
| `chat-server-postgres` | Flyway schema and JDBC persistence adapters |
| `chat-server-redis` | Redis fan-out, presence, and distributed rate limits |
| `real-time-chat-server` (`war`) | The deployable Tomcat application |

The `chat-server-integration-tests` module is a Testcontainers harness and is
intentionally not published.

## Architecture

```text
OIDC provider
     |
Load balancer
     |
Tomcat nodes (stateless WAR)
     |                 |
PostgreSQL          Redis
durable truth       live fan-out, presence, limits
```

Modules:

- `chat-server-core`: protocol, domain, RBAC, and infrastructure ports
- `chat-server-postgres`: Flyway schema and JDBC persistence
- `chat-server-redis`: distributed fan-out, presence, and rate limits
- `chat-server-war`: WebSocket, REST, security, metrics, and lifecycle
- `chat-server-integration-tests`: Testcontainers verification

## Security Model

- OIDC access tokens are verified using discovery metadata and cached JWKS.
- Multiple approved issuers are supported.
- User identity and tenant roles are resolved from PostgreSQL, not trusted from
  URL parameters or mutable token roles.
- Tenant, room, and role authorization is centralized.
- Browser WebSocket origins must exactly match a configured HTTPS origin.
- Messages are persisted transactionally before cross-node publication.
- Administrative mutations create append-only audit records.
- Rate limits use Redis atomic scripts.
- The v1 anonymous endpoint is disabled by default.

See [SECURITY.md](SECURITY.md) and [THREAT_MODEL.md](THREAT_MODEL.md).

## Requirements

- JDK 17+
- Maven 3.9+
- Tomcat 10.1+
- PostgreSQL 15+
- Redis 7+
- OIDC provider with RS256 access tokens

## Build and Test

```bash
mvn clean test
mvn clean verify
mvn clean package
```

Docker-backed integration tests automatically skip when Docker is unavailable.
The WAR is generated at:

```text
chat-server-war/target/real-time-chat-server.war
```

## Required Configuration

Secrets must be provided by the deployment platform.

| Environment variable | Purpose |
| --- | --- |
| `REALTIMECHAT_ENVIRONMENT` | Safe Redis namespace such as `production` |
| `REALTIMECHAT_JDBC_URL` | PostgreSQL JDBC URL |
| `REALTIMECHAT_DATABASE_USERNAME` | PostgreSQL username |
| `REALTIMECHAT_DATABASE_PASSWORD` | PostgreSQL password |
| `REALTIMECHAT_REDIS_URI` | Redis URI, preferably TLS-enabled |
| `REALTIMECHAT_OIDC_ISSUERS` | Comma-separated approved HTTPS issuers |
| `REALTIMECHAT_OIDC_AUDIENCE` | Required access-token audience |
| `REALTIMECHAT_ALLOWED_ORIGINS` | Comma-separated exact HTTPS origins |

Optional configuration:

| Environment variable | Default |
| --- | --- |
| `REALTIMECHAT_ALLOW_MISSING_ORIGIN` | `false` |
| `REALTIMECHAT_V1_ENABLED` | `false` |
| `REALTIMECHAT_MAX_MESSAGE_LENGTH` | `1000` |
| `REALTIMECHAT_HISTORY_PAGE_SIZE` | `100` |
| `REALTIMECHAT_OUTBOUND_QUEUE_SIZE` | `100` |
| `REALTIMECHAT_DATABASE_POOL_SIZE` | `20` |

Equivalent lower-case dotted JVM system properties override environment
variables, for example `-Drealtimechat.jdbc.url=...`.

## WebSocket v2

```text
wss://chat.example.com/v2/tenants/{tenantId}/rooms/{roomId}/chat
Authorization: Bearer <access-token>
Origin: https://approved-client.example.com
```

Clients that cannot set an `Authorization` header must connect through a
trusted gateway that authenticates the client and forwards the header. Tokens
are never accepted in query parameters.

Client chat event:

```json
{
  "protocolVersion": 2,
  "type": "CHAT",
  "correlationId": "request-123",
  "idempotencyKey": "018f6b75-9200-7d00-a000-000000000001",
  "content": "Hello"
}
```

Reconnect/replay event:

```json
{
  "protocolVersion": 2,
  "type": "RESUME",
  "correlationId": "request-124",
  "afterMessageId": "018f6b75-9200-7d00-a000-000000000001"
}
```

Delivery is at least once. Clients must deduplicate using `eventId` or
`message.messageId`.

## Management API

All endpoints require `Authorization: Bearer ...` and `tenantId` query
parameter. Responses use JSON; errors use RFC 9457-style problem details.

Read endpoints:

- `GET /api/v2/me`
- `GET /api/v2/messages?tenantId=...&roomId=...&afterMessageId=...`

Audited mutation endpoints:

- `POST /api/v2/rooms`
- `POST /api/v2/tenant-memberships`
- `POST /api/v2/room-memberships`
- `POST /api/v2/retention-policy`
- `POST /api/v2/legal-holds`
- `POST /api/v2/message-tombstones`

See [docs/openapi.yaml](docs/openapi.yaml).

## Operations

- `GET /health/live`: process liveness
- `GET /health/ready`: PostgreSQL and Redis readiness
- `GET /metrics`: Prometheus metrics

Flyway migrations run before readiness. Background workers retry unpublished
outbox events and enforce retention policies while respecting legal holds.

See [OPERATIONS.md](OPERATIONS.md) for backup, recovery, scaling, and incident
response expectations.

## Deployment

Traditional Tomcat:

```bash
cp chat-server-war/target/real-time-chat-server.war "$CATALINA_BASE/webapps/"
```

Container:

```bash
docker build -t real-time-chat-server:2.1.0 .
```

Kubernetes:

```bash
helm upgrade --install chat ./helm --values production-values.yaml
```

The Helm chart references an existing secret. PostgreSQL, Redis, and the OIDC
provider are never bundled into production packaging.

## Releasing to Maven Central

Version 2.1.0 is live on
[Maven Central](https://central.sonatype.com/namespace/io.github.usmanovmahmudkhan)
and resolvable from
[repo1.maven.org](https://repo1.maven.org/maven2/io/github/usmanovmahmudkhan/).
The parent POM carries the Central metadata (SCM, issue management, license,
developers), source/javadoc attachment, SBOM generation, and GPG signing behind
the `release` profile. The integration-test module is excluded from the Central
bundle via the publisher plugin's `excludeArtifacts`.

The full release procedure — including the failure modes the first release hit
(reactor module exclusion, GPG subkey/keyserver mismatch, Docker-dependent
tests) — is documented in
[docs/publishing-to-maven-central.md](docs/publishing-to-maven-central.md).
Releases are staged with `autoPublish=false` and published manually from the
Central Portal. Remember to bump the version before the next release; published
versions are immutable.

## Contributing and Support

- Bugs and feature requests: [GitHub Issues](https://github.com/UsmanovMahmudkhan/real-time-chat-server/issues)
- Contribution policy: [CONTRIBUTING.md](CONTRIBUTING.md)
- Vulnerability reports: [SECURITY.md](SECURITY.md) (use private security advisories)
- Release notes: [CHANGELOG.md](CHANGELOG.md)

## License

[Apache License 2.0](LICENSE). No source from the audited reference
repositories was copied.
