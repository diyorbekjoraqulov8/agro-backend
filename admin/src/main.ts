import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/http'
import { useAuth } from './stores/auth'

const app = createApp(App)
app.use(createPinia())
app.use(router)

// Refresh ham o'tmadi (sessiya bekor qilingan / muddati tugagan) — login sahifasiga
setUnauthorizedHandler(() => {
  useAuth().reset()
  if (router.currentRoute.value.path !== '/login') {
    router.push({ path: '/login', query: { next: router.currentRoute.value.fullPath } })
  }
})

app.mount('#app')
