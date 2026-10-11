# Security Policy

## Supported versions

Security fixes are released for the latest minor version of the current major version.

| Version | Supported |
|---|---|
| 6.x | ✅ |
| < 6.0 | ❌ — upgrade with the [6.0 upgrade guide](wiki/guide/upgrade-6.md) |

## Reporting a vulnerability

Please do **not** open a public issue. Report it privately through
[GitHub's private vulnerability reporting](https://github.com/Ahoo-Wang/CoSky/security/advisories/new)
(or by email to ahoowang@qq.com) with:

- the affected version and component (e.g. REST API server, Spring Cloud starter),
- a description of the vulnerability and its impact,
- steps or a proof of concept to reproduce it.

You will get an acknowledgement within 3 working days. Once the issue is confirmed, a fix is prepared in a private
fork and released as a patch version; the release notes credit the reporter unless they prefer otherwise.

## Hardening the REST API server

- Set a random `cosky.security.key` (`COSKY_SECURITY_KEY`) of at least 32 characters; the server refuses to start
  without one.
- Grant users only the namespaces they need through RBAC roles, and keep the `admin` role for administrators.
- Expose the server through TLS, and keep Redis on a private network with authentication enabled.
