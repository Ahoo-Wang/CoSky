import {defineConfig} from 'vite'
import react, {reactCompilerPreset} from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import tailwindcss from '@tailwindcss/vite'
import {fileURLToPath, URL} from 'node:url'

const exclude = [/src\/generated/, /node_modules/]

export default defineConfig({
  plugins: [
    tailwindcss(),
    react({
      exclude,
    }),
    babel({
      presets: [reactCompilerPreset()],
      exclude,
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  optimizeDeps: {
    include: ['monaco-editor'],
    exclude: ['@monaco-editor/react'],
  },
})
