# Enterprise Readiness

The repository contains an enterprise-oriented architecture and working
security/persistence mechanisms. It is not yet independently certified or
proven at the stated capacity.

## Required Release Gates

- Independent application-security and dependency review
- Documented threat-model review and remediation
- Successful PostgreSQL backup and point-in-time restore drill
- Redis outage, PostgreSQL failover, and rolling-deployment exercises
- Multi-node Tomcat integration test with authorization revocation
- Published load report proving 10,000 concurrent connections and 1,000
  accepted messages per second on documented hardware
- External penetration test
- SLO, alert, and incident-response approval
- No unapproved critical or high vulnerabilities
- Signed Maven artifacts, OCI image, SBOM, and provenance

Until every gate is complete, describe the project as an enterprise-oriented
foundation, not a corporation-ready certified product.
