<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  Chart, Filler, LinearScale, LineController, LineElement, PointElement, ScatterController, Tooltip, type ChartDataset,
} from 'chart.js'
import { Activity, BellRing, ClipboardCheck, CloudLightning, Loader2, Plus, Satellite, Trash2 } from '@lucide/vue'
import { toast } from 'vue-sonner'
import ObservationDialog from '@/components/app/ObservationDialog.vue'
import ReviewButtons from '@/components/app/ReviewButtons.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { Button } from '@/components/ui/button'
import { jobTone } from '@/components/app/badges'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { accuracyApi, fieldsApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { FieldHistory, HistoryEventType, JobStatus, ObsCondition } from '@/api/types'
import { fmtDate, fmtDateTime, fmtNum, isoDate } from '@/lib/format'
import { CAUSE, CONDITION, CROP, JOB_STATUS, P, SIDE, WEATHER_KIND, WEATHER_LEVEL } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

Chart.register(LineController, ScatterController, LineElement, PointElement, LinearScale, Tooltip, Filler)

/** L1b: dala holati tarixi — NDVI egri chizig'i (tahlil + monitoring) va tizim nima qilgani/degani */
const props = defineProps<{ fieldId: number; polygon: { coordinates: number[][][] } | null }>()
const auth = useAuth()
const canWrite = computed(() => auth.can(P.FIELD_WRITE))
const adding = ref(false)
const data = ref<FieldHistory | null>(null)
const loading = ref(false)
const period = ref('DEFAULT')                          // DEFAULT | YEAR | season:<id>
const filter = ref<HistoryEventType | 'ALL'>('ALL')
const canvas = ref<HTMLCanvasElement>()
let chart: Chart | null = null

type Tone = 'red' | 'green' | 'gray' | 'amber' | 'blue'
const VERDICT: Record<string, { l: string; t: Tone; c: string }> = {
  DECLINE: { l: 'Sustlashdi', t: 'red', c: '#dc2626' }, STABLE: { l: 'Oʻzgarish yoʻq', t: 'green', c: '#16a34a' },
  HARVEST: { l: 'Pishish/oʻrim', t: 'amber', c: '#d97706' }, NO_CROP: { l: 'Ekin yoʻq', t: 'gray', c: '#9ca3af' },
  BASELINE: { l: 'Boshlandi', t: 'blue', c: '#2563eb' },
}
const NOTIF: Record<string, { l: string; t: Tone }> = {
  PENDING: { l: 'Navbatda', t: 'blue' }, SENDING: { l: 'Yuborilmoqda', t: 'amber' }, SENT: { l: 'Yetdi', t: 'green' },
  FAILED: { l: 'Xato', t: 'red' }, SKIPPED: { l: 'Yuborilmadi', t: 'gray' }, CANCELLED: { l: 'Bekor', t: 'gray' },
}
const TYPE: Record<HistoryEventType, { l: string; icon: unknown }> = {
  ANALYSIS: { l: 'Tahlillar', icon: Satellite }, MONITORING: { l: 'Monitoring', icon: Activity },
  WEATHER: { l: 'Ob-havo', icon: CloudLightning }, NOTIFICATION: { l: 'Xabarlar', icon: BellRing },
  OBSERVATION: { l: 'Kuzatuvlar', icon: ClipboardCheck },
}
const OBS: Record<ObsCondition, { t: Tone; c: string }> = {
  GOOD: { t: 'green', c: '#16a34a' }, WEAK: { t: 'amber', c: '#d97706' }, DAMAGED: { t: 'red', c: '#dc2626' },
  BARE: { t: 'gray', c: '#6b7280' }, HARVESTED: { t: 'blue', c: '#2563eb' },
}

// Ikki bosishli o'chirish (brauzer confirm'i o'rniga): birinchisi — "O'chirilsinmi?", ikkinchisi — o'chiradi
const confirming = ref<number | null>(null)
const removing = ref(false)
async function removeObservation(id: number) {
  if (confirming.value !== id) {
    confirming.value = id
    return
  }
  if (removing.value) return
  removing.value = true
  try {
    await accuracyApi.deleteObservation(id)
    toast.success('Kuzatuv oʻchirildi')
    load()
  } catch (e) {
    toast.error(errorMessage(e))
  } finally {
    confirming.value = null
    removing.value = false
  }
}

const query = computed(() => {
  if (period.value === 'YEAR') {
    const d = new Date()
    d.setFullYear(d.getFullYear() - 1)
    return { from: isoDate(d) }
  }
  const s = period.value.startsWith('season:') && data.value?.seasons.find((x) => `season:${x.id}` === period.value)
  return s ? { from: s.startedOn, to: s.finishedOn ?? undefined } : {}
})

let req = 0                                            // davr tez almashsa — eski javob yangisini bosmasin
async function load() {
  const my = ++req
  loading.value = true
  try {
    const h = await fieldsApi.history(props.fieldId, query.value)
    if (my !== req) return
    data.value = h
    await nextTick()
    draw()
  } catch { /* ixtiyoriy karta — huquq yo'q yoki xato */ } finally {
    if (my === req) loading.value = false
  }
}
onMounted(load)
watch(period, load)
onBeforeUnmount(() => chart?.destroy())

const counts = computed(() => {
  const c: Record<string, number> = {}
  for (const e of data.value?.events ?? []) c[e.type] = (c[e.type] ?? 0) + 1
  return c
})
const events = computed(() => (data.value?.events ?? []).filter((e) => filter.value === 'ALL' || e.type === filter.value))

const day = (s: string) => new Date(`${s.slice(0, 10)}T00:00:00`).getTime()

function draw() {
  const h = data.value
  if (!h || !canvas.value) return
  const analysis = h.ndvi.filter((p) => p.source === 'ANALYSIS')
  const monitoring = h.ndvi.filter((p) => p.source === 'MONITORING')
  const verdicts = new Map(h.events.filter((e) => e.type === 'MONITORING').map((e) => [e.refId, e.status ?? '']))
  const datasets: ChartDataset<'line' | 'scatter', { x: number; y: number }[]>[] = [
    {
      type: 'line', label: 'NDVI (tahlil)', data: analysis.map((p) => ({ x: day(p.date), y: p.ndvi })),
      borderColor: '#16a34a', backgroundColor: 'rgba(22,163,74,0.08)', fill: true, tension: 0.3, pointRadius: 2, borderWidth: 2,
    },
    {
      type: 'scatter', label: 'Monitoring', data: monitoring.map((p) => ({ x: day(p.date), y: p.ndvi })),
      pointRadius: 5, pointHoverRadius: 7, pointBorderColor: '#fff', pointBorderWidth: 1.5,
      pointBackgroundColor: monitoring.map((p) => VERDICT[verdicts.get(p.refId) ?? '']?.c ?? '#6b7280'),
    },
  ]
  const weather = h.events.filter((e) => e.type === 'WEATHER' && e.data.eventDate)
  if (weather.length) {
    datasets.push({
      type: 'scatter', label: 'Ob-havo', data: weather.map((e) => ({ x: day(e.data.eventDate), y: 0.02 })),
      pointStyle: 'triangle', pointRadius: 6, pointBackgroundColor: weather.map((e) => (e.status === 'DANGER' ? '#dc2626' : '#f59e0b')),
    })
  }
  const obs = h.events.filter((e) => e.type === 'OBSERVATION')
  if (obs.length) {
    datasets.push({
      type: 'scatter', label: 'Kuzatuv', data: obs.map((e) => ({ x: day(e.data.observedOn), y: 0.96 })),
      pointStyle: 'rectRot', pointRadius: 7, pointBorderColor: '#fff', pointBorderWidth: 1.5,
      pointBackgroundColor: obs.map((e) => OBS[e.status as ObsCondition]?.c ?? '#6b7280'),
    })
  }
  const grid = 'rgba(127,127,127,0.15)', tick = '#8a8a8a'
  chart?.destroy()
  chart = new Chart(canvas.value, {
    type: 'line',
    data: { datasets },
    options: {
      responsive: true, maintainAspectRatio: false, animation: false,
      interaction: { mode: 'nearest', intersect: false },
      scales: {
        x: {
          type: 'linear', min: day(h.from), max: day(h.to), grid: { color: grid },
          ticks: { color: tick, maxTicksLimit: 8, callback: (v) => fmtDate(isoDate(new Date(Number(v)))) },
        },
        y: { min: 0, max: 1, grid: { color: grid }, ticks: { color: tick, stepSize: 0.2 } },
      },
      plugins: {
        tooltip: {
          callbacks: {
            title: (items) => fmtDate(isoDate(new Date(items[0]!.parsed.x as number))),
            label: (c) => {
              if (c.dataset.label === 'Ob-havo') {
                const e = weather[c.dataIndex]!
                return `${WEATHER_KIND[e.data.kind] ?? e.data.kind} · ${WEATHER_LEVEL[e.status ?? ''] ?? e.status}`
              }
              if (c.dataset.label === 'Kuzatuv') {
                const e = obs[c.dataIndex]!
                return `Kuzatuv: ${CONDITION[e.status as ObsCondition]}${e.data.cause ? ` · ${CAUSE[e.data.cause as keyof typeof CAUSE]}` : ''}`
              }
              if (c.dataset.label === 'Monitoring') {
                const v = verdicts.get(monitoring[c.dataIndex]!.refId) ?? ''
                return `Monitoring: ${fmtNum(c.parsed.y as number, 2)} · ${VERDICT[v]?.l ?? v}`
              }
              return `${c.dataset.label}: ${fmtNum(c.parsed.y as number, 2)}`
            },
          },
        },
      },
    },
  })
}

function seasonLabel(s: FieldHistory['seasons'][number]) {
  return `${CROP[s.crop]} · ${fmtDate(s.startedOn)} – ${s.finishedOn ? fmtDate(s.finishedOn) : 'hozir'}`
}
</script>

<template>
  <Card class="lg:col-span-3">
    <CardHeader class="flex flex-row flex-wrap items-start justify-between gap-3">
      <div>
        <CardTitle class="flex items-center gap-2 text-base">Holat tarixi <Loader2 v-if="loading" class="size-4 animate-spin" /></CardTitle>
        <CardDescription>Tizim nimani koʻrgan va nima degan — NDVI, monitoring, ob-havo, fermerga xabarlar</CardDescription>
      </div>
      <div class="flex flex-wrap items-center gap-2">
      <Button v-if="canWrite" variant="outline" size="sm" @click="adding = true"><Plus /> Kuzatuv</Button>
      <Select v-model="period">
        <SelectTrigger class="w-64"><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectItem value="DEFAULT">Joriy + oldingi mavsum</SelectItem>
          <SelectItem v-for="s in data?.seasons ?? []" :key="s.id" :value="`season:${s.id}`">{{ seasonLabel(s) }}</SelectItem>
          <SelectItem value="YEAR">Oxirgi 12 oy</SelectItem>
        </SelectContent>
      </Select>
      </div>
    </CardHeader>
    <CardContent v-if="data" class="space-y-5">
      <div>
        <div class="text-muted-foreground mb-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs">
          <span>{{ fmtDate(data.from) }} – {{ fmtDate(data.to) }}</span>
          <span class="flex items-center gap-1.5"><i class="inline-block h-0.5 w-4 bg-green-600" /> Tahlil NDVI</span>
          <span v-for="(v, k) in VERDICT" :key="k" class="flex items-center gap-1.5">
            <i class="inline-block size-2.5 rounded-full" :style="{ background: v.c }" /> {{ v.l }}</span>
          <span class="flex items-center gap-1.5"><i class="inline-block size-0 border-x-[5px] border-b-[9px] border-x-transparent border-b-amber-500" /> Ob-havo</span>
          <span class="flex items-center gap-1.5"><i class="inline-block size-2.5 rotate-45 bg-amber-600" /> Kuzatuv</span>
        </div>
        <div class="relative h-64">
          <canvas ref="canvas" />
          <p v-if="!data.ndvi.length" class="text-muted-foreground absolute inset-0 grid place-items-center text-sm">
            Bu davrda NDVI maʼlumoti yoʻq — tahlil hali bajarilmagan yoki surat yoʻq
          </p>
        </div>
      </div>

      <div class="space-y-2">
        <div class="flex flex-wrap gap-1.5">
          <button type="button" :aria-pressed="filter === 'ALL'" class="h-7 rounded-md border px-2.5 text-xs font-medium"
                  :class="filter === 'ALL' ? 'bg-primary text-primary-foreground border-primary' : 'text-muted-foreground hover:bg-accent'"
                  @click="filter = 'ALL'">Hammasi · {{ data.events.length }}</button>
          <button v-for="(t, k) in TYPE" :key="k" type="button" :aria-pressed="filter === k"
                  class="flex h-7 items-center gap-1.5 rounded-md border px-2.5 text-xs font-medium"
                  :class="filter === k ? 'bg-primary text-primary-foreground border-primary' : 'text-muted-foreground hover:bg-accent'"
                  @click="filter = k"><component :is="t.icon" class="size-3.5" /> {{ t.l }} · {{ counts[k] ?? 0 }}</button>
        </div>

        <ol v-if="events.length" class="divide-y rounded-lg border">
          <li v-for="e in events" :key="`${e.type}-${e.refId}`" class="flex gap-3 px-3 py-2.5 text-sm">
            <component :is="TYPE[e.type].icon" class="text-muted-foreground mt-0.5 size-4 shrink-0" />
            <div class="min-w-0 flex-1 space-y-0.5">
              <div class="flex flex-wrap items-center gap-2">
                <template v-if="e.type === 'ANALYSIS'">
                  <RouterLink :to="`/analyses/${e.refId}`" class="font-medium hover:underline">Tahlil #{{ e.refId }}</RouterLink>
                  <StatusBadge :tone="jobTone[e.status as JobStatus]" :label="JOB_STATUS[e.status as JobStatus]" />
                </template>
                <template v-else-if="e.type === 'MONITORING'">
                  <span class="font-medium">Monitoring · surat {{ fmtDate(e.data.sceneDate) }}</span>
                  <StatusBadge :tone="VERDICT[e.status ?? '']?.t ?? 'gray'" :label="VERDICT[e.status ?? '']?.l ?? e.status ?? '—'" />
                </template>
                <template v-else-if="e.type === 'WEATHER'">
                  <span class="font-medium">{{ WEATHER_KIND[e.data.kind] ?? e.data.kind }} · {{ fmtDate(e.data.eventDate) }}</span>
                  <StatusBadge :tone="e.status === 'DANGER' ? 'red' : 'amber'" :label="WEATHER_LEVEL[e.status ?? ''] ?? e.status ?? ''" />
                </template>
                <template v-else-if="e.type === 'OBSERVATION'">
                  <span class="font-medium">Kuzatuv · {{ fmtDate(e.data.observedOn) }}</span>
                  <StatusBadge :tone="OBS[e.status as ObsCondition]?.t ?? 'gray'" :label="CONDITION[e.status as ObsCondition] ?? e.status ?? ''" />
                  <span v-if="e.data.cause" class="text-xs">{{ CAUSE[e.data.cause as keyof typeof CAUSE] }}</span>
                </template>
                <template v-else>
                  <span class="font-medium">Xabar</span>
                  <StatusBadge :tone="NOTIF[e.status ?? '']?.t ?? 'gray'" :label="NOTIF[e.status ?? '']?.l ?? e.status ?? ''" />
                </template>
                <span class="text-muted-foreground ml-auto text-xs tabular-nums">{{ e.type === 'OBSERVATION' ? (e.data.author ?? '') : fmtDateTime(e.at) }}</span>
                <button v-if="e.type === 'OBSERVATION' && canWrite" type="button" :disabled="removing"
                        class="flex items-center gap-1 text-xs" aria-label="Kuzatuvni oʻchirish"
                        :class="confirming === e.refId ? 'text-destructive font-medium' : 'text-muted-foreground hover:text-destructive'"
                        @click="removeObservation(e.refId)" @blur="confirming === e.refId && !removing && (confirming = null)">
                  <Trash2 class="size-3.5" /><span v-if="confirming === e.refId">Oʻchirilsinmi?</span></button>
              </div>
              <p class="text-muted-foreground text-xs">
                <template v-if="e.type === 'ANALYSIS'">
                  <template v-if="e.data.peakNdvi != null">
                    NDVI choʻqqisi {{ fmtNum(e.data.peakNdvi, 2) }} ({{ fmtDate(e.data.peakDate) }})
                    · zonalar {{ e.data.zones?.good_pct }} / {{ e.data.zones?.mid_pct }} / {{ e.data.zones?.weak_pct }}%
                    <template v-if="e.data.zones?.weak_side"> · sust: {{ SIDE[e.data.zones.weak_side] }}</template>
                    · {{ e.data.scenesUsed }} surat ({{ e.data.scenesRejected }} bulutli)
                  </template>
                  <span v-else-if="e.data.error" class="break-all">{{ e.data.error }}</span>
                </template>
                <template v-else-if="e.type === 'MONITORING'">
                  NDVI {{ fmtNum(e.data.ndvi, 2) }}<template v-if="e.data.ndviRef != null"> ← {{ fmtNum(e.data.ndviRef, 2) }} ({{ fmtDate(e.data.refDate) }})</template>
                  <template v-if="e.data.declinePct != null"> · {{ e.data.declinePct }}% sustlashgan<template v-if="e.data.declineSide">, {{ SIDE[e.data.declineSide] }}</template></template>
                  <template v-if="e.data.improvePct != null"> · {{ e.data.improvePct }}% yaxshilangan</template>
                </template>
                <template v-else-if="e.type === 'WEATHER'">Qiymat: {{ fmtNum(e.data.value, 1) }}</template>
                <template v-else-if="e.type === 'OBSERVATION'">
                  {{ e.data.lat != null ? `Nuqta: ${e.data.lat}, ${e.data.lon}` : 'Butun dala' }}<template v-if="e.data.note"> · {{ e.data.note }}</template>
                </template>
                <template v-else>
                  {{ e.data.preview }}<span v-if="e.data.lastError" class="block break-all">{{ e.data.lastError }}</span>
                </template>
              </p>
              <ReviewButtons v-if="(e.type === 'ANALYSIS' && e.status === 'DONE') || e.type === 'MONITORING'"
                             :type="e.type" :id="e.refId" :review="e.data.review ?? null" :readonly="!canWrite" class="pt-1" />
            </div>
          </li>
        </ol>
        <p v-else class="text-muted-foreground rounded-lg border border-dashed p-6 text-center text-sm">Bu davrda hodisa yoʻq</p>
      </div>
    </CardContent>
  </Card>
  <ObservationDialog v-if="canWrite" v-model:open="adding" :field-id="fieldId" :polygon="polygon" @saved="load" />
</template>
