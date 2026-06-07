# Operations

## Production Baseline

- Terminate TLS at a trusted load balancer or Tomcat.
- Use TLS and authentication for PostgreSQL and Redis.
- Give the application database role only required privileges.
- Store secrets in a managed secret store.
- Restrict `/metrics` and health endpoints to trusted networks.
- Use at least three application replicas and a highly available PostgreSQL
  deployment with tested backups.

## Backup and Recovery

- Enable PostgreSQL point-in-time recovery and encrypted backups.
- Back up before schema migration.
- Test restore into an isolated environment at least quarterly.
- Redis is not a source of truth and is restored by rebuilding ephemeral state.

## Alerts

Alert on readiness failures, authentication spikes, tenant authorization
failures, outbox backlog growth, PostgreSQL/Redis latency, slow-consumer
disconnects, and unusual message-rate-limit activity.

## Incident Response

1. Preserve audit logs and correlation IDs.
2. Revoke affected OIDC clients or identities.
3. Rotate database, Redis, and deployment credentials.
4. Isolate affected tenants or nodes.
5. Restore from verified backups if durable data was affected.
6. Document impact, timeline, remediation, and follow-up controls.
