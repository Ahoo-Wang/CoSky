---
title: Dashboard
description: What the web console does, how to sign in, and how it stays current.
---

# Dashboard

The server hosts the Dashboard at `/`, on port 8080. It uses only the public [REST API](./rest-api.md), so it can do nothing you could not do with `curl`, and your [roles](./security.md#who-may-do-what) apply to it. A refused action shows the server's reason.

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
