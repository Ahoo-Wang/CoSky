import { defineConfig } from 'vitepress'
import { en } from './config/en'
import { zh } from './config/zh'

export default defineConfig({
  title: 'CoSky',
  description: 'High-Performance Microservice Governance Platform',
  base: '/',
  lastUpdated: true,
  cleanUrls: true,
  ignoreDeadLinks: true,
  sitemap: {
    hostname: 'https://cosky.ahoo.me',
  },
  themeConfig: {
    search: {
      provider: 'local',
    },
  },
  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/cosky-logo.svg' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.googleapis.com' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' }],
    ['link', { href: 'https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap', rel: 'stylesheet' }],
    ['script', { async: '', src: 'https://www.googletagmanager.com/gtag/js?id=G-5DREMPKM1W' }],
    ['script', {}, "window.dataLayer=window.dataLayer||[];function gtag(){dataLayer.push(arguments);}gtag('js',new Date());gtag('config','G-5DREMPKM1W');"],
  ],
  locales: {
    root: { ...en },
    zh: { ...zh },
  },
})
