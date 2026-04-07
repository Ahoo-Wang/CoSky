import { defineConfig } from 'vite'
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'

const exclude = [/src\/generated/, /node_modules/]

export default defineConfig({
  plugins: [
    react({
      exclude,
    }),
    babel({
      presets: [reactCompilerPreset()],
      exclude,
    }),
  ],
  optimizeDeps: {
    include: ['monaco-editor'],
    exclude: ['@monaco-editor/react']
  },
})
