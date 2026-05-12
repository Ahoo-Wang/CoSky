import { DefaultTheme } from 'vitepress'

export const en: DefaultTheme.Config = {
  label: 'English',
  lang: 'en',
  title: 'CoSky',
  description: 'High-Performance Microservice Governance Platform — Service Discovery & Configuration',
  themeConfig: {
    nav: [
      { text: 'Guide', link: '/guide/' },
      { text: 'Architecture', link: '/guide/architecture' },
      { text: 'API', link: '/guide/rest-api' },
    ],
    sidebar: {
      '/guide/': [
        {
          text: 'Getting Started',
          items: [
            { text: 'Overview', link: '/guide/' },
            { text: 'Getting Started', link: '/guide/getting-started' },
            { text: 'Installation', link: '/guide/installation' },
          ],
        },
        {
          text: 'Architecture',
          items: [
            { text: 'Architecture Overview', link: '/guide/architecture' },
            { text: 'Core Module', link: '/guide/core' },
          ],
        },
        {
          text: 'Configuration Service',
          items: [
            { text: 'Configuration Management', link: '/guide/config-service' },
            { text: 'Consistency Layer', link: '/guide/config-consistency' },
          ],
        },
        {
          text: 'Service Discovery',
          items: [
            { text: 'Service Registry', link: '/guide/service-registry' },
            { text: 'Service Discovery', link: '/guide/service-discovery' },
            { text: 'Load Balancers', link: '/guide/load-balancers' },
            { text: 'Service Topology', link: '/guide/service-topology' },
          ],
        },
        {
          text: 'Spring Cloud Integration',
          items: [
            { text: 'Config Starter', link: '/guide/spring-cloud-config' },
            { text: 'Discovery Starter', link: '/guide/spring-cloud-discovery' },
          ],
        },
        {
          text: 'REST API & Dashboard',
          items: [
            { text: 'REST API Server', link: '/guide/rest-api' },
            { text: 'Security & RBAC', link: '/guide/security-rbac' },
            { text: 'Dashboard', link: '/guide/dashboard' },
          ],
        },
        {
          text: 'Deployment',
          items: [
            { text: 'Docker', link: '/guide/deployment-docker' },
            { text: 'Kubernetes', link: '/guide/deployment-kubernetes' },
            { text: 'Standalone', link: '/guide/deployment-standalone' },
          ],
        },
        {
          text: 'Performance',
          items: [
            { text: 'Benchmarks', link: '/guide/performance' },
          ],
        },
        {
          text: 'Onboarding',
          collapsed: false,
          items: [
            { text: 'Contributor Guide', link: '/guide/onboarding-contributor' },
            { text: 'Staff Engineer Guide', link: '/guide/onboarding-staff-engineer' },
            { text: 'Executive Guide', link: '/guide/onboarding-executive' },
            { text: 'Product Manager Guide', link: '/guide/onboarding-pm' },
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
