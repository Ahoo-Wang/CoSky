---
tags: [skill-creator]
max_turns: 20
timeout_seconds: 600
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit, Bash]
---

staging 的 inventory-service 起不来。为了省事我们让它直接挂了 dev 用的那个 Secret cosky-dev（dev 环境的服务都用它，没问题），配置里写的是 cosky.namespace: staging、cosky.token-file: /etc/cosky/token。启动日志：

```
2026-11-23T10:12:41.207+08:00 ERROR 1 --- [inventory-service] [main] o.s.boot.SpringApplication : Application run failed
org.springframework.boot.context.config.ConfigDataResourceNotFoundException ... 
Caused by: me.ahoo.cosky.api.error.CoSkyException: serviceaccount:dev may not watch CONFIG in namespace staging.
```

为什么？怎么改最省事又不留隐患？把结论和要执行的步骤写到 DIAGNOSIS.md。
