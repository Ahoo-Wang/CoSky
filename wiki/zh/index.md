---
layout: home

hero:
  name: CoSky
  text: 高性能微服务治理平台
  tagline: 基于 Redis 的服务发现与配置中心 — 100K+ QPS，零额外基础设施
  image:
    src: /cosky-logo.svg
    alt: CoSky
  actions:
    - theme: brand
      text: 快速开始
      link: /zh/guide/getting-started
    - theme: alt
      text: 架构概览
      link: /zh/guide/architecture
    - theme: alt
      text: GitHub 仓库
      link: https://github.com/Ahoo-Wang/CoSky

features:
  - title: 服务发现
    icon: 🔍
    details: 基于 Redis 的实时服务注册与发现。支持加权负载均衡、服务拓扑可视化和自动实例续约。
  - title: 配置管理
    icon: ⚙️
    details: 动态配置管理，支持版本控制、回滚和导入/导出。配置变更通过 Redis PubSub 实时传播。
  - title: 极致性能
    icon: 🚀
    details: 标准操作 100K+ QPS，一致性缓存层可达 70M+ QPS。基于 Redis Lua 脚本和进程内缓存。
  - title: Spring Cloud 原生集成
    icon: 🌿
    details: 通过 Starter 开箱即用的 Spring Cloud 集成。同时支持响应式（WebClient）和阻塞式（RestClient）编程模型。
  - title: 安全与 RBAC
    icon: 🔐
    details: JWT 认证、命名空间级别的 RBAC 权限控制、审计日志和基于 CoSec 的策略授权。
  - title: 轻量级部署
    icon: 📦
    details: 无需额外基础设施 — 使用现有的 Redis 即可。支持独立 JAR、Docker 容器或 Kubernetes Pod 部署。
---
