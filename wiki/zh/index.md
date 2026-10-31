---
layout: home

hero:
  name: CoSky
  text: 配置管理与服务发现
  tagline: 在你已有的 Redis 前面放一个小型 server，客户端从内存读取
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
    details: 应用挂在租约上注册，租约结束实例就消失。消费者通过 Spring Cloud 或 Kotlin 客户端实时看到变化。
  - title: 配置管理
    icon: ⚙️
    details: 由 CoSky 提供并实时刷新的 Spring 属性，支持 revision、回滚，以及 zip 导入导出。
  - title: 从内存读取
    icon: 🚀
    details: 每个客户端都保存一份由 Watch 流保持最新的本地视图，读配置、选实例都不需要网络往返。
  - title: 故障时继续工作
    icon: 🛡️
    details: server 不可用时，客户端继续提供已知的数据；重启后从磁盘快照恢复。分区之后的大面积到期会被拦住，而不是照单执行。
  - title: 每次调用都鉴权
    icon: 🔐
    details: 支持 ServiceAccount token 和 OIDC，用角色说明谁能做什么，可细到名称前缀。应用同样要认证，并且不接触 Redis。
  - title: 运维简单
    icon: 📦
    details: 一个镜像同时包含 server 和 Dashboard，只依赖 Redis 7，可运行在 Docker 或 Kubernetes 上。
---
