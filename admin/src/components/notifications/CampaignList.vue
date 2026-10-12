<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { useIntervalFn } from '@vueuse/core'
import { Ban, Megaphone } from '@lucide/vue'
import Paginator from '@/components/app/Paginator.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { notificationsApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Audience, Campaign, CampaignStatus, Page } from '@/api/types'
import { fmtDateTime, fmtNum } from '@/lib/format'
import { P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

/** Kampaniyalar: holat va yetkazish natijasi (yuborilayotganlari — har 5 s yangilanadi) */
const emit = defineEmits<{ openLog: [number] }>()
const auth = useAuth()
const page = ref(0)
const data = ref<Page<Campaign> | null>(null)

const STATUS: Record<CampaignStatus, { l: string; t: 'blue' | 'amber' | 'green' | 'gray' }> = {
  SCHEDULED: { l: 'Rejalashtirilgan', t: 'blue' }, SENDING: { l: 'Yuborilmoqda', t: 'amber' },
  DONE: { l: 'Yakunlandi', t: 'green' }, CANCELLED: { l: 'Bekor qilingan', t: 'gray' },
}

async function load() {
  try { data.value = await notificationsApi.campaigns({ page: page.value, size: 20 }) } catch (e) { toast.error(errorMessage(e)) }
}
watch(page, load)
onMounted(load)
const active = computed(() => (data.value?.content ?? []).some((c) => c.status === 'SENDING' || c.status === 'SCHEDULED'))
const { pause } = useIntervalFn(() => { if (active.value) load() }, 5000)
onBeforeUnmount(pause)

function audienceText(a: Audience) {
  if (a.type === 'ALL') return 'Hammaga'
  if (a.type === 'SINGLE') return `Bitta fermer #${a.farmerId}`
  const parts = [
    a.languages?.length && `til: ${a.languages.join(', ')}`, a.crops?.length && `${a.crops.length} ekin`,
    a.paid != null && (a.paid ? 'pullik' : 'bepul'), a.hasFields != null && (a.hasFields ? 'dalasi bor' : 'dalasiz'),
    a.activated != null && (a.activated ? 'faol' : 'faol emas'), a.countries?.length && a.countries.join(', '),
    (a.registeredFrom || a.registeredTo) && `roʻyxat: ${a.registeredFrom ?? '…'} — ${a.registeredTo ?? '…'}`,
  ].filter(Boolean)
  return 'Filtr: ' + (parts.join(' · ') || 'hammasi')
}
const pct = (c: Campaign) => (c.total ? Math.round(((c.counts.SENT ?? 0) / c.total) * 100) : 0)

async function cancel(c: Campaign) {
  try {
    const r = await notificationsApi.cancel(c.id)
    data.value!.content = data.value!.content.map((x) => (x.id === r.id ? r : x))
    toast.success('Bekor qilindi — kutayotgan xabarlar yuborilmaydi')
  } catch (e) { toast.error(errorMessage(e)) }
}
</script>

<template>
  <div class="bg-background rounded-xl border">
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Kampaniya</TableHead><TableHead>Kimga</TableHead><TableHead>Holat</TableHead>
          <TableHead class="w-56">Natija</TableHead><TableHead>Vaqt</TableHead><TableHead class="bg-card sticky right-0" />
        </TableRow>
      </TableHeader>
      <TableBody>
        <template v-if="!data"><TableRow v-for="i in 4" :key="i"><TableCell :colspan="6"><Skeleton class="h-8 w-full" /></TableCell></TableRow></template>
        <TableRow v-for="c in data?.content ?? []" :key="c.id" class="cursor-pointer" @click="emit('openLog', c.id)">
          <TableCell>
            <p class="font-medium">{{ c.title }}</p>
            <p class="text-muted-foreground max-w-72 truncate text-xs">{{ c.texts.uz }}</p>
          </TableCell>
          <TableCell class="text-muted-foreground max-w-56 text-sm">{{ audienceText(c.audience) }}</TableCell>
          <TableCell><StatusBadge :tone="STATUS[c.status].t" :label="STATUS[c.status].l" /></TableCell>
          <TableCell>
            <template v-if="c.total !== null">
              <div class="bg-muted h-2 overflow-hidden rounded-full"><div class="bg-primary h-full transition-all" :style="{ width: pct(c) + '%' }" /></div>
              <p class="text-muted-foreground mt-1 text-xs tabular-nums">
                {{ fmtNum(c.counts.SENT ?? 0) }} / {{ fmtNum(c.total) }} yetdi
                <span v-if="c.counts.FAILED" class="text-red-600"> · {{ c.counts.FAILED }} xato</span>
                <span v-if="c.counts.SKIPPED"> · {{ c.counts.SKIPPED }} bloklagan</span>
                <span v-if="c.counts.PENDING"> · {{ c.counts.PENDING }} navbatda</span>
              </p>
            </template>
            <span v-else class="text-muted-foreground text-xs">hali boshlanmagan</span>
          </TableCell>
          <TableCell class="text-muted-foreground text-sm whitespace-nowrap">
            {{ fmtDateTime(c.scheduledAt) }}<br><span class="text-xs">{{ c.createdByEmail }}</span>
          </TableCell>
          <TableCell class="bg-card sticky right-0 text-right">
            <Button v-if="auth.can(P.NOTIFICATION_SEND) && (c.status === 'SCHEDULED' || c.status === 'SENDING')" variant="ghost" size="sm"
                    @click.stop="cancel(c)"><Ban /> Bekor</Button>
          </TableCell>
        </TableRow>
        <TableEmpty v-if="data && !data.content.length" :colspan="6">
          <div class="flex flex-col items-center gap-2 py-6"><Megaphone class="size-8 opacity-40" />Hali xabar yuborilmagan</div>
        </TableEmpty>
      </TableBody>
    </Table>
  </div>
  <Paginator v-if="data && data.totalPages > 1" class="mt-4" :page="data.page" :total-pages="data.totalPages" :total-elements="data.totalElements" @change="page = $event" />
</template>
