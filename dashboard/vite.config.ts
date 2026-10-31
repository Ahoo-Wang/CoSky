import { defineConfig } from 'vite'
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

const exclude = [/src\/generated/, /node_modules/]
const coverage = process.env.COSKY_COVERAGE === '1'

export default defineConfig({
  plugins: [
    react({
      exclude,
    }),
    babel({
      presets: [reactCompilerPreset()],
      exclude,
    }),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  // The server has no CORS (design D34): in development, as behind a reverse proxy, the API shares the page's origin.
  server: {
    proxy: {
      '/v1': process.env.COSKY_API_URL ?? 'http://localhost:8080',
    },
  },
  // Coverage maps what the browser ran back to the sources, which must not reach dist/ and the image.
  build: coverage ? {sourcemap: true, outDir: 'dist-coverage'} : {},
  optimizeDeps: {
    include: ['monaco-editor'],
    exclude: ['@monaco-editor/react']
  }
})
