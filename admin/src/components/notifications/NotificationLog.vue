<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { toast } from 'vue-sonner'
import { RotateCcw, X } from '@lucide/vue'
import Paginator from '@/components/app/Paginator.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { notificationsApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { NotificationCategory, NotificationItem, NotificationStatus, Page } from '@/api/types'
import { fmtDateTime, fmtNum } from '@/lib/format'
import { P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

/** Barcha bildirishnomalar (avtomatik va kampaniya): holat, urinishlar, xato; yetkazilmaganini qayta yuborish */
const props = defineProps<{ farmerId?: number | null; campaignId?: number | null }>()
const emit = defineEmits<{ clearFilter: [] }>()
const auth = useAuth()
const ALL = 'ALL'

const STATUS: Record<NotificationStatus, { l: string; t: 'gray' | 'blue' | 'amber' | 'green' | 'red' | 'violet' }> = {
  PENDING: { l: 'Navbatda', t: 'blue' }, SENDING: { l: 'Yuborilmoqda', t: 'amber' }, SENT: { l: 'Yetdi', t: 'green' },
  FAILED: { l: 'Xato', t: 'red' }, SKIPPED: { l: 'Bloklagan', t: 'gray' }, CANCELLED: { l: 'Bekor', t: 'gray' },
}
const CATEGORY: Record<NotificationCategory, string> = {
  ANALYSIS: 'Tahlil', IRRIGATION: 'Sugʻorish', FEEDBACK: 'Murojaat', REFERRAL: 'Taklif', BROADCAST: 'Kampaniya', SYSTEM: 'Tizim', WEATHER: 'Ob-havo', MONITORING: 'Monitoring', ADVISOR: 'AI maslahat',
}

const f = reactive({ status: ALL, category: ALL, from: '', to: '' })
const page = ref(0)
const data = ref<Page<NotificationItem> | null>(null)
const stats = ref<Partial<Record<NotificationStatus, number>>>({})

async function load() {
  try {
    data.value = await notificationsApi.search({
      status: f.status === ALL ? undefined : f.status, category: f.category === ALL ? undefined : f.category,
      farmerId: props.farmerId ?? undefined, campaignId: props.campaignId ?? undefined,
      from: f.from || undefined, to: f.to || undefined, page: page.value, size: 50,
    })
  } catch (e) { toast.error(errorMessage(e)) }
}
// Filtr o'zgarsa — 1-sahifa; page allaqachon 0 bo'lsa watch(page) ishlamaydi, shuning uchun o'zimiz yuklaymiz (ikki marta emas)
watch(() => [f.status, f.category, f.from, f.to, props.farmerId, props.campaignId], () => {
  if (page.value === 0) load()
  else page.value = 0
})
watch(page, load)
onMounted(async () => {
  load()
  try { stats.value = await notificationsApi.stats() } catch { /* ixtiyoriy */ }
})

const filterLabel = computed(() => props.campaignId ? `Kampaniya #${props.campaignId}` : props.farmerId ? `Fermer #${props.farmerId}` : null)

async function retry(n: NotificationItem) {
  try {
    const r = await notificationsApi.retry(n.id)
    data.value!.content = data.value!.content.map((x) => (x.id === r.id ? r : x))
    toast.success('Qayta navbatga qoʻyildi')
  } catch (e) { toast.error(errorMessage(e)) }
}
</script>

<template>
  <div class="mb-4 grid grid-cols-2 gap-3 md:grid-cols-5">
    <div v-for="s in (['SENT', 'PENDING', 'FAILED', 'SKIPPED', 'CANCELLED'] as NotificationStatus[])" :key="s" class="bg-background rounded-xl border p-3 shadow-xs">
      <p class="text-muted-foreground text-xs">{{ STATUS[s].l }} · 24 soat</p>
      <p class="mt-1 text-xl font-semibold tabular-nums">{{ fmtNum(stats[s] ?? 0) }}</p>
    </div>
  </div>

  <div class="mb-3 flex flex-wrap items-center gap-2">
    <span v-if="filterLabel" class="bg-primary/10 text-primary inline-flex items-center gap-1 rounded-full px-3 py-1 text-sm font-medium">
      {{ filterLabel }} <button type="button" class="hover:opacity-70" @click="emit('clearFilter')"><X class="size-3.5" /></button>
    </span>
    <Select v-model="f.status">
      <SelectTrigger class="w-40"><SelectValue /></SelectTrigger>
      <SelectContent><SelectItem :value="ALL">Barcha holat</SelectItem><SelectItem v-for="(s, k) in STATUS" :key="k" :value="k">{{ s.l }}</SelectItem></SelectContent>
    </Select>
    <Select v-model="f.category">
      <SelectTrigger class="w-40"><SelectValue /></SelectTrigger>
      <SelectContent><SelectItem :value="ALL">Barcha tur</SelectItem><SelectItem v-for="(l, k) in CATEGORY" :key="k" :value="k">{{ l }}</SelectItem></SelectContent>
    </Select>
    <Input v-model="f.from" type="date" class="w-40" aria-label="Sanadan" />
    <Input v-model="f.to" type="date" class="w-40" aria-label="Sanagacha" />
  </div>

  <div class="bg-background rounded-xl border">
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Vaqt</TableHead><TableHead>Fermer</TableHead><TableHead>Tur</TableHead><TableHead>Xabar</TableHead>
          <TableHead>Holat</TableHead><TableHead class="bg-card sticky right-0" />
        </TableRow>
      </TableHeader>
      <TableBody>
        <template v-if="!data"><TableRow v-for="i in 6" :key="i"><TableCell :colspan="6"><Skeleton class="h-6 w-full" /></TableCell></TableRow></template>
        <TableRow v-for="n in data?.content ?? []" :key="n.id">
          <TableCell class="text-muted-foreground text-sm whitespace-nowrap">{{ fmtDateTime(n.sentAt ?? n.createdDate) }}</TableCell>
          <TableCell class="whitespace-nowrap">
            <RouterLink v-if="auth.can(P.FARMER_READ)" :to="`/farmers/${n.farmerId}`" class="hover:underline">{{ n.farmerName ?? `#${n.farmerId}` }}</RouterLink>
            <span v-else>{{ n.farmerName ?? `#${n.farmerId}` }}</span>
          </TableCell>
          <TableCell><StatusBadge tone="gray" :label="CATEGORY[n.category]" /></TableCell>
          <TableCell class="max-w-md">
            <p class="truncate text-sm" :title="n.preview ?? ''">{{ n.preview }}</p>
            <p v-if="n.lastError" class="truncate text-xs text-red-600" :title="n.lastError">{{ n.attempts }} urinish · {{ n.lastError }}</p>
          </TableCell>
          <TableCell><StatusBadge :tone="STATUS[n.status].t" :label="STATUS[n.status].l" /></TableCell>
          <TableCell class="bg-card sticky right-0 text-right">
            <Button v-if="auth.can(P.NOTIFICATION_SEND) && (n.status === 'FAILED' || n.status === 'SKIPPED')" variant="ghost" size="sm" @click="retry(n)">
              <RotateCcw /> Qayta
            </Button>
          </TableCell>
        </TableRow>
        <TableEmpty v-if="data && !data.content.length" :colspan="6">Xabar yoʻq</TableEmpty>
      </TableBody>
    </Table>
  </div>
  <Paginator v-if="data && data.totalPages > 1" class="mt-4" :page="data.page" :total-pages="data.totalPages" :total-elements="data.totalElements" @change="page = $event" />
</template>
