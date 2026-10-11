---
layout: home

hero:
  name: CoSky
  text: 基于 Redis 的配置管理与服务发现
  tagline: 面向 Spring Cloud 微服务，除了你已经在运行的 Redis，没有任何需要运维的东西
  image:
    src: /cosky-logo.svg
    alt: CoSky
  actions:
    - theme: brand
      text: 快速开始
      link: /zh/guide/getting-started
    - theme: alt
      text: 概述
      link: /zh/guide/
    - theme: alt
      text: GitHub 仓库
      link: https://github.com/Ahoo-Wang/CoSky

features:
  - title: 服务发现
    icon: 🔍
    details: 应用自行注册、续约并自动过期；消费者通过 Spring Cloud 发现存活实例，支持加权负载均衡与服务拓扑。
  - title: 配置管理
    icon: ⚙️
    details: 由 Redis 提供并实时刷新的 Spring 属性，支持版本、回滚，以及从 Nacos 导入导出。
  - title: 从内存读取
    icon: 🚀
    details: 每个应用持有由 Redis Pub/Sub 保持最新的本地缓存，读取配置或选择实例都不产生网络往返。
  - title: Spring Cloud 原生集成
    icon: 🌿
    details: 通过 Starter 开箱即用的 Spring Cloud 集成，同时覆盖配置中心与服务发现。
  - title: 安全与 RBAC
    icon: 🔐
    details: JWT 认证、命名空间级别的 RBAC 权限控制、审计日志和基于 CoSec 的策略授权。
  - title: 轻量级部署
    icon: 📦
    details: 应用只需要 Redis。可选的 REST API 服务器与控制台可作为 Docker 镜像、在 Kubernetes 上或以独立发行包运行。
---
