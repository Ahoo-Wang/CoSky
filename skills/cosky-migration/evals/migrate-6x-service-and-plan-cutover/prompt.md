---
tags: [skill-creator]
max_turns: 30
timeout_seconds: 900
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit, Bash]
---

dev 环境要从 CoSky 6.x 升到 8.0。新的 8.0 server 已经部署在 k8s 里了（Service cosky.dev.svc.cluster.local，80 是 REST，9090 是 gRPC），用的是旧的那个 Redis Cluster（redis-dev-redis-cluster.dev.svc.cluster.local:6379，有密码）。先帮我把 order-service 改到 8.0（版本 8.0.0-beta.9），再写一份 MIGRATION.md：Config 怎么从旧 Redis 迁过来（给出要跑的命令），以及 order-service 和它调用的 payment-service、inventory-service 该怎么切换、出问题怎么回退。

Input files: build.gradle.kts, src/main/resources/bootstrap.yaml, src/main/resources/application.yaml
