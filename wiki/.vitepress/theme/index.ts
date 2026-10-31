import DefaultTheme from 'vitepress/theme'
import type { Theme } from 'vitepress'
import { defineAsyncComponent } from 'vue'
import './custom.css'

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    // As in the CoCache and Wow docs: vitepress-plugin-mermaid turns a ```mermaid block into <Mermaid>, which renders it.
    app.component('Mermaid', defineAsyncComponent(() => import('./Mermaid.vue')))
  },
} satisfies Theme
