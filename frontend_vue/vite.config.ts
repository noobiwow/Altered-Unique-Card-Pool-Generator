import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    // Same-origin API in dev: also works when opened from another device
    // with `npm run dev -- --host`.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
