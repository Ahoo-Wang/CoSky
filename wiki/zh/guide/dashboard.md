---
title: 控制台
description: Web 控制台能做什么、如何登录，以及它如何保持最新。
---

# 控制台

server 在 8080 端口的 `/` 提供 Dashboard。它只使用公开的 [REST API](./rest-api.md)，所以能做的事情 `curl` 都能做，你的[角色](./security.md#谁能做什么)同样适用。操作被拒绝时会显示 server 给出的原因。

![Dashboard](/dashboard-dashboard.png)

## 登录

粘贴 ServiceAccount token，或者在 server [提供 OIDC](./security.md#oidc) 时点击 OIDC 按钮。token 保存在当前浏览器标签页中，关闭标签页就消失。`cosky.auth.enabled: false` 的 server 不要求登录。

## 页面

页头的 namespace 选择器决定每个页面显示的内容。

| 页面 | 作用 |
|---|---|
| **Dashboard** | 服务、实例、Config 和 namespace 的数量，以及谁 watch 谁的拓扑图 |
| **Configuration** | 创建、编辑、删除、导入和导出 Config；把任意 revision 与当前版本比较，并回滚 |
| **Service** | 服务及其实例，已挂租约或固定；添加、编辑和删除实例 |
| **Namespace** | 创建和删除 namespace。只有空的才能删除，当前选中的也不能删除。 |
| **Service Account** | 账号及其 token。新 token 只显示一次，请当时复制。 |
| **Role** 和 **Role Binding** | 谁能做什么，在哪里 |

![Configuration](/dashboard-config.png)

## 保持最新

Configuration 和 Service 页面通过 server-sent events 流实时跟随变化，不需要刷新页面。流断开或长时间静默时会重新打开。首页每 30 秒读取一次数量，不单独占用连接：通过 HTTP/1.1 时，浏览器对每个 server 允许的连接很少。

## 安全地编辑

编辑会记住开始时的 revision。如果此间有人改动了这个对象，保存会失败并显示 server 给出的原因，不会有任何改动，重新加载后再试即可。
