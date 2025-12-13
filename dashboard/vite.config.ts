import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react({
      exclude: [/src\/generated/, /node_modules/],
      babel: {
        plugins: [['babel-plugin-react-compiler']],
      },
    }),
  ],
})
