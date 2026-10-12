import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import type { Me } from '@/api/types'

/**
 * Huquqlar UI'ni yashirish uchun — HIMOYA EMAS. Haqiqiy tekshiruv backend @PreAuthorize'da
 * va har so'rovda DB'dan (ADR #13): rol o'zgarsa keyingi so'rovdayoq 403.
 */
export const useAuth = defineStore('auth', () => {
  const me = ref<Me | null>(null)
  const loaded = ref(false)

  const permissions = computed(() => new Set(me.value?.permissions ?? []))
  const can = (p: string) => permissions.value.has(p)

  async function load(): Promise<Me | null> {
    try {
      me.value = await authApi.me()          // XSRF-TOKEN cookie ham shu javobda keladi
    } catch {
      me.value = null
    } finally {
      loaded.value = true
    }
    return me.value
  }

  async function login(email: string, password: string) {
    await authApi.login(email, password)
    await load()
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      me.value = null
    }
  }

  function reset() {
    me.value = null
  }

  return { me, loaded, can, load, login, logout, reset }
})
