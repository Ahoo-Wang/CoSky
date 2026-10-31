import { DefaultTheme } from 'vitepress'

export const en: DefaultTheme.Config = {
  label: 'English',
  lang: 'en',
  title: 'CoSky',
  description: 'Configuration and service discovery for microservices, on a small server in front of Redis',
  themeConfig: {
    nav: [
      { text: 'Guide', link: '/guide/' },
      { text: 'Architecture', link: '/guide/architecture' },
      { text: 'API', link: '/guide/rest-api' },
    ],
    sidebar: {
      '/guide/': [
        {
          text: 'Introduction',
          items: [
            { text: 'Overview', link: '/guide/' },
            { text: 'Getting Started', link: '/guide/getting-started' },
          ],
        },
        {
          text: 'Guides',
          items: [
            { text: 'Configuration', link: '/guide/configuration' },
            { text: 'Service Discovery', link: '/guide/service-discovery' },
            { text: 'Using the SDK', link: '/guide/sdk' },
          ],
        },
        {
          text: 'Operations',
          items: [
            { text: 'Deployment', link: '/guide/deployment' },
            { text: 'Security', link: '/guide/security' },
            { text: 'Dashboard', link: '/guide/dashboard' },
            { text: 'Moving from 6.x', link: '/guide/upgrade-8' },
          ],
        },
        {
          text: 'Reference',
          items: [
            { text: 'Configuration Properties', link: '/guide/properties' },
            { text: 'REST API', link: '/guide/rest-api' },
            { text: 'Requirements', link: '/guide/requirements' },
          ],
        },
        {
          text: 'Concepts',
          items: [
            { text: 'Architecture', link: '/guide/architecture' },
            { text: 'Performance', link: '/guide/performance' },
          ],
        },
        {
          text: 'Contributing',
          items: [
            { text: 'Internals', link: '/guide/internals' },
          ],
        },
      ],
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/Ahoo-Wang/CoSky' },
    ],
    footer: {
      message: 'Released under the Apache License 2.0.',
      copyright: 'Copyright 2021-present Ahoo Wang',
    },
    editLink: {
      pattern: 'https://github.com/Ahoo-Wang/CoSky/edit/main/wiki/:path',
      text: 'Edit this page on GitHub',
    },
    search: {
      provider: 'local',
    },
    outline: {
      label: 'On this page',
    },
    lastUpdated: {
      text: 'Updated at',
    },
    docFooter: {
      prev: 'Previous',
      next: 'Next',
    },
  },
}
