---
type: llm
focus: trace
weight: 1
---

The token reaches the application from a Kubernetes Secret (cosky.token-file pointing into a Secret volume mounted without subPath, or cosky.token from a secretKeyRef environment variable), and no file holds a token value
