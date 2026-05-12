import { DefaultTheme } from 'vitepress'

export const zh: DefaultTheme.Config = {
  label: '简体中文',
  lang: 'zh-CN',
  title: 'CoSky',
  description: '高性能微服务治理平台 — 服务发现与配置中心',
  themeConfig: {
    nav: [
      { text: '指南', link: '/zh/guide/' },
      { text: '架构', link: '/zh/guide/architecture' },
      { text: 'API', link: '/zh/guide/rest-api' },
    ],
    sidebar: {
      '/zh/guide/': [
        {
          text: '快速开始',
          items: [
            { text: '概述', link: '/zh/guide/' },
            { text: '快速开始', link: '/zh/guide/getting-started' },
            { text: '安装', link: '/zh/guide/installation' },
          ],
        },
        {
          text: '架构',
          items: [
            { text: '架构总览', link: '/zh/guide/architecture' },
            { text: '核心模块', link: '/zh/guide/core' },
          ],
        },
        {
          text: '配置服务',
          items: [
            { text: '配置管理', link: '/zh/guide/config-service' },
            { text: '一致性层', link: '/zh/guide/config-consistency' },
          ],
        },
        {
          text: '服务发现',
          items: [
            { text: '服务注册', link: '/zh/guide/service-registry' },
            { text: '服务发现', link: '/zh/guide/service-discovery' },
            { text: '负载均衡', link: '/zh/guide/load-balancers' },
            { text: '服务拓扑', link: '/zh/guide/service-topology' },
          ],
        },
        {
          text: 'Spring Cloud 集成',
          items: [
            { text: '配置 Starter', link: '/zh/guide/spring-cloud-config' },
            { text: '服务发现 Starter', link: '/zh/guide/spring-cloud-discovery' },
          ],
        },
        {
          text: 'REST API 与控制台',
          items: [
            { text: 'REST API 服务', link: '/zh/guide/rest-api' },
            { text: '安全与 RBAC', link: '/zh/guide/security-rbac' },
            { text: '控制台', link: '/zh/guide/dashboard' },
          ],
        },
        {
          text: '部署',
          items: [
            { text: 'Docker 部署', link: '/zh/guide/deployment-docker' },
            { text: 'Kubernetes 部署', link: '/zh/guide/deployment-kubernetes' },
            { text: '独立部署', link: '/zh/guide/deployment-standalone' },
          ],
        },
        {
          text: '性能',
          items: [
            { text: '性能基准测试', link: '/zh/guide/performance' },
          ],
        },
        {
          text: '入门指南',
          collapsed: false,
          items: [
            { text: '贡献者指南', link: '/zh/guide/onboarding-contributor' },
            { text: '高级工程师指南', link: '/zh/guide/onboarding-staff-engineer' },
            { text: '管理层指南', link: '/zh/guide/onboarding-executive' },
            { text: '产品经理指南', link: '/zh/guide/onboarding-pm' },
          ],
        },
      ],
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/Ahoo-Wang/CoSky' },
    ],
    footer: {
      message: '基于 Apache License 2.0 许可发布。',
      copyright: 'Copyright 2021-present Ahoo Wang',
    },
    editLink: {
      pattern: 'https://github.com/Ahoo-Wang/CoSky/edit/main/wiki/:path',
      text: '在 GitHub 上编辑此页',
    },
    search: {
      provider: 'local',
    },
    outline: {
      label: '本页目录',
    },
    lastUpdated: {
      text: '最后更新于',
    },
    docFooter: {
      prev: '上一页',
      next: '下一页',
    },
  },
}
