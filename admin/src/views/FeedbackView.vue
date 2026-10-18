<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDocumentVisibility, useIntervalFn } from '@vueuse/core'
import { Archive, Gem, Headset, Inbox, MessagesSquare } from '@lucide/vue'
import PageHeader from '@/components/app/PageHeader.vue'
import ChatInbox from '@/components/feedback/ChatInbox.vue'
import FeedbackHistory from '@/components/feedback/FeedbackHistory.vue'
import FeedbackQueue from '@/components/feedback/FeedbackQueue.vue'
import { feedbackApi } from '@/api'
import type { FeedbackCounts } from '@/api/types'
import { P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

/**
 * Murojaatlar: Navbat → «Javob berish» (suhbat operatorga biriktiriladi) → Mening chatlarim → yakunlash → Tarix.
 * Holat URL'da (?tab=mine&chat=12) — havolani ulashsa yoki sahifa yangilansa o'sha suhbat ochiladi.
 */
type Tab = 'queue' | 'sales' | 'mine' | 'all' | 'history'

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const manage = computed(() => auth.can(P.FEEDBACK_MANAGE))

const tabs = computed<Tab[]>(() => (manage.value ? ['queue', 'sales', 'mine', 'all', 'history'] : ['queue', 'sales', 'mine', 'history']))
const tab = computed<Tab>({
  get: () => {
    const t = route.query.tab as Tab
    return tabs.value.includes(t) ? t : 'queue'
  },
  set: (t) => router.replace({ query: { tab: t } }),
})
const chat = computed<number | null>({
  get: () => (route.query.chat ? Number(route.query.chat) || null : null),
  set: (id) => router.replace({ query: { ...route.query, chat: id ?? undefined } }),
})

const counts = ref<FeedbackCounts>({ NEW: 0, MINE: 0, MINE_UNREAD: 0 })
async function loadCounts() {
  try { counts.value = await feedbackApi.counts() } catch { /* nishon ixtiyoriy */ }
}
const visibility = useDocumentVisibility()
const { pause } = useIntervalFn(() => { if (visibility.value === 'visible') loadCounts() }, 10000)
watch(() => route.query, loadCounts)
onMounted(() => { loadCounts(); window.addEventListener('feedback-changed', loadCounts) })
onBeforeUnmount(() => { pause(); window.removeEventListener('feedback-changed', loadCounts) })

const tabItems = computed(() => {
  const c = counts.value
  const all = [
    { key: 'queue' as Tab, title: 'Navbat', icon: Inbox, badge: c.NEW, badgeClass: 'bg-amber-500 text-white' },
    // Pro olish / Agro Biznes arizalari — navbatdagisi ham, suhbatdagisi ham; nishon — javob kutayotganlar
    { key: 'sales' as Tab, title: 'Obuna soʻrovlari', icon: Gem, badge: c.SALES ?? 0, badgeClass: 'bg-amber-500 text-white' },
    { key: 'mine' as Tab, title: 'Mening chatlarim', icon: Headset, badge: c.MINE_UNREAD || c.MINE,
      badgeClass: c.MINE_UNREAD ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground' },
    { key: 'all' as Tab, title: 'Barcha chatlar', icon: MessagesSquare, badge: c.OPEN ?? 0, badgeClass: 'bg-muted text-muted-foreground' },
    { key: 'history' as Tab, title: 'Chatlar tarixi', icon: Archive, badge: 0, badgeClass: '' },
  ]
  return all.filter((t) => tabs.value.includes(t.key))
})

function openMine(id: number) {
  router.replace({ query: { tab: 'mine', chat: id } })
}
</script>

<template>
  <PageHeader title="Murojaatlar" description="Fermerlar botdagi «💬 Fikr va yordam» orqali yozadi. Suhbat ikki tomondan biri yakunlaguncha davom etadi" />

  <nav class="mb-4 flex gap-1 overflow-x-auto border-b" aria-label="Murojaatlar boʻlimlari">
    <button
      v-for="t in tabItems" :key="t.key" type="button"
      :class="['-mb-px flex shrink-0 items-center gap-2 border-b-2 px-3 py-2.5 text-sm font-medium transition-colors',
               tab === t.key ? 'border-primary text-foreground' : 'text-muted-foreground hover:text-foreground border-transparent']"
      :aria-current="tab === t.key ? 'page' : undefined"
      @click="tab = t.key"
    >
      <component :is="t.icon" class="size-4" />
      {{ t.title }}
      <span v-if="t.badge" :class="['min-w-5 rounded-full px-1.5 text-center text-[11px] leading-5 font-semibold tabular-nums', t.badgeClass]">{{ t.badge }}</span>
    </button>
  </nav>

  <FeedbackQueue v-if="tab === 'queue'" @claimed="openMine" />
  <ChatInbox v-else-if="tab === 'sales'" v-model:selected="chat" scope="all" kind="SUBSCRIPTION" />
  <ChatInbox v-else-if="tab === 'mine'" v-model:selected="chat" scope="mine" @to-queue="tab = 'queue'" />
  <ChatInbox v-else-if="tab === 'all'" v-model:selected="chat" scope="all" />
  <FeedbackHistory v-else />
</template>
