# Security Policy

## Supported versions

Security fixes are released for the latest minor version of the current major version.

| Version | Supported |
|---|---|
| 8.x | ✅ |
| 6.x | ✅ on the `v6` branch, which takes bug fixes only — move with the [guide](wiki/guide/upgrade-8.md) |
| < 6.0 | ❌ |

## Reporting a vulnerability

Please do **not** open a public issue. Report it privately through
[GitHub's private vulnerability reporting](https://github.com/Ahoo-Wang/CoSky/security/advisories/new)
(or by email to ahoowang@qq.com) with:

- the affected version and component (e.g. server, Spring Boot starter),
- a description of the vulnerability and its impact,
- steps or a proof of concept to reproduce it.

You will get an acknowledgement within 3 working days. Once the issue is confirmed, a fix is prepared in a private
fork and released as a patch version; the release notes credit the reporter unless they prefer otherwise.

## Hardening the server

- Turn on [TLS](wiki/guide/security.md#tls) on both ports, and in the clients: without it, tokens cross the network in the clear.
- Revoke the bootstrap token once real accounts exist, and grant each subject only what it needs through roles.
- Keep Redis on a private network with authentication enabled: whoever reaches it can read and change everything.
- Never run with `cosky.auth.enabled: false` or `--dev` outside development.
