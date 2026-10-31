---
title: Dashboard
description: A tour of the CoSky web dashboard served by the REST API server.
---

# Dashboard

The REST API server serves a web dashboard at `http://<server>:8080`. Sign in with a CoSky user (see [Security](./security.md)); each page works on the namespace selected at the top, and only shows what your roles grant.

| Page | What you do there |
|---|---|
| **Home** | Counts of namespaces, configs, services and instances, and the service topology |
| **Config** | Create, edit and remove configs; browse the history with diffs and roll back; import and export zips |
| **Service** | Browse services and instances, register fixed instances, edit metadata, deregister |
| **Namespace** | Add and remove namespaces |
| **User** | Create users, bind roles, lock, unlock, change passwords |
| **Role** | Define roles and the namespaces they grant |
| **Audit Log** | Search who did what, filter by time and outcome, export CSV |

![Home](/dashboard-dashboard.png)

## Configs

![Configs](/dashboard-config.png)

Saving a config creates a new version that every application using it receives within milliseconds. The history compares any version with the current one and rolls back to it; see [Configuration](./configuration.md#versions-and-rollback).

![Rollback](/dashboard-config-rollback.png)

## Services

![Services](/dashboard-service.png)

Instances registered by applications appear and disappear on their own. Fixed instances added here stay until removed; see [Service Discovery](./service-discovery.md).

![Topology](/dashboard-topology.png)

## Users, roles and audit

![Roles](/dashboard-role.png)

![Audit log](/dashboard-audit-log.png)

## Developing the dashboard

The dashboard is a React application in [`dashboard/`](https://github.com/Ahoo-Wang/CoSky/tree/v6/dashboard); its README describes the development server, tests and the API client generation.
