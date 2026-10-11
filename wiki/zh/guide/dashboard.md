---
title: 控制台
description: REST API 服务器提供的 CoSky Web 控制台导览。
---

# 控制台

REST API 服务器在 `http://<服务器>:8080` 提供 Web 控制台。用 CoSky 用户登录（见[安全](./security.md)）；每个页面都作用于顶部选中的命名空间，并且只显示你的角色授权可见的内容。

| 页面 | 用途 |
|---|---|
| **Home** | 命名空间、配置、服务和实例的数量，以及服务拓扑 |
| **Config** | 创建、编辑、删除配置；浏览带差异对比的历史并回滚；导入导出 zip |
| **Service** | 浏览服务与实例，注册固定实例，编辑元数据，注销实例 |
| **Namespace** | 添加和删除命名空间 |
| **User** | 创建用户、绑定角色、锁定、解锁、修改密码 |
| **Role** | 定义角色及其授权的命名空间 |
| **Audit Log** | 查询谁做了什么，按时间和结果筛选，导出 CSV |

![Home](/dashboard-dashboard.png)

## 配置

![配置](/dashboard-config.png)

保存配置会产生新版本，所有使用它的应用在毫秒内收到。历史记录可以把任意版本与当前内容对比并回滚；见[配置管理](./configuration.md#版本与回滚)。

![回滚](/dashboard-config-rollback.png)

## 服务

![服务](/dashboard-service.png)

应用注册的实例会自动出现和消失。在这里添加的固定实例会一直保留，直到被删除；见[服务发现](./service-discovery.md)。

![拓扑](/dashboard-topology.png)

## 用户、角色与审计

![角色](/dashboard-role.png)

![审计日志](/dashboard-audit-log.png)

## 开发控制台

控制台是 [`dashboard/`](https://github.com/Ahoo-Wang/CoSky/tree/main/dashboard) 下的 React 应用；其 README 介绍了开发服务器、测试以及 API 客户端生成。
