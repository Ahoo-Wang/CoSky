---
type: llm
focus: trace
weight: 1
---

MIGRATION.md runs cosky-migrate with --cluster, first with --dry-run and then to import, against the REST address as --target, with the Redis password in COSKY_SOURCE_URI rather than on the command line and an admin token in COSKY_TOKEN
