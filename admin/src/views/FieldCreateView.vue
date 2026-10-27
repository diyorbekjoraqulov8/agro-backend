<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useEventListener } from '@vueuse/core'
import {
  ArrowLeft, CircleAlert, CircleCheck, CircleDashed, Copy, Eraser, FileUp, Loader2, LocateFixed, MapPin,
  Scan, Search, Tags, Undo2, X,
} from '@lucide/vue'
import { toast } from 'vue-sonner'
import FieldDrawMap from '@/components/app/FieldDrawMap.vue'
import IconAction from '@/components/app/IconAction.vue'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { Textarea } from '@/components/ui/textarea'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { fieldsApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Crop, IrrigationType } from '@/api/types'
import { fmtNum, isoDate } from '@/lib/format'
import { areaHa, type LonLat, parseLatLon, parseRing, perimeterM, selfIntersects, toGeoJson, TooManyVertices } from '@/lib/geo'
import { CROP, IRRIGATION } from '@/lib/labels'

const router = useRouter()
const NONE = 'NONE'
const MAX_VERTICES = 200                                   // backend Rings.MAX_VERTICES
const DRAFT_KEY = 'dropy.labFieldDraft'
const map = ref<InstanceType<typeof FieldDrawMap>>()
const points = ref<LonLat[]>([])
const form = ref({ name: '', crop: 'WHEAT' as Crop, sownOn: '', irrigation: NONE as IrrigationType | typeof NONE })
const saving = ref(false)
const labels = ref(true)
const today = isoDate(new Date())

const area = computed(() => areaHa(points.value))
const perimeter = computed(() => perimeterM(points.value))
const tooMany = computed(() => points.value.length > MAX_VERTICES)
// O(n²) — 200 dan oshsa hisoblamaymiz (saqlash baribir bloklanadi). 1 m² dan kichik — bir chiziqdagi uchlar
const crosses = computed(() => !tooMany.value && points.value.length >= 3
  && (selfIntersects(points.value) || area.value < 1e-4))
const boundaryOk = computed(() => points.value.length >= 3 && !crosses.value && !tooMany.value)
const missing = computed(() => {
  if (points.value.length < 3) return 'Xaritada kamida 3 ta nuqta belgilang'
  if (crosses.value) return 'Chegara qirralari bir-birini kesmasin'
  if (tooMany.value) return `Nuqtalar ${MAX_VERTICES} tadan oshmasin`
  if (!form.value.name.trim()) return 'Dala nomini yozing'
  return ''
})

// Undo — har o'zgarishdan oldingi holat (nuqta qo'shish, sudrash, o'chirish, import, tozalash)
const history: LonLat[][] = []
const canUndo = ref(false)
let restoring = false
watch(points, (_, prev) => {
  if (restoring) return void (restoring = false)
  history.push(prev)
  if (history.length > 100) history.shift()
  canUndo.value = true
})

function undo() {
  const prev = history.pop()
  if (!prev) return
  restoring = true
  points.value = prev
  canUndo.value = history.length > 0
}

function clearAll() {
  points.value = []
  toast('Chegara tozalandi', { action: { label: 'Qaytarish', onClick: undo } })
}

// ── Qidiruv: koordinata yoki joy nomi (OSM Nominatim — faqat Enter'da, avtomatik to'ldirish qoidaga zid) ──
interface Place { display_name: string; boundingbox: [string, string, string, string] }
const query = ref('')
const results = ref<Place[]>([])
const searching = ref(false)
const cache = new Map<string, Place[]>()
let lastAt = 0                                              // Nominatim qoidasi: ≤ 1 so'rov/s

async function search() {
  const q = query.value.trim()
  if (!q) return
  const ll = parseLatLon(q)
  if (ll) {
    results.value = []
    return map.value?.goTo(ll[0], ll[1])
  }
  if (cache.has(q)) return void (results.value = cache.get(q)!)
  if (searching.value) return
  const wait = lastAt + 1000 - Date.now()
  searching.value = true
  try {
    if (wait > 0) await new Promise((r) => setTimeout(r, wait))
    lastAt = Date.now()
    const url = new URL('https://nominatim.openstreetmap.org/search')
    url.search = new URLSearchParams({
      q, format: 'jsonv2', limit: '6', 'accept-language': 'uz,ru,en', viewbox: '55,46,76,36',   // O'rta Osiyo ustun
    }).toString()
    const res = await fetch(url)
    if (!res.ok) throw new Error()
    results.value = await res.json()
    cache.set(q, results.value)
    if (!results.value.length) toast.info('Hech narsa topilmadi')
  } catch {
    toast.error('Qidiruv ishlamadi — koordinata kiriting: 41.31, 69.24')
  } finally {
    searching.value = false
  }
}

