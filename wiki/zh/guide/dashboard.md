---
title: 控制台
description: Web 控制台能做什么、如何登录、它如何保持最新，以及如何在反向代理后面把它放在某个路径下。
---

# 控制台

server 在 8080 端口的 `/` 提供 Dashboard，反向代理也可以把它放在自己的路径下（见[在反向代理后面](#在反向代理后面)）。它只使用公开的 [REST API](./rest-api.md)，所以能做的事情 `curl` 都能做，你的[角色](./security.md#谁能做什么)同样适用。操作被拒绝时会显示 server 给出的原因。

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

列表每页显示 10 行。列表下方的 **Rows per page** 可以改为每页 20、50 或 100 行，浏览器会为每个列表分别记住这一选择。搜索覆盖所有页。

![Configuration](/dashboard-config.png)

## 保持最新

Configuration 和 Service 页面通过 server-sent events 流实时跟随变化，不需要刷新页面。流断开或长时间静默时会重新打开。首页每 30 秒读取一次数量，不单独占用连接：通过 HTTP/1.1 时，浏览器对每个 server 允许的连接很少。

## 安全地编辑

编辑会记住开始时的 revision。如果此间有人改动了这个对象，保存会失败并显示 server 给出的原因，不会有任何改动，重新加载后再试即可。

## 在反向代理后面

同一份 Dashboard 既能放在根路径，也能放在任意路径下，例如 `https://example.com/cosky/`，无需重新构建。它相对自己的地址加载文件、调用 API，并把路由放在 URL 片段里（`#/config`），所以刷新页面或打开某个页面的链接，请求的始终只是这个地址。通过 [OIDC](./security.md#oidc) 登录后也回到这里：在身份提供方登记 `https://example.com/cosky/`。

把 Dashboard 放在某个路径下的代理需要：

- **把该路径下的一切去掉路径后转发**：`/cosky/` 到 server 的 `/`，`/cosky/assets/...` 到它的 `/assets/...`，`/cosky/v1/...` 到 `/v1/...` 的 REST API。
- **把不带末尾斜杠的路径** `/cosky` **重定向到** `/cosky/`。没有这个斜杠，浏览器会相对 `/` 解析页面中的地址。
- **不缓冲响应**，这样[保持最新](#保持最新)中的变化一发出就能到达。空闲的流每隔 [`cosky.watch.progress-interval`](./properties.md)（10 秒）带一个 bookmark，所以比这更长的读超时都没问题。

nginx 的配置：

```nginx
location /cosky/ {
    # URI 末尾的斜杠把 /cosky/ 替换为 /，并让 nginx 把 /cosky 重定向到 /cosky/。
    proxy_pass http://cosky-server:8080/;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    # Server-sent events：每条一到就转发。
    proxy_buffering off;
}
```

如果改为从别处提供 `dashboard/dist` 中的文件，就在该路径下提供它们，只把 `/cosky/v1/` 不经缓冲地转发到 server 的 `/v1/`：页面要求 API 就在它旁边、与它同源，因为 server 不提供 CORS。
