---
title: 升级
description: 从一个 CoSky 8.x 版本升级到另一个，哪些保持兼容，以及按什么顺序升级 server 和应用。
---

# 升级

本页面向运行 CoSky 8.x、要升级到更新的 8.x 版本的人。从 6.x 迁移时，由于它和 8.0 没有任何共享，请看[从 6.x 迁移](./upgrade-8.md)。

## 哪些保持兼容

从 `8.0.0-rc.1` 起，以下内容只增不减：

- gRPC API，包括错误原因；
- REST；
- `cosky-api`、`cosky-client` 和 `cosky-spring-boot-starter` 的 Kotlin API；
- server 和 starter 的属性；
- [部署](./deployment.md#可观测性)中列出的指标，以及它们的标签；
- Redis 中的数据。

可以增加字段、调用、属性或指标，但不会删除、重新编号、改名或改变含义。CI 在每个 PR 上按这条规则检查 proto、Kotlin API、REST、属性和指标。`cosky-api` 中标为 `@InternalCoSkyApi` 的部分，以及其他模块的 Kotlin API，任何版本都可能改变。新的主版本，比如 9.0，可以改变任何东西，并附带升级指南。完整规则见 [`CONTRIBUTING.md`](https://github.com/Ahoo-Wang/CoSky/blob/main/CONTRIBUTING.md#releases-and-versioning)。

alpha 和 beta 没有这样的承诺。每个版本的说明都写明了怎样从上一个版本升级。

这个承诺是单向的：较新的 server 能读较旧的 server 写下的数据，也能服务较旧的应用。退回较旧的版本不在其中，所以请先读要离开的那个版本的说明。

## 升级顺序

1. **阅读发布说明**，从你的版本到新版本之间的每一个，见[发布页](https://github.com/Ahoo-Wang/CoSky/releases)。其中的 “Upgrading from …” 一节说明要做什么，“Known limits” 说明会遇到什么。
2. **升级 server**，一次一个副本。Dashboard 随 server 一起升级。
3. **然后升级应用**，按各自的节奏：提高 `cosky-bom` 的版本，照常构建和部署。

滚动更新期间，两个版本的副本共用 Redis，同时为客户端服务。在 Kubernetes 中，设置新的标签，比如 overlay 的 `kustomization.yaml` 中的 `newTag`，然后应用它：Deployment 一次替换一个 Pod，客户端重连到其他副本，并从原来的位置继续，详见[滚动更新](./deployment.md#滚动更新)。使用 Docker 时，一次替换一个容器，等新容器就绪后再换下一个。

更新期间，`cosky_watch_gone_total` 显示有多少客户端没能续传、读取了新快照；它上升时怎么办，见[故障排查](./troubleshooting.md#cosky-watch-gone-total-上升)。

## 从 8.0 beta 升级到 rc.1

rc.1 确定了这个承诺所保持的内容，所以它的改动比一个 beta 多。Redis 中的数据没有变化：原地升级 server，然后升级应用，在同一个时间窗口内完成。beta 应用与 rc.1 server，或者反过来，对“位置已失效的 watch 怎样结束”的表达互不认识，这样的 watch 只有在应用重启后才会重新开始。

要检查的内容，简要如下：

- **bootstrap token**：格式不对，或 secret 少于 22 个字符时，server 拒绝启动。
- **设置**：server 拒绝以不能工作的设置启动；不带单位的时长现在按秒计算，而不是毫秒。
- **访问管理**：写 Role、RoleBinding 或 token 的账号必须持有它授予的权限。
- **starter 的健康指示器**现在叫 `cosky`，由 `management.health.cosky.enabled` 关闭。
- **告警和看板**：`cosky_auth_failures_total{reason}` 的新取值，`cosky_lease_active` 要取 `max by (namespace)`，`cosky_lease_expired_total` 有了 `namespace` 标签。
- **Kubernetes 清单**把 Actuator 放在 8081 端口，所以请在那里抓取 Pod；内存请求为 2Gi。
- **镜像**以 `999:999` 运行，`JAVA_OPTS` 追加到它的 JVM 参数之后，而不是替换它们。
- **你自己的 REST 和 gRPC 客户端**：更严格的请求、分页的视图，以及 watch 结束的新方式。
- **OIDC**：注册带结尾斜杠的 Dashboard 地址。

每一项的细节见 [rc.1 的发布说明](https://github.com/Ahoo-Wang/CoSky/releases/tag/v8.0.0-rc.1)。从更早的 beta 升级时，请依次阅读每个版本的说明：从 beta.1 起，Redis 中的数据都能沿用。
