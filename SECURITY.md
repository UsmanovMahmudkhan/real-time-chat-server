# Security Policy

## Reporting a Vulnerability

Report vulnerabilities privately through GitHub Security Advisories for the
project repository. Do not include credentials or sensitive production data.

## Supported Versions

Only the latest released version is supported with security fixes.

## Security Baseline

Version 2 requires OIDC, PostgreSQL, Redis, exact origin allowlists, tenant
RBAC, distributed rate limits, and audited management changes. Deployers remain
responsible for TLS, network policy, database/Redis hardening, secret
management, monitoring, backup, and identity-provider security.

Known residual risks and required verification are documented in
`THREAT_MODEL.md` and `ENTERPRISE_READINESS.md`.
