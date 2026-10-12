<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { toast } from 'vue-sonner'
import {
  Bot, Copy, Download, ExternalLink, FileText, FolderOpen, Image as ImageIcon, LayoutGrid, List, Search, Server, UserRound, ZoomIn,
} from '@lucide/vue'
import ImageLightbox from '@/components/app/ImageLightbox.vue'
import PageHeader from '@/components/app/PageHeader.vue'
import Paginator from '@/components/app/Paginator.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { filesApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { FileAdmin, FileSource, FileStats, FileUsage, Page } from '@/api/types'
import { fmtBytes, fmtDateTime, fmtNum } from '@/lib/format'
import { P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

/** Barcha fayllar (file:manage): chat rasmlari, fermer yuborganlari, tahlil xaritalari — qayerda ishlatilgani bilan */
const auth = useAuth()
const ALL = 'ALL'
const SOURCE: Record<FileSource, { label: string; tone: 'blue' | 'violet' | 'gray' }> = {
  TELEGRAM: { label: 'Fermer (Telegram)', tone: 'blue' },
  STAFF: { label: 'Xodim', tone: 'violet' },
  SYSTEM: { label: 'Tizim', tone: 'gray' },
}

const f = reactive({ q: '', type: ALL, source: ALL, from: '', to: '' })
const page = ref(0)
const data = ref<Page<FileAdmin> | null>(null)
const stats = ref<FileStats | null>(null)

// Ko'rinish tanlovi — shu brauzerda eslab qolinadi (localStorage bo'lmasa ham ishlaydi)
const VIEW_KEY = 'agro.files.view'
function readView(): 'grid' | 'table' {
  try { return localStorage.getItem(VIEW_KEY) === 'table' ? 'table' : 'grid' } catch { return 'grid' }
}
const view = ref<'grid' | 'table'>(readView())
watch(view, (v) => { try { localStorage.setItem(VIEW_KEY, v) } catch { /* ixtiyoriy */ } })

async function load() {
  try {
    data.value = await filesApi.search({
      q: f.q.trim() || undefined, type: f.type === ALL ? undefined : f.type, source: f.source === ALL ? undefined : f.source,
      from: f.from || undefined, to: f.to || undefined, page: page.value, size: 24, sort: 'createdDate,desc',
    })
  } catch (e) {
    toast.error(errorMessage(e))
  }
}
async function loadStats() {
  try { stats.value = await filesApi.stats() } catch { /* statistika ixtiyoriy */ }
}

let timer: ReturnType<typeof setTimeout> | undefined
watch(() => [f.type, f.source, f.from, f.to], () => { page.value = 0; load() })
watch(() => f.q, () => { clearTimeout(timer); timer = setTimeout(() => { page.value = 0; load() }, 350) })
watch(page, load)
onMounted(() => { load(); loadStats() })

const filtered = computed(() => !!(f.q || f.type !== ALL || f.source !== ALL || f.from || f.to))
function reset() { Object.assign(f, { q: '', type: ALL, source: ALL, from: '', to: '' }) }

// ── Tafsilot va katta ko'rish ──
const selected = ref<FileAdmin | null>(null)
const images = computed(() => (data.value?.content ?? []).filter((x) => x.image)
  .map((x) => ({ url: x.url, name: x.name, caption: `${fmtBytes(x.size)} · ${fmtDateTime(x.createdDate)}`, hashId: x.hashId })))
const viewing = ref<number | null>(null)
function zoom(x: FileAdmin) {
  const i = images.value.findIndex((g) => g.hashId === x.hashId)
  if (i >= 0) viewing.value = i
}

function usageLink(u: FileUsage): string | null {
  if (u.kind === 'ANALYSIS') return auth.can(P.ANALYSIS_READ) ? `/analyses/${u.refId}` : null
  if (u.kind === 'FEEDBACK') return auth.can(P.FEEDBACK_MANAGE) ? `/feedback?tab=all&chat=${u.refId}` : null
  return null
}

async function copy(text: string, what: string) {
  try {
    await navigator.clipboard.writeText(text)
    toast.success(`${what} nusxalandi`)
  } catch {
    toast.error('Nusxalab boʻlmadi')
  }
}
</script>

<template>
  <PageHeader title="Fayllar" description="Serverda saqlanayotgan barcha fayllar: chat rasmlari, fermer yuborganlari, tahlil xaritalari">
    <div class="bg-muted inline-flex rounded-lg p-0.5">
      <button type="button" :class="['flex items-center gap-1.5 rounded-md px-2.5 py-1 text-sm', view === 'grid' ? 'bg-background shadow-xs' : 'text-muted-foreground']" @click="view = 'grid'">
        <LayoutGrid class="size-4" /> Panjara
      </button>
      <button type="button" :class="['flex items-center gap-1.5 rounded-md px-2.5 py-1 text-sm', view === 'table' ? 'bg-background shadow-xs' : 'text-muted-foreground']" @click="view = 'table'">
        <List class="size-4" /> Jadval
      </button>
    </div>
  </PageHeader>

  <!-- Statistika -->
  <div class="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
    <div v-for="c in [
      { icon: FolderOpen, label: 'Jami fayllar', value: stats ? fmtNum(stats.total) : null, sub: stats ? fmtBytes(stats.totalBytes) : '' },
      { icon: ImageIcon, label: 'Rasmlar', value: stats ? fmtNum(stats.images) : null, sub: 'JPG, PNG' },
      { icon: FileText, label: 'PDF hujjatlar', value: stats ? fmtNum(stats.pdfs) : null, sub: '' },
      { icon: Bot, label: 'Fermerlardan', value: stats ? fmtNum(stats.fromTelegram) : null, sub: stats ? `xodimlardan ${fmtNum(stats.fromStaff)}` : '' },
    ]" :key="c.label" class="bg-background rounded-xl border p-4 shadow-xs">
      <div class="text-muted-foreground flex items-center gap-2 text-xs"><component :is="c.icon" class="size-4" />{{ c.label }}</div>
      <p v-if="c.value !== null" class="mt-1.5 text-2xl font-semibold tabular-nums">{{ c.value }}</p>
      <Skeleton v-else class="mt-2 h-7 w-16" />
      <p class="text-muted-foreground mt-0.5 text-xs">{{ c.sub }}&nbsp;</p>
    </div>
  </div>

  <!-- Filtrlar -->
  <div class="mb-4 flex flex-wrap items-center gap-2">
    <div class="relative w-full sm:w-64">
      <Search class="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
      <Input v-model="f.q" placeholder="Fayl nomi yoki hashId…" class="pl-8" />
    </div>
    <Select v-model="f.type">
      <SelectTrigger class="w-36"><SelectValue /></SelectTrigger>
      <SelectContent>
        <SelectItem :value="ALL">Barcha tur</SelectItem>
        <SelectItem value="IMAGE">Rasmlar</SelectItem>
        <SelectItem value="PDF">PDF</SelectItem>
      </SelectContent>
    </Select>
    <Select v-model="f.source">
      <SelectTrigger class="w-48"><SelectValue /></SelectTrigger>
      <SelectContent>
        <SelectItem :value="ALL">Barcha manba</SelectItem>
        <SelectItem v-for="(s, k) in SOURCE" :key="k" :value="k">{{ s.label }}</SelectItem>
      </SelectContent>
    </Select>
    <div class="flex items-center gap-1.5">
      <Input v-model="f.from" type="date" class="w-40" aria-label="Sanadan" />
      <span class="text-muted-foreground">—</span>
      <Input v-model="f.to" type="date" class="w-40" aria-label="Sanagacha" />
    </div>
    <Button v-if="filtered" variant="ghost" size="sm" @click="reset">Tozalash</Button>
  </div>

  <!-- Panjara -->
  <template v-if="view === 'grid'">
    <div v-if="!data" class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
      <Skeleton v-for="i in 12" :key="i" class="aspect-square rounded-xl" />
    </div>
    <div v-else-if="!data.content.length" class="text-muted-foreground flex flex-col items-center gap-2 rounded-xl border border-dashed py-16 text-center">
      <FolderOpen class="size-10 opacity-40" />
      <p>{{ filtered ? 'Filtrga mos fayl yoʻq' : 'Hali fayl yoʻq' }}</p>
    </div>
    <div v-else class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
      <button
        v-for="x in data.content" :key="x.hashId" type="button"
        class="bg-background group overflow-hidden rounded-xl border text-left shadow-xs transition hover:shadow-md focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
        @click="selected = x"
      >
        <div class="bg-muted relative aspect-square overflow-hidden">
          <!-- Tizim rasmlari (xarita, grafik) — to'liq ko'rinsin; foto — kvadratni to'ldirsin -->
          <img v-if="x.thumbUrl" :src="x.thumbUrl" :alt="x.name" loading="lazy"
               :class="['size-full transition group-hover:scale-[1.03]', x.source === 'SYSTEM' ? 'bg-white object-contain p-1' : 'object-cover']" />
          <div v-else class="flex size-full flex-col items-center justify-center gap-1 text-red-600">
            <FileText class="size-12" /><span class="text-xs font-semibold">PDF</span>
          </div>
          <span class="absolute top-2 left-2"><StatusBadge :tone="SOURCE[x.source].tone" :label="SOURCE[x.source].label" /></span>
        </div>
        <div class="p-2.5">
          <p class="truncate text-sm font-medium" :title="x.name">{{ x.name }}</p>
          <p class="text-muted-foreground truncate text-xs">{{ fmtBytes(x.size) }} · {{ fmtDateTime(x.createdDate) }}</p>
          <p class="mt-1 truncate text-xs" :class="x.usages.length ? 'text-foreground/70' : 'text-amber-600'">
            {{ x.usages.length ? x.usages[0]!.label : 'Hech qayerda ishlatilmagan' }}
          </p>
        </div>
      </button>
    </div>
  </template>

  <!-- Jadval -->
  <div v-else class="bg-background rounded-xl border">
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Fayl</TableHead><TableHead>Hajm</TableHead><TableHead>Oʻlcham</TableHead><TableHead>Manba</TableHead>
          <TableHead>Kim yukladi</TableHead><TableHead>Qayerda</TableHead><TableHead>Sana</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        <template v-if="!data">
          <TableRow v-for="i in 6" :key="i"><TableCell :colspan="7"><Skeleton class="h-8 w-full" /></TableCell></TableRow>
        </template>
        <TableRow v-for="x in data?.content ?? []" :key="x.hashId" class="cursor-pointer" @click="selected = x">
          <TableCell>
            <div class="flex items-center gap-3">
              <img v-if="x.thumbUrl" :src="x.thumbUrl" alt="" loading="lazy" class="size-10 shrink-0 rounded-md object-cover" />
              <div v-else class="flex size-10 shrink-0 items-center justify-center rounded-md bg-red-50 text-red-600 dark:bg-red-500/10"><FileText class="size-5" /></div>
              <div class="min-w-0">
                <p class="max-w-64 truncate font-medium">{{ x.name }}</p>
                <p class="text-muted-foreground font-mono text-xs">{{ x.hashId }}</p>
              </div>
            </div>
          </TableCell>
          <TableCell class="whitespace-nowrap tabular-nums">{{ fmtBytes(x.size) }}</TableCell>
          <TableCell class="text-muted-foreground whitespace-nowrap tabular-nums">{{ x.width ? `${x.width}×${x.height}` : '—' }}</TableCell>
          <TableCell><StatusBadge :tone="SOURCE[x.source].tone" :label="SOURCE[x.source].label" /></TableCell>
          <TableCell class="text-muted-foreground whitespace-nowrap">{{ x.uploadedByEmail ?? '—' }}</TableCell>
          <TableCell class="max-w-56">
            <span v-if="x.usages.length" class="block truncate">{{ x.usages.map((u) => u.label).join(', ') }}</span>
            <span v-else class="text-amber-600">ishlatilmagan</span>
          </TableCell>
          <TableCell class="text-muted-foreground whitespace-nowrap">{{ fmtDateTime(x.createdDate) }}</TableCell>
        </TableRow>
        <TableEmpty v-if="data && !data.content.length" :colspan="7">{{ filtered ? 'Filtrga mos fayl yoʻq' : 'Hali fayl yoʻq' }}</TableEmpty>
      </TableBody>
    </Table>
  </div>

  <Paginator v-if="data && data.totalElements" class="mt-4" :page="data.page" :total-pages="data.totalPages" :total-elements="data.totalElements" @change="page = $event" />

  <!-- Tafsilot -->
  <Sheet :open="selected !== null" @update:open="(v: boolean) => { if (!v) selected = null }">
    <SheetContent class="flex w-full flex-col gap-0 overflow-y-auto p-0 sm:max-w-md">
      <template v-if="selected">
        <SheetHeader class="border-b p-4 pr-12">
          <SheetTitle class="break-all">{{ selected.name }}</SheetTitle>
          <SheetDescription>{{ selected.contentType }} · {{ fmtBytes(selected.size) }}</SheetDescription>
        </SheetHeader>

        <div class="bg-muted/40 border-b p-4">
          <button v-if="selected.image" type="button" class="group relative block w-full cursor-zoom-in" title="Kattalashtirish" @click="zoom(selected)">
            <img :src="selected.url" :alt="selected.name" class="mx-auto max-h-80 rounded-lg object-contain" />
            <span class="absolute right-2 bottom-2 flex items-center gap-1 rounded-full bg-black/60 px-2 py-1 text-xs text-white opacity-0 transition group-hover:opacity-100">
              <ZoomIn class="size-3.5" /> Kattalashtirish
            </span>
          </button>
          <div v-else class="flex flex-col items-center gap-2 py-8 text-red-600"><FileText class="size-16" /><span class="text-sm font-semibold">PDF hujjat</span></div>
          <div class="mt-3 flex gap-2">
            <Button as-child variant="outline" size="sm" class="flex-1"><a :href="selected.url" :download="selected.name"><Download /> Yuklab olish</a></Button>
            <Button as-child variant="outline" size="sm" class="flex-1"><a :href="selected.url" target="_blank" rel="noopener"><ExternalLink /> Yangi tabda</a></Button>
          </div>
        </div>

        <dl class="divide-y text-sm">
          <div class="flex items-center justify-between gap-3 px-4 py-2.5">
            <dt class="text-muted-foreground">hashId</dt>
            <dd class="flex min-w-0 items-center gap-1 font-mono text-xs">
              <span class="truncate">{{ selected.hashId }}</span>
              <button type="button" class="text-muted-foreground hover:text-foreground p-1" title="Nusxalash" @click="copy(selected.hashId, 'hashId')"><Copy class="size-3.5" /></button>
            </dd>
          </div>
          <div v-if="selected.width" class="flex justify-between gap-3 px-4 py-2.5"><dt class="text-muted-foreground">Oʻlcham</dt><dd class="tabular-nums">{{ selected.width }} × {{ selected.height }} px</dd></div>
          <div class="flex justify-between gap-3 px-4 py-2.5"><dt class="text-muted-foreground">Manba</dt><dd><StatusBadge :tone="SOURCE[selected.source].tone" :label="SOURCE[selected.source].label" /></dd></div>
          <div class="flex justify-between gap-3 px-4 py-2.5">
            <dt class="text-muted-foreground">Kim yukladi</dt>
            <dd class="flex items-center gap-1.5">
              <component :is="selected.source === 'STAFF' ? UserRound : selected.source === 'TELEGRAM' ? Bot : Server" class="text-muted-foreground size-3.5" />
              {{ selected.uploadedByEmail ?? (selected.source === 'TELEGRAM' ? 'Fermer' : 'Tizim') }}
            </dd>
          </div>
          <div class="flex justify-between gap-3 px-4 py-2.5"><dt class="text-muted-foreground">Sana</dt><dd>{{ fmtDateTime(selected.createdDate) }}</dd></div>
          <div class="flex items-center justify-between gap-3 px-4 py-2.5">
            <dt class="text-muted-foreground">SHA-256</dt>
            <dd class="flex min-w-0 items-center gap-1 font-mono text-xs">
              <span class="truncate" :title="selected.sha256">{{ selected.sha256.slice(0, 16) }}…</span>
              <button type="button" class="text-muted-foreground hover:text-foreground p-1" title="Nusxalash" @click="copy(selected.sha256, 'SHA-256')"><Copy class="size-3.5" /></button>
            </dd>
          </div>
        </dl>

        <div class="border-t p-4">
          <p class="mb-2 text-sm font-medium">Qayerda ishlatilgan</p>
          <ul v-if="selected.usages.length" class="space-y-1.5">
            <li v-for="u in selected.usages" :key="u.kind + u.refId" class="text-sm">
              <RouterLink v-if="usageLink(u)" :to="usageLink(u)!" class="text-primary hover:underline">{{ u.label }}</RouterLink>
              <span v-else>{{ u.label }}</span>
            </li>
          </ul>
          <p v-else class="text-sm text-amber-600">Hech qayerda ishlatilmagan — yuklangan, lekin yuborilmagan</p>
        </div>
      </template>
    </SheetContent>
  </Sheet>

  <ImageLightbox v-model:index="viewing" :images="images" />
</template>
