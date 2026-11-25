---
title: Dashboard
description: What the web console does, how to sign in, how it stays current, and how to serve it under a path behind a reverse proxy.
---

# Dashboard

The server hosts the Dashboard at `/`, on port 8080, and a reverse proxy can serve it under a path of its own ([Behind a reverse proxy](#behind-a-reverse-proxy)). It uses only the public [REST API](./rest-api.md), so it can do nothing you could not do with `curl`, and your [roles](./security.md#who-may-do-what) apply to it. A refused action shows the server's reason.

![Dashboard](/dashboard-dashboard.png)

## Signing in

Paste a ServiceAccount token, or use the OIDC button when the server [offers one](./security.md#oidc). The token stays in that browser tab, and goes when the tab closes. A server with `cosky.auth.enabled: false` asks for nothing.

## Pages

The namespace selector in the header decides what every page shows.

| Page | What it does |
|---|---|
| **Dashboard** | Counts of services, instances, Configs and namespaces, and the topology of who watches whom |
| **Configuration** | Create, edit, delete, import and export Configs; compare any revision with the current one, and roll back |
| **Service** | Services and their instances, leased or fixed; add, edit and delete instances |
| **Namespace** | Create and delete namespaces. Only an empty one can be deleted, and not the one selected. |
| **Service Account** | Accounts and their tokens. A new token shows once, so copy it then. |
| **Role** and **Role Binding** | Who may do what, and where |

![Configuration](/dashboard-config.png)

## Staying current

The Configuration and Service pages follow changes as they happen, over a server-sent event stream, without a reload. A stream that breaks or goes silent is opened again. The first page reads its counts every 30 seconds, so that it does not hold a connection of its own: browsers allow few per server over HTTP/1.1.

## Editing safely

An edit remembers the revision it started from. If someone changed the object in the meantime, saving fails with the server's reason and changes nothing, so reload and try again.

## Behind a reverse proxy

The same Dashboard works at the root and under any path, such as `https://example.com/cosky/`, without being built again. It loads its files and calls the API relative to its own address, and keeps its routes in the fragment (`#/config`), so a reload or a link to one of its pages only ever asks for that address. Signing in with [OIDC](./security.md#oidc) returns to it as well: register `https://example.com/cosky/` with the provider.

A proxy that serves the Dashboard under a path has to:

- **Pass on everything under the path, without it**: `/cosky/` reaches the server's `/`, `/cosky/assets/...` its `/assets/...`, and `/cosky/v1/...` the REST API at `/v1/...`.
- **Redirect the path without its trailing slash**, `/cosky`, to `/cosky/`. Without the slash, the browser resolves the page's addresses against `/`.
- **Not buffer responses**, so that the changes of [Staying current](#staying-current) arrive when they are sent. An idle stream carries a bookmark every [`cosky.watch.progress-interval`](./properties.md) (10 seconds), so any read timeout longer than that holds.

With nginx:

```nginx
location /cosky/ {
    # The trailing slash of the URI replaces /cosky/ with /, and has nginx redirect /cosky to /cosky/.
    proxy_pass http://cosky-server:8080/;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    # Server-sent events: pass each one on as it comes.
    proxy_buffering off;
}
```

To serve the files of `dashboard/dist` from somewhere else instead, serve them at the path, and pass on only `/cosky/v1/` to the server's `/v1/`, unbuffered: the page expects the API next to it, on its own origin, since the server has no CORS.
