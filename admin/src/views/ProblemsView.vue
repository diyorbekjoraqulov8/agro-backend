<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import PageHeader from '@/components/app/PageHeader.vue'
import Paginator from '@/components/app/Paginator.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { problemsApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Page, ProblemItem, ProblemSource, ProblemSummary, ServiceStatus } from '@/api/types'
import { fmtDateTime, fmtNum } from '@/lib/format'
import { P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

/**
 * Muammolar — fermer va xizmat xatolari bir joyda (ProblemRecorder yozadi) + tashqi xizmatlar holati.
 * Bir xil xato bitta qatorda (necha marta takrorlangani bilan). ?farmerId=5 — shu fermerniki (fermer sahifasidan).
 */
type State = 'OPEN' | 'RESOLVED' | 'ALL'
const route = useRoute()
const router = useRouter()
const auth = useAuth()

const SOURCE: Record<ProblemSource, string> = {
  BOT: 'Bot', ANALYSIS: 'Tahlil', MONITORING: 'Monitoring', ADVISOR: 'AI maslahat', NOTIFICATION: 'Bildirishnoma',
  WEATHER: 'Ob-havo', IRRIGATION: 'Sugʻorish', API: 'Admin API', SYSTEM: 'Tizim (servis)',
}
const HEALTH: Record<ServiceStatus, { l: string; t: 'green' | 'amber' | 'red' | 'gray' }> = {
  UP: { l: 'Ishlayapti', t: 'green' }, WARN: { l: 'Eʼtibor bering', t: 'amber' }, DOWN: { l: 'Ishlamayapti', t: 'red' }, OFF: { l: 'Oʻchiq', t: 'gray' },
}
// Muammo qaysi yozuvga tegishli — o'sha sahifaga havola (bo'lmasa — havolasiz)
const REF_LINK: Record<string, (id: number) => string> = {
  ANALYSIS: (id) => `/analyses/${id}`, ADVISOR_REQUEST: (id) => `/advisor?id=${id}`, NOTIFICATION: () => '/notifications',
}
const REF_LABEL: Record<string, string> = { ANALYSIS: 'Tahlil', ADVISOR_REQUEST: 'AI soʻrov', NOTIFICATION: 'Bildirishnoma' }

const state = ref<State>('OPEN')
const source = ref<ProblemSource | null>(null)
const page = ref(0)
const data = ref<Page<ProblemItem> | null>(null)
const summary = ref<ProblemSummary | null>(null)
const open = ref<ProblemItem | null>(null)
const busy = ref(false)

const farmerId = computed(() => Number(route.query.farmerId) || null)
const canManage = computed(() => auth.can(P.PROBLEMS_MANAGE))
const openBySource = computed(() => Object.fromEntries((summary.value?.sources ?? []).map((s) => [s.source, s])))

async function load() {
  try {
    data.value = await problemsApi.list({ state: state.value, source: source.value ?? undefined,
      farmerId: farmerId.value ?? undefined, page: page.value, size: 30 })
  } catch (e) { toast.error(errorMessage(e)) }
}
async function loadSummary() {
  try { summary.value = await problemsApi.summary() } catch { /* holat ixtiyoriy — ro'yxat baribir ishlaydi */ }
}
watch([state, source, farmerId], () => { if (page.value === 0) load(); else page.value = 0 })
watch(page, load)
onMounted(() => { load(); loadSummary() })

async function show(r: ProblemItem) {
  open.value = r
  try {
    const full = await problemsApi.get(r.id)
    if (open.value?.id === r.id) open.value = full      // shu orada yopilgan/boshqasi ochilgan bo'lsa — qayta ochmaymiz
  } catch (e) { toast.error(errorMessage(e)) }
}
async function resolve(r: ProblemItem) {
  busy.value = true
  try {
    await problemsApi.resolve(r.id)
    toast.success('Hal qilindi deb belgilandi')
    open.value = null
    load(); loadSummary()
  } catch (e) { toast.error(errorMessage(e)) } finally { busy.value = false }
}
async function resolveAll(s: ProblemSource) {
  busy.value = true
  try {
    const { resolved } = await problemsApi.resolveAll(s)
    toast.success(`${resolved} ta muammo hal qilindi deb belgilandi`)
    load(); loadSummary()
  } catch (e) { toast.error(errorMessage(e)) } finally { busy.value = false }
}
function clearFarmer() { router.replace({ query: {} }) }
</script>

<template>
  <PageHeader title="Muammolar" description="Fermerlarda va xizmatlarda chiqqan xatolar. Bir xil xato bitta qatorda — necha marta takrorlangani bilan" />

  <!-- Xizmatlar holati -->
  <div class="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
    <template v-if="!summary"><Skeleton v-for="i in 4" :key="i" class="h-20 rounded-xl" /></template>
    <div v-for="s in summary?.services ?? []" :key="s.name" class="bg-background rounded-xl border p-3 shadow-xs">
      <div class="flex items-center justify-between gap-2">
        <p class="truncate text-sm font-medium">{{ s.name }}</p>
        <StatusBadge :tone="HEALTH[s.status].t" :label="HEALTH[s.status].l" />
      </div>
      <p class="text-muted-foreground mt-1 text-xs">{{ s.detail }}</p>
    </div>
  </div>

  <div v-if="farmerId" class="bg-muted/50 mb-3 flex items-center gap-2 rounded-lg px-3 py-2 text-sm">
    Faqat fermer #{{ farmerId }} muammolari
    <Button size="sm" variant="ghost" class="ml-auto" @click="clearFarmer">Hammasini koʻrsatish</Button>
  </div>

  <nav class="mb-2 flex gap-1 overflow-x-auto border-b">
    <button v-for="f in ([['OPEN', 'Ochiq'], ['RESOLVED', 'Hal qilingan'], ['ALL', 'Hammasi']] as [State, string][])" :key="f[0]" type="button"
            :class="['-mb-px shrink-0 border-b-2 px-3 py-2.5 text-sm font-medium transition-colors',
                     state === f[0] ? 'border-primary text-foreground' : 'text-muted-foreground hover:text-foreground border-transparent']"
            @click="state = f[0]">{{ f[1] }}</button>
  </nav>
  <div class="mb-4 flex flex-wrap gap-1.5">
    <button type="button" :class="['rounded-full border px-3 py-1 text-xs', source === null ? 'bg-primary text-primary-foreground border-primary' : 'hover:bg-muted']"
            @click="source = null">Barcha manbalar</button>
    <button v-for="(l, k) in SOURCE" :key="k" type="button"
            :class="['rounded-full border px-3 py-1 text-xs', source === k ? 'bg-primary text-primary-foreground border-primary' : 'hover:bg-muted']"
            @click="source = k">
      {{ l }}<span v-if="openBySource[k]?.open" class="ml-1 font-semibold tabular-nums">{{ openBySource[k].open }}</span>
    </button>
    <Button v-if="canManage && source && state === 'OPEN' && openBySource[source]?.open" size="sm" variant="outline" class="ml-auto h-7"
            :disabled="busy" @click="resolveAll(source)">Hammasini hal qilindi</Button>
  </div>

  <div class="bg-background rounded-xl border">
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Xato</TableHead><TableHead>Fermer</TableHead><TableHead>Manba</TableHead>
          <TableHead class="text-right">Marta</TableHead><TableHead>Oxirgi marta</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        <template v-if="!data"><TableRow v-for="i in 5" :key="i"><TableCell :colspan="5"><Skeleton class="h-10 w-full" /></TableCell></TableRow></template>
        <TableRow v-for="r in data?.content ?? []" :key="r.id" class="cursor-pointer" @click="show(r)">
          <TableCell class="max-w-lg">
            <div class="flex items-start gap-2">
              <span :class="['mt-1.5 size-2 shrink-0 rounded-full', r.level === 'ERROR' ? 'bg-red-500' : 'bg-amber-400']" :title="r.level === 'ERROR' ? 'Xato' : 'Ogohlantirish'" />
              <p class="truncate text-sm" :title="r.message">{{ r.message }}</p>
            </div>
            <p v-if="r.resolvedAt" class="text-muted-foreground mt-0.5 pl-4 text-xs">✓ hal qilindi · {{ r.resolvedByEmail ?? '' }}</p>
          </TableCell>
          <TableCell>
            <template v-if="r.farmerId">
              <div class="font-medium">{{ r.farmerName ?? `#${r.farmerId}` }}</div>
              <div v-if="r.fieldName" class="text-muted-foreground text-xs">{{ r.fieldName }}</div>
            </template>
            <span v-else class="text-muted-foreground text-xs">—</span>
          </TableCell>
          <TableCell><StatusBadge :tone="r.level === 'ERROR' ? 'red' : 'amber'" :label="SOURCE[r.source]" /></TableCell>
          <TableCell class="text-right tabular-nums">{{ fmtNum(r.occurrences) }}</TableCell>
          <TableCell class="text-muted-foreground text-sm whitespace-nowrap">{{ fmtDateTime(r.lastSeen) }}</TableCell>
        </TableRow>
        <TableEmpty v-if="data && !data.content.length" :colspan="5">{{ state === 'OPEN' ? 'Ochiq muammo yoʻq 🎉' : 'Muammo yoʻq' }}</TableEmpty>
      </TableBody>
    </Table>
  </div>
  <Paginator v-if="data && data.totalPages > 1" class="mt-4" :page="data.page" :total-pages="data.totalPages" :total-elements="data.totalElements" @change="page = $event" />

  <Sheet :open="!!open" @update:open="(v: boolean) => { if (!v) open = null }">
    <SheetContent class="w-full overflow-y-auto sm:max-w-2xl">
      <template v-if="open">
        <SheetHeader>
          <SheetTitle>{{ SOURCE[open.source] }} · muammo №{{ open.id }}</SheetTitle>
          <SheetDescription>
            {{ open.level === 'ERROR' ? 'Xato' : 'Ogohlantirish (fermer tomonidagi sabab)' }} ·
            {{ fmtNum(open.occurrences) }} marta · birinchi {{ fmtDateTime(open.firstSeen) }} · oxirgi {{ fmtDateTime(open.lastSeen) }}
          </SheetDescription>
        </SheetHeader>
        <div class="space-y-4 px-4 pb-6 text-sm">
          <p class="rounded-lg border p-3 break-words">{{ open.message }}</p>
          <div class="flex flex-wrap gap-2">
            <RouterLink v-if="open.farmerId && auth.can(P.FARMER_READ)" :to="`/farmers/${open.farmerId}`">
              <Button size="sm" variant="outline">Fermer: {{ open.farmerName ?? `#${open.farmerId}` }}</Button>
            </RouterLink>
            <RouterLink v-if="open.fieldId && auth.can(P.FIELD_READ)" :to="`/fields/${open.fieldId}`">
              <Button size="sm" variant="outline">Dala: {{ open.fieldName ?? `#${open.fieldId}` }}</Button>
            </RouterLink>
            <RouterLink v-if="open.refType && open.refId && REF_LINK[open.refType]" :to="REF_LINK[open.refType](open.refId)">
              <Button size="sm" variant="outline">{{ REF_LABEL[open.refType] ?? open.refType }} №{{ open.refId }}</Button>
            </RouterLink>
          </div>
          <details v-if="open.detail" class="rounded-lg border">
            <summary class="cursor-pointer px-3 py-2 font-medium">Texnik tafsilot (stack trace)</summary>
            <pre class="bg-muted/50 max-h-96 overflow-auto px-3 py-2 text-xs whitespace-pre-wrap">{{ open.detail }}</pre>
          </details>
          <p v-if="open.resolvedAt" class="text-muted-foreground">✓ Hal qilindi: {{ fmtDateTime(open.resolvedAt) }} · {{ open.resolvedByEmail }}</p>
          <Button v-else-if="canManage" :disabled="busy" @click="resolve(open)">Hal qilindi</Button>
          <p class="text-muted-foreground text-xs">Hal qilingan xato yana takrorlansa — yangi qator boʻlib qaytadi.</p>
        </div>
      </template>
    </SheetContent>
  </Sheet>
</template>