function pick(p: Place) {
  const [s, n, w, e] = p.boundingbox.map(Number) as [number, number, number, number]
  if ([s, n, w, e].every(Number.isFinite)) map.value?.fitBounds(s, w, n, e)
  results.value = []
}

function clearSearch() {
  query.value = ''
  results.value = []
}

function locateMe() {
  if (!navigator.geolocation) return toast.error('Brauzer joylashuvni bermaydi')
  navigator.geolocation.getCurrentPosition(
    (p) => map.value?.goTo(p.coords.latitude, p.coords.longitude),
    () => toast.error('Joylashuvga ruxsat berilmadi'),
    { enableHighAccuracy: true, timeout: 10_000 },
  )
}

// ── GeoJSON: import (matn yoki fayl) va nusxalash ──
const importOpen = ref(false)
const geojson = ref('')

async function importGeojson() {
  try {
    points.value = parseRing(geojson.value, MAX_VERTICES)
    importOpen.value = false
    geojson.value = ''
    await nextTick()
    map.value?.fit()
    // [lat, lon] tartibida yozilgan fayl: O'rta Osiyo dalasi Arktikaga tushadi — almashtirilsa O'zbekiston kengligiga to'g'ri keladi
    const lat = points.value.reduce((a, p) => a + p[1], 0) / points.value.length
    const lon = points.value.reduce((a, p) => a + p[0], 0) / points.value.length
    if (lat > 50 && lon >= 35 && lon <= 47) toast.warning('Kenglik/uzunlik almashib qolmaganmi? GeoJSON tartibi — [lon, lat]')
  } catch (e) {
    if (e instanceof TooManyVertices) return toast.error(`${e.message} ta nuqta — koʻpi bilan ${MAX_VERTICES}. Chegarani soddalashtiring`)
    toast.error('GeoJSON Polygon kutilgan: {"type":"Polygon","coordinates":[[[lon,lat],...]]}')
  }
}

async function readFile(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (f) geojson.value = await f.text()
}

async function copyGeojson() {
  try {
    await navigator.clipboard.writeText(toGeoJson(points.value))
    toast.success('GeoJSON nusxalandi')
  } catch {
    toast.error('Nusxalab boʻlmadi')
  }
}

// ── Qoralama: sahifadan chiqib ketilsa chizilgan chegara yo'qolmasin (faqat shu brauzerda) ──
onMounted(async () => {
  try {
    const d = JSON.parse(localStorage.getItem(DRAFT_KEY) ?? 'null')
    if (!Array.isArray(d?.points) || !d.points.length) return
    const restored = parseRing(JSON.stringify(d.points), MAX_VERTICES)   // buzilgan qoralama xaritani yiqitmasin
    const f = d.form ?? {}
    form.value = {
      name: typeof f.name === 'string' ? f.name.slice(0, 80) : '',
      crop: f.crop in CROP ? f.crop : 'WHEAT',
      sownOn: typeof f.sownOn === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(f.sownOn) ? f.sownOn : '',
      irrigation: f.irrigation in IRRIGATION ? f.irrigation : NONE,
    }
    restoring = true                                        // tiklash undo tarixiga tushmasin
    points.value = restored
    await nextTick()
    map.value?.fit()
    toast.info('Oldingi qoralama tiklandi', { action: { label: 'Tozalash', onClick: reset } })
  } catch {
    try { localStorage.removeItem(DRAFT_KEY) } catch { /* storage yopiq */ }
  }
})
watch([points, form], () => {
  try {
    if (points.value.length) localStorage.setItem(DRAFT_KEY, JSON.stringify({ points: points.value, form: form.value }))
    else localStorage.removeItem(DRAFT_KEY)
  } catch { /* storage yopiq */ }
}, { deep: true })

function reset() {
  points.value = []
  form.value = { name: '', crop: 'WHEAT', sownOn: '', irrigation: NONE }
}

// Ctrl/Cmd+Z — oxirgi amalni bekor qilish (matn maydonida va dialog ochiqligida emas). code — kirill layout'da ham
useEventListener('keydown', (e: KeyboardEvent) => {
  const t = e.target as HTMLElement
  if ((e.metaKey || e.ctrlKey) && !e.shiftKey && e.code === 'KeyZ' && canUndo.value && !importOpen.value
    && !t.closest('input, textarea, [contenteditable]')) {
    e.preventDefault()
    undo()
  }
})

async function save() {
  if (missing.value) return toast.error(missing.value)
  saving.value = true
  try {
    const f = await fieldsApi.createLab({
      name: form.value.name.trim(), crop: form.value.crop, coordinates: points.value,
      sownOn: form.value.sownOn || undefined,
      irrigationMethod: form.value.irrigation === NONE ? undefined : form.value.irrigation,
    })
    try { localStorage.removeItem(DRAFT_KEY) } catch { /* storage yopiq */ }
    toast.success('Dala qoʻshildi, birinchi tahlil navbatda')
    router.push(`/fields/${f.id}`)
  } catch (e) {
    toast.error(errorMessage(e))
  } finally {
    saving.value = false
  }
}

