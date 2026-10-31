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
          text: '介绍',
          items: [
            { text: '概述', link: '/zh/guide/' },
            { text: '快速开始', link: '/zh/guide/getting-started' },
          ],
        },
        {
          text: '使用指南',
          items: [
            { text: '配置管理', link: '/zh/guide/configuration' },
            { text: '服务发现', link: '/zh/guide/service-discovery' },
            { text: '使用 SDK', link: '/zh/guide/sdk' },
          ],
        },
        {
          text: '运维',
          items: [
            { text: '部署', link: '/zh/guide/deployment' },
            { text: '安全', link: '/zh/guide/security' },
            { text: '控制台', link: '/zh/guide/dashboard' },
            { text: '升级到 6.0', link: '/zh/guide/upgrade-6' },
          ],
        },
        {
          text: '参考',
          items: [
            { text: '配置属性', link: '/zh/guide/properties' },
            { text: 'REST API', link: '/zh/guide/rest-api' },
            { text: '需求', link: '/zh/guide/requirements' },
          ],
        },
        {
          text: '原理',
          items: [
            { text: '架构', link: '/zh/guide/architecture' },
            { text: '性能', link: '/zh/guide/performance' },
          ],
        },
        {
          text: '贡献',
          items: [
            { text: '内部实现', link: '/zh/guide/internals' },
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
      pattern: 'https://github.com/Ahoo-Wang/CoSky/edit/v6/wiki/:path',
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
