import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [
    react({
      exclude: [/src\/generated/, /node_modules/],
      babel: {
        plugins: [['babel-plugin-react-compiler']],
      },
    }),
  ],
  optimizeDeps: {
    include: ['monaco-editor'],
    exclude: ['@monaco-editor/react']
  },
})