const chip = (on: boolean) => [
  'h-8 rounded-md border px-3 text-xs font-medium transition-colors',
  on ? 'border-primary bg-primary text-primary-foreground' : 'hover:bg-accent text-muted-foreground hover:text-foreground',
]
</script>

<template>
  <!-- Ilova sarlavhasi 3.5rem + main padding 3rem — katta ekranda sahifa aynan bitta ekran -->
  <div class="flex flex-col gap-4 lg:h-[calc(100svh-6.5rem)] lg:flex-row">
    <!-- ── Xarita ── -->
    <section class="relative h-[62svh] min-h-[420px] overflow-hidden rounded-xl border lg:h-auto lg:flex-1">
      <FieldDrawMap ref="map" v-model="points" :invalid="crosses" :labels="labels" />

      <!-- Qidiruv -->
      <div class="absolute top-3 left-3 z-10 w-[min(380px,calc(100%-4.5rem))]">
        <form class="bg-background/95 flex h-10 items-center gap-2 rounded-lg border px-3 shadow-md backdrop-blur"
              @submit.prevent="search">
          <Loader2 v-if="searching" class="text-muted-foreground size-4 shrink-0 animate-spin" />
          <Search v-else class="text-muted-foreground size-4 shrink-0" />
          <input v-model="query" class="placeholder:text-muted-foreground h-full min-w-0 flex-1 bg-transparent text-sm outline-none"
                 placeholder="Joy nomi yoki 41.31, 69.24" aria-label="Joy qidirish" @keydown.esc="clearSearch" />
          <button v-if="query" type="button" class="text-muted-foreground hover:text-foreground" aria-label="Tozalash"
                  @click="clearSearch"><X class="size-4" /></button>
        </form>
        <ul v-if="results.length" class="bg-background/95 mt-1.5 overflow-hidden rounded-lg border shadow-md backdrop-blur">
          <li v-for="(r, i) in results" :key="i">
            <button type="button" class="hover:bg-accent flex w-full items-start gap-2 px-3 py-2 text-left text-sm"
                    @click="pick(r)">
              <MapPin class="text-muted-foreground mt-0.5 size-3.5 shrink-0" />
              <span class="line-clamp-2">{{ r.display_name }}</span>
            </button>
          </li>
        </ul>
      </div>

      <!-- Xarita boshqaruvi -->
      <div class="bg-background/95 absolute top-3 right-3 z-10 flex flex-col rounded-lg border p-0.5 shadow-md backdrop-blur">
        <IconAction :icon="LocateFixed" label="Mening joylashuvim" @click="locateMe" />
        <Tooltip>
          <TooltipTrigger as-child>
            <Button variant="ghost" size="icon-sm" :aria-pressed="labels" aria-label="Joy nomlari"
                    :class="labels ? 'bg-accent text-foreground' : 'text-muted-foreground'" @click="labels = !labels"><Tags /></Button>
          </TooltipTrigger>
          <TooltipContent side="left">Joy nomlari: {{ labels ? 'yoqiq' : 'oʻchiq' }}</TooltipContent>
        </Tooltip>
        <IconAction :icon="Scan" label="Dalaga moslash" :disabled="points.length < 2" @click="map?.fit()" />
      </div>

      <!-- Chizish paneli -->
      <div class="bg-background/95 absolute bottom-4 left-1/2 z-10 flex max-w-[calc(100%-6rem)] -translate-x-1/2 items-center gap-1 rounded-full border py-1 pr-1 pl-4 shadow-md backdrop-blur">
        <span v-if="!points.length" class="text-muted-foreground truncate pr-3 text-sm">Xaritani bosib dala chegarasini chizing</span>
        <template v-else>
          <span class="truncate text-sm tabular-nums" :class="crosses ? 'text-destructive' : ''">
            <b class="font-semibold">{{ fmtNum(area, 2) }} ga</b>
            <span class="text-muted-foreground"> · {{ points.length }} nuqta · {{ fmtNum(perimeter / 1000, 2) }} km</span>
          </span>
          <Separator orientation="vertical" class="mx-1 h-5" />
          <IconAction :icon="Undo2" label="Bekor qilish (⌘Z)" :disabled="!canUndo" @click="undo" />
          <IconAction :icon="Eraser" label="Hammasini tozalash" @click="clearAll" />
          <IconAction :icon="Copy" label="GeoJSON nusxalash" :disabled="points.length < 3" @click="copyGeojson" />
        </template>
        <IconAction :icon="FileUp" label="GeoJSON import" @click="importOpen = true" />
      </div>
    </section>

    <!-- ── Ma'lumotlar ── -->
    <aside class="bg-card flex flex-col rounded-xl border lg:w-[360px] lg:shrink-0">
      <div class="flex items-start gap-2 border-b p-4">
        <Button variant="ghost" size="icon-sm" class="-ml-1" aria-label="Orqaga" @click="router.back()"><ArrowLeft /></Button>
        <div>
          <h1 class="text-lg leading-7 font-semibold tracking-tight">Lab dala qoʻshish</h1>
          <p class="text-muted-foreground text-xs">Fermer dalasi kabi kuzatiladi — tahlil, monitoring, ob-havo</p>
        </div>
      </div>

      <div class="flex-1 space-y-5 overflow-y-auto p-4">
        <!-- Chegara holati -->
        <div class="flex items-center gap-3 rounded-lg border p-3"
             :class="crosses || tooMany ? 'border-destructive/40 bg-destructive/5' : ''">
          <CircleAlert v-if="crosses || tooMany" class="text-destructive size-5 shrink-0" />
          <CircleCheck v-else-if="boundaryOk" class="size-5 shrink-0 text-emerald-600" />
          <CircleDashed v-else class="text-muted-foreground size-5 shrink-0" />
          <div class="min-w-0 text-sm">
            <div class="font-medium">Chegara</div>
            <div class="text-muted-foreground truncate text-xs tabular-nums">
              <template v-if="crosses">Qirralar kesishmoqda — nuqtani suring</template>
              <template v-else-if="tooMany">{{ points.length }} nuqta — ko‘pi bilan {{ MAX_VERTICES }}</template>
              <template v-else-if="boundaryOk">{{ fmtNum(area, 2) }} ga · {{ points.length }} nuqta</template>
              <template v-else>Xaritada kamida 3 nuqta belgilang</template>
            </div>
          </div>
        </div>

        <div class="space-y-1.5">
          <Label for="name">Nomi</Label>
          <Input id="name" v-model="form.name" maxlength="80" placeholder="Jizzax, bugʻdoy 1" />
        </div>

        <div class="space-y-1.5">
          <Label>Ekin</Label>
          <div class="flex flex-wrap gap-1.5" role="group" aria-label="Ekin">
            <button v-for="(l, k) in CROP" :key="k" type="button" :aria-pressed="form.crop === k"
                    :class="chip(form.crop === k)" @click="form.crop = k">{{ l }}</button>
          </div>
        </div>

        <div class="space-y-1.5">
          <Label for="sown">Ekish sanasi <span class="text-muted-foreground font-normal">· ixtiyoriy</span></Label>
          <Input id="sown" v-model="form.sownOn" type="date" :max="today" />
        </div>

        <div class="space-y-1.5">
          <Label>Sugʻorish usuli</Label>
          <div class="flex flex-wrap gap-1.5" role="group" aria-label="Sugʻorish usuli">
            <button type="button" :aria-pressed="form.irrigation === NONE" :class="chip(form.irrigation === NONE)"
                    @click="form.irrigation = NONE">Nomaʼlum</button>
            <button v-for="(l, k) in IRRIGATION" :key="k" type="button" :aria-pressed="form.irrigation === k"
                    :class="chip(form.irrigation === k)" @click="form.irrigation = k">{{ l }}</button>
          </div>
        </div>

        <p class="text-muted-foreground text-xs leading-relaxed">
          Saqlangach birinchi tahlil navbatga qoʻyiladi. Xabarlar Telegram’ga yuborilmaydi.
        </p>
      </div>

      <div class="space-y-2 border-t p-4">
        <Button class="w-full" size="lg" :disabled="saving || !!missing" @click="save">
          <Loader2 v-if="saving" class="animate-spin" /> Saqlash va tahlil qilish
        </Button>
        <p v-if="missing" class="text-muted-foreground text-center text-xs">{{ missing }}</p>
      </div>
    </aside>
  </div>

  <Dialog v-model:open="importOpen">
    <DialogContent class="sm:max-w-lg">
      <DialogHeader>
        <DialogTitle>GeoJSON import</DialogTitle>
        <DialogDescription>Boshqa joyda chizilgan chegara: Polygon, Feature yoki [[lon, lat], …]. Joriy chizma almashtiriladi.</DialogDescription>
      </DialogHeader>
      <Textarea v-model="geojson" rows="7" class="font-mono text-xs" placeholder='{"type":"Polygon","coordinates":[[[69.2,41.3],...]]}' />
      <DialogFooter class="sm:justify-between">
        <Button variant="outline" as-child>
          <label class="cursor-pointer"><FileUp /> Fayldan<input type="file" accept=".geojson,.json,application/geo+json,application/json" class="sr-only" @change="readFile" /></label>
        </Button>
        <Button :disabled="!geojson.trim()" @click="importGeojson">Xaritaga qoʻyish</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
