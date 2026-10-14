<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import PageHeader from '@/components/app/PageHeader.vue'
import Paginator from '@/components/app/Paginator.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import ImageLightbox from '@/components/app/ImageLightbox.vue'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { advisorApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { AdvisorCategory, AdvisorItem, AdvisorStats, AdvisorStatus, Page } from '@/api/types'
import { fmtDateTime, fmtNum } from '@/lib/format'
import { P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

/**
 * AI maslahat — tarix va statistika. Fermer rasmi → Claude tavsiyasi fermerga yakuniy boradi ("agronom bilan
 * maslahatlashing" ogohlantirishi bilan; agronom tekshiruvi yo'q). ?id=12 — so'rovni ochib beradi (Fayllar sahifasidan).
 */
type Filter = 'ALL' | AdvisorStatus
const route = useRoute()
const router = useRouter()
const auth = useAuth()

const filter = ref<Filter>('ALL')
const page = ref(0)
const data = ref<Page<AdvisorItem> | null>(null)
const stats = ref<AdvisorStats | null>(null)
const open = ref<AdvisorItem | null>(null)
const viewing = ref<number | null>(null)

const CATEGORY: Record<AdvisorCategory, { l: string; t: 'red' | 'amber' | 'blue' | 'green' | 'gray' }> = {
  DISEASE: { l: 'Kasallik', t: 'red' }, PEST: { l: 'Zararkunanda', t: 'red' }, NUTRIENT: { l: 'Oziqa yetishmasligi', t: 'amber' },
  WATER: { l: 'Suv', t: 'blue' }, ABIOTIC: { l: 'Ob-havo / kimyoviy', t: 'amber' }, HEALTHY: { l: 'Sogʻlom', t: 'green' },
  UNCLEAR: { l: 'Aniq emas', t: 'gray' }, NOT_PLANT: { l: 'Oʻsimlik emas', t: 'gray' },
}
const STATUS: Record<AdvisorStatus, { l: string; t: 'blue' | 'red' | 'green' }> = {
  PENDING: { l: 'Tahlil qilinmoqda', t: 'blue' }, ANSWERED: { l: 'Javob berildi', t: 'green' },
  FAILED: { l: 'AI javob bermadi', t: 'red' },
}
const FILTERS: [Filter, string][] = [['ALL', 'Hammasi'], ['ANSWERED', 'Javob berilgan'], ['FAILED', 'Xato'], ['PENDING', 'Navbatda']]
// Taxminiy narx, $ / 1M token (kirish, chiqish) — Anthropic narxlari o'zgarsa yangilang
const PRICE: Record<string, [number, number]> = {
  'claude-opus-5-5': [4, 20], 'claude-sonnet-5-5': [2, 10], 'claude-haiku-5-5': [0.1, 0.5],
}

const photo = (h: string) => `/api/v1/files/${h}`
const sum = (o?: Partial<Record<string, number>>) => Object.values(o ?? {}).reduce((a: number, b) => a + (b ?? 0), 0)

const cost = computed(() => {
  let usd = 0
  let unknown = false
  for (const t of stats.value?.tokens ?? []) {
    const p = PRICE[t.model]
    if (!p) { unknown = true; continue }
    usd += (t.inputTokens * p[0] + t.outputTokens * p[1]) / 1e6
  }
  return { usd, unknown }
})
const answered = computed(() => stats.value?.byStatus.ANSWERED ?? 0)
const failedPct = computed(() => {
  const total = sum(stats.value?.byStatus)
  return total ? Math.round(((stats.value?.byStatus.FAILED ?? 0) / total) * 100) : 0
})
// Eng ko'p uchragan muammo turlari (ko'pdan kamga)
const categories = computed(() => Object.entries(stats.value?.byCategory ?? {})
  .sort((a, b) => (b[1] ?? 0) - (a[1] ?? 0)) as [AdvisorCategory, number][])
const images = computed(() => open.value
  ? [{ url: photo(open.value.photoHashId), name: `advisor-${open.value.id}.jpg`, caption: open.value.farmerNote ?? '' }] : [])

async function load() {
  try {
    data.value = await advisorApi.list({ filter: filter.value, page: page.value, size: 20 })
  } catch (e) { toast.error(errorMessage(e)) }
}
watch(filter, () => { if (page.value === 0) load(); else page.value = 0 })
watch(page, load)
onMounted(async () => {
  load()
  try { stats.value = await advisorApi.stats() } catch { /* statistika ixtiyoriy — ro'yxat baribir ishlaydi */ }
  const id = Number(route.query.id)
  if (id) {
    try { open.value = await advisorApi.get(id) } catch (e) { toast.error(errorMessage(e)) }
  }
})

function close(v: boolean) {
  if (v) return
  open.value = null
  if (route.query.id) router.replace({ query: {} })
}
</script>

<template>
  <PageHeader title="AI maslahat" description="Fermer rasmlari va sunʼiy intellekt tavsiyalari. Tavsiya fermerga «agronom bilan maslahatlashing» ogohlantirishi bilan boradi" />

  <div class="mb-4 grid grid-cols-2 gap-3 md:grid-cols-4">
    <div class="bg-background rounded-xl border p-3 shadow-xs">
      <p class="text-muted-foreground text-xs">Javob berilgan · 30 kun</p>
      <p class="mt-1 text-xl font-semibold tabular-nums">{{ fmtNum(answered) }}</p>
    </div>
    <div class="bg-background rounded-xl border p-3 shadow-xs">
      <p class="text-muted-foreground text-xs">AI javob bermagan · 30 kun</p>
      <p class="mt-1 text-xl font-semibold tabular-nums">{{ failedPct }}%</p>
    </div>
    <div class="bg-background rounded-xl border p-3 shadow-xs">
      <p class="text-muted-foreground text-xs">Koʻp uchragan · 30 kun</p>
      <p class="mt-1 truncate text-sm font-medium">
        <template v-if="categories.length">{{ categories.slice(0, 2).map(([k, n]) => `${CATEGORY[k]?.l ?? k} (${n})`).join(', ') }}</template>
        <span v-else class="text-muted-foreground">—</span>
      </p>
    </div>
    <div class="bg-background rounded-xl border p-3 shadow-xs">
      <p class="text-muted-foreground text-xs">Claude xarajati · 30 kun (taxminan)</p>
      <p class="mt-1 text-xl font-semibold tabular-nums">${{ cost.usd.toFixed(2) }}<span v-if="cost.unknown" class="text-muted-foreground text-xs"> + nomaʼlum model</span></p>
    </div>
  </div>

  <nav class="mb-4 flex gap-1 overflow-x-auto border-b">
    <button v-for="f in FILTERS" :key="f[0]" type="button"
            :class="['-mb-px shrink-0 border-b-2 px-3 py-2.5 text-sm font-medium transition-colors',
                     filter === f[0] ? 'border-primary text-foreground' : 'text-muted-foreground hover:text-foreground border-transparent']"
            @click="filter = f[0]">{{ f[1] }}</button>
  </nav>

  <div class="bg-background rounded-xl border">
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead class="w-14" /><TableHead>Fermer</TableHead><TableHead>AI tavsiyasi</TableHead>
          <TableHead>Holat</TableHead><TableHead>Vaqt</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        <template v-if="!data"><TableRow v-for="i in 5" :key="i"><TableCell :colspan="5"><Skeleton class="h-10 w-full" /></TableCell></TableRow></template>
        <TableRow v-for="r in data?.content ?? []" :key="r.id" class="cursor-pointer" @click="open = r">
          <TableCell><img :src="`${photo(r.photoHashId)}/thumb`" alt="" class="size-10 rounded-md border object-cover" loading="lazy" /></TableCell>
          <TableCell>
            <div class="font-medium">{{ r.farmerName ?? `#${r.farmerId}` }}</div>
            <div class="text-muted-foreground text-xs">№{{ r.id }}<template v-if="r.fieldName"> · {{ r.fieldName }}</template></div>
          </TableCell>
          <TableCell class="max-w-md">
            <template v-if="r.diagnosis">
              <StatusBadge :tone="CATEGORY[r.diagnosis.category].t" :label="CATEGORY[r.diagnosis.category].l" />
              <span v-if="r.diagnosis.urgent" class="ml-1 text-xs font-medium text-red-600">shoshilinch</span>
              <p class="text-muted-foreground mt-1 truncate text-sm" :title="r.diagnosis.summary">{{ r.diagnosis.summary }}</p>
            </template>
            <p v-else-if="r.aiError" class="truncate text-xs text-red-600" :title="r.aiError">{{ r.aiError }}</p>
          </TableCell>
          <TableCell><StatusBadge :tone="STATUS[r.status].t" :label="STATUS[r.status].l" /></TableCell>
          <TableCell class="text-muted-foreground text-sm whitespace-nowrap">{{ fmtDateTime(r.createdDate) }}</TableCell>
        </TableRow>
        <TableEmpty v-if="data && !data.content.length" :colspan="5">Soʻrov yoʻq</TableEmpty>
      </TableBody>
    </Table>
  </div>
  <Paginator v-if="data && data.totalPages > 1" class="mt-4" :page="data.page" :total-pages="data.totalPages" :total-elements="data.totalElements" @change="page = $event" />

  <Sheet :open="!!open" @update:open="close">
    <SheetContent class="w-full overflow-y-auto sm:max-w-xl">
      <template v-if="open">
        <SheetHeader>
          <SheetTitle>AI maslahat №{{ open.id }}</SheetTitle>
          <SheetDescription>
            <RouterLink v-if="auth.can(P.FARMER_READ)" :to="`/farmers/${open.farmerId}`" class="hover:underline">{{ open.farmerName ?? `#${open.farmerId}` }}</RouterLink>
            <template v-else>{{ open.farmerName ?? `#${open.farmerId}` }}</template>
            <template v-if="open.fieldName"> · {{ open.fieldName }}</template> · {{ fmtDateTime(open.createdDate) }} · til: {{ open.language }}
          </SheetDescription>
        </SheetHeader>

        <div class="space-y-4 px-4 pb-6 text-sm">
          <button type="button" class="block w-full" aria-label="Rasmni kattalashtirish" @click="viewing = 0">
            <img :src="photo(open.photoHashId)" alt="Fermer rasmi" class="max-h-80 w-full rounded-lg border object-contain" />
          </button>
          <p v-if="open.farmerNote" class="bg-muted/50 rounded-lg p-3"><span class="text-muted-foreground">Fermer izohi:</span> {{ open.farmerNote }}</p>

          <div v-if="open.diagnosis" class="space-y-3 rounded-lg border p-3">
            <div class="flex flex-wrap items-center gap-2">
              <StatusBadge :tone="CATEGORY[open.diagnosis.category].t" :label="CATEGORY[open.diagnosis.category].l" />
              <span v-if="open.diagnosis.urgent" class="text-xs font-medium text-red-600">⚠️ shoshilinch</span>
              <span class="text-muted-foreground ml-auto text-xs">{{ open.aiModel }} · {{ fmtNum(open.inputTokens ?? 0) }} + {{ fmtNum(open.outputTokens ?? 0) }} token</span>
            </div>
            <p>{{ open.diagnosis.summary }}</p>
            <div v-if="open.diagnosis.causes.length">
              <p class="font-medium">Ehtimoliy sabablar</p>
              <ul class="mt-1 space-y-1">
                <li v-for="(c, i) in open.diagnosis.causes" :key="i">
                  <b>{{ c.name }}</b> — {{ c.confidencePct }}%<span class="text-muted-foreground block text-xs">{{ c.evidence }}</span>
                </li>
              </ul>
            </div>
            <div v-if="open.diagnosis.checks.length"><p class="font-medium">Tekshirish</p><ul class="list-disc pl-5"><li v-for="(x, i) in open.diagnosis.checks" :key="i">{{ x }}</li></ul></div>
            <div v-if="open.diagnosis.actions.length"><p class="font-medium">Birinchi qadamlar</p><ul class="list-disc pl-5"><li v-for="(x, i) in open.diagnosis.actions" :key="i">{{ x }}</li></ul></div>
            <p v-if="open.diagnosis.photoAdvice" class="text-muted-foreground">📸 {{ open.diagnosis.photoAdvice }}</p>
          </div>
          <p v-else-if="open.aiError" class="rounded-lg border border-red-200 p-3 text-red-700">AI: {{ open.aiError }} ({{ open.attempts }} urinish)</p>
        </div>
      </template>
    </SheetContent>
  </Sheet>
  <!-- Sheet'dan TASHQARIDA: ichida bo'lsa Sheet lightbox'dagi bosishni "tashqariga bosish" deb yopib yuboradi (FilesView pattern'i) -->
  <ImageLightbox v-model:index="viewing" :images="images" />
</template>
