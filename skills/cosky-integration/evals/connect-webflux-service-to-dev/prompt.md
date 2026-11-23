---
tags: [skill-creator]
max_turns: 30
timeout_seconds: 900
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit, Bash]
---

order-service 要接入我们 k8s dev 环境的 CoSky 8（server 的 Service 是 cosky.dev.svc.cluster.local），要读 order-service.yaml 这个 Config 而且改了能热刷新，还要按服务名调 payment-service（见 OrderController 里的 TODO）。CoSky 版本用 8.0.0-beta.9。帮我把项目改好，包括 k8s/deployment.yaml，再写个 NOTES.md 告诉我 token 要谁去建、怎么建、怎么给到服务。

Input files: build.gradle.kts, src/main/resources/application.yaml, src/main/kotlin/com/example/order/OrderController.kt, k8s/deployment.yaml
