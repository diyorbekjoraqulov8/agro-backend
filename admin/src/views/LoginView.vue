<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loader2, Sprout } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { useAuth } from '@/stores/auth'
import { ApiError } from '@/api/http'

const auth = useAuth()
const router = useRouter()
const route = useRoute()
const email = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(email.value.trim(), password.value)
    const next = typeof route.query.next === 'string' && route.query.next.startsWith('/') ? route.query.next : '/'
    router.replace(next)
  } catch (e) {
    // 401 — email yoki parol (yoki 5 xatodan keyin blok) — backend sababni oshkor qilmaydi
    error.value = e instanceof ApiError && e.status === 401 ? 'Email yoki parol notoʻgʻri' : 'Serverga ulanib boʻlmadi'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="bg-muted/40 flex min-h-svh items-center justify-center p-4">
    <div class="w-full max-w-sm">
      <div class="mb-6 flex flex-col items-center gap-2 text-center">
        <div class="bg-primary text-primary-foreground flex size-10 items-center justify-center rounded-xl">
          <Sprout class="size-5" />
        </div>
        <h1 class="text-xl font-semibold">Agro boshqaruv paneli</h1>
        <p class="text-muted-foreground text-sm">Xodimlar uchun kirish</p>
      </div>
      <form class="bg-card space-y-4 rounded-xl border p-6 shadow-sm" @submit.prevent="submit">
        <Alert v-if="error" variant="destructive"><AlertDescription>{{ error }}</AlertDescription></Alert>
        <div class="space-y-2">
          <Label for="email">Email</Label>
          <Input id="email" v-model="email" type="email" autocomplete="username" required autofocus />
        </div>
        <div class="space-y-2">
          <Label for="password">Parol</Label>
          <Input id="password" v-model="password" type="password" autocomplete="current-password" required />
        </div>
        <Button type="submit" class="w-full" :disabled="loading">
          <Loader2 v-if="loading" class="animate-spin" /> Kirish
        </Button>
      </form>
    </div>
  </div>
</template>
