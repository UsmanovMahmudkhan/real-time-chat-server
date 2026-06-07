# Threat Model

## Assets

- Tenant isolation and role assignments
- Access tokens and external identity mappings
- Message content, history, audit events, and legal holds
- Availability of live messaging and management operations

## Trust Boundaries

- Client to load balancer/Tomcat
- Tomcat to OIDC provider
- Tomcat to PostgreSQL
- Tomcat to Redis
- Operator access to Kubernetes and secrets

## Primary Threats and Controls

| Threat | Control |
| --- | --- |
| Token forgery or issuer confusion | Explicit issuer/audience allowlist, discovery, JWKS verification, required claims |
| Cross-tenant access | Tenant-qualified repository queries, centralized RBAC, PostgreSQL RLS policies |
| Browser cross-site WebSocket abuse | Exact HTTPS global/tenant origin allowlists |
| Replay and duplicate sends | Required idempotency keys and durable uniqueness constraints |
| Flooding and slow consumers | Distributed rate limits, bounded queues, protocol violation closure |
| Redis loss | PostgreSQL/outbox remains authoritative and retries publication |
| Audit tampering | Append-only audit trigger and restricted database privileges |
| Sensitive log exposure | Tokens and message content must never be logged |

## Residual Risks

- Infrastructure administrators can access plaintext unless database-level
  encryption and operational access controls are configured.
- Single-region operation does not protect against complete regional loss.
- Browser clients cannot set WebSocket Authorization headers directly and need
  a trusted authentication gateway.
- Authorization revocation currently affects new requests immediately but
  requires an additional cross-node disconnect command for already-open
  sessions.
