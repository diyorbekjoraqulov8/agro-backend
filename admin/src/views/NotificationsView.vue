<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { History, Megaphone, Send } from '@lucide/vue'
import PageHeader from '@/components/app/PageHeader.vue'
import CampaignList from '@/components/notifications/CampaignList.vue'
import NotificationComposer from '@/components/notifications/NotificationComposer.vue'
import NotificationLog from '@/components/notifications/NotificationLog.vue'
import { P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

/**
 * Bildirishnomalar: yangi xabar (hammaga / filtr / bitta fermerga) · kampaniyalar · jurnal (barcha avtomatik xabarlar ham).
 * Holat URL'da: ?tab=log&farmerId=12 — fermer sahifasidan to'g'ridan-to'g'ri ochiladi.
 */
type Tab = 'compose' | 'campaigns' | 'log'
const route = useRoute()
const router = useRouter()
const auth = useAuth()

const tabs = computed(() => [
  ...(auth.can(P.NOTIFICATION_SEND) ? [{ key: 'compose' as Tab, title: 'Yangi xabar', icon: Send }] : []),
  { key: 'campaigns' as Tab, title: 'Kampaniyalar', icon: Megaphone },
  { key: 'log' as Tab, title: 'Jurnal', icon: History },
])
const tab = computed<Tab>(() => {
  const t = route.query.tab as Tab
  return tabs.value.some((x) => x.key === t) ? t : tabs.value[0]!.key
})
const num = (v: unknown) => (v ? Number(v) || null : null)
const farmerId = computed(() => num(route.query.farmerId))
const campaignId = computed(() => num(route.query.campaignId))

function go(t: Tab, extra: Record<string, string | number> = {}) {
  router.replace({ query: { tab: t, ...extra } })
}
</script>

<template>
  <PageHeader title="Bildirishnomalar" description="Fermerlarga xabar yuborish va bot yuborgan barcha xabarlar jurnali (hisobot, eslatma, javob…)" />

  <nav class="mb-4 flex gap-1 overflow-x-auto border-b">
    <button v-for="t in tabs" :key="t.key" type="button"
            :class="['-mb-px flex shrink-0 items-center gap-2 border-b-2 px-3 py-2.5 text-sm font-medium transition-colors',
                     tab === t.key ? 'border-primary text-foreground' : 'text-muted-foreground hover:text-foreground border-transparent']"
            @click="go(t.key)">
      <component :is="t.icon" class="size-4" /> {{ t.title }}
    </button>
  </nav>

  <NotificationComposer v-if="tab === 'compose'" :farmer-id="farmerId" @sent="go('campaigns')" />
  <CampaignList v-else-if="tab === 'campaigns'" @open-log="(id) => go('log', { campaignId: id })" />
  <NotificationLog v-else :farmer-id="farmerId" :campaign-id="campaignId" @clear-filter="go('log')" />
</template>
