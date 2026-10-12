import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// Backend bilan BITTA origin (ADR #25): CORS sozlanmaydi, cookie SameSite=Strict saqlanadi.
// Dev'da /api → localhost:8081 proxy; prod'da nginx xuddi shunday.
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8081', changeOrigin: false },
    },
  },
})
