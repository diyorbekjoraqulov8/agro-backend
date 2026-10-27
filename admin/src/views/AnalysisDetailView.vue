<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, RotateCcw } from '@lucide/vue'
import { toast } from 'vue-sonner'
import ImageLightbox from '@/components/app/ImageLightbox.vue'
import PageHeader from '@/components/app/PageHeader.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { jobTone } from '@/components/app/badges'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { analysesApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { AnalysisResponse } from '@/api/types'
import { duration, fmtDate, fmtDateTime, fmtNum } from '@/lib/format'
import { JOB_KIND, JOB_STATUS, P, SIDE, recommendation } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const id = Number(route.params.id)
const job = ref<AnalysisResponse | null>(null)
let timer: ReturnType<typeof setInterval> | undefined

const r = computed(() => job.value?.result ?? null)
const prevDiff = computed(() => r.value?.prev_year_peak_ndvi ? Math.round((r.value.peak_ndvi / r.value.prev_year_peak_ndvi - 1) * 100) : null)

async function load() {
  try {
    job.value = await analysesApi.get(id)
    // Navbatda/bajarilmoqda — 5 s da yangilab turamiz, tugagach to'xtaymiz
    const busy = job.value.status === 'PENDING' || job.value.status === 'RUNNING'
    if (busy && !timer) timer = setInterval(load, 5000)
    if (!busy && timer) { clearInterval(timer); timer = undefined }
  } catch (e) {
    toast.error(errorMessage(e))
  }
}

async function retry() {
  try {
    job.value = await analysesApi.retry(id)
    toast.success('Qayta navbatga qoʻyildi')
    load()
  } catch (e) {
    toast.error(errorMessage(e))
  }
}

onMounted(load)
onBeforeUnmount(() => clearInterval(timer))

// Xarita va grafik — sahifaning o'zida katta ko'rish
const viewing = ref<number | null>(null)
const gallery = computed(() => job.value ? [
  { url: analysesApi.mapUrl(job.value.id), name: `tahlil-${job.value.id}-xarita.png`, caption: 'Zonalar xaritasi' },
  { url: analysesApi.chartUrl(job.value.id), name: `tahlil-${job.value.id}-grafik.png`, caption: 'NDVI grafigi' },
  ...(r.value?.debug && !ndviMissing.value ? [{ url: analysesApi.ndviUrl(job.value.id), name: `tahlil-${job.value.id}-ndvi.png`, caption: 'NDVI xaritasi (mavsum choʻqqisi)' }] : []),
] : [])

// NDVI xaritasi ixtiyoriy (fayl saqlanmagan bo'lishi mumkin) — 404 bo'lsa siniq rasm o'rniga matn
const ndviMissing = ref(false)

// L1b debug: qaysi suratlar ishlatildi — ro'yxat uzun bo'lsa avval faqat rad etilganlar
const dbg = computed(() => r.value?.debug ?? null)
const showAllScenes = ref(false)
const scenes = computed(() => (dbg.value?.scenes ?? []).filter((s) => showAllScenes.value || !s.used))
</script>

<template>
  <Button variant="ghost" size="sm" class="mb-2 -ml-2" @click="router.back()"><ArrowLeft /> Orqaga</Button>
  <div v-if="!job" class="space-y-4"><Skeleton class="h-10 w-72" /><Skeleton class="h-96 w-full" /></div>
  <template v-else>
    <PageHeader :title="`Tahlil #${job.id}`" :description="`${JOB_KIND[job.kind]} · ${fmtDate(job.seasonStart)} – ${fmtDate(job.seasonEnd)}`">
      <StatusBadge :tone="jobTone[job.status]" :label="JOB_STATUS[job.status]" />
      <Button variant="outline" size="sm" as-child><RouterLink :to="`/fields/${job.fieldId}`">Dala #{{ job.fieldId }}</RouterLink></Button>
      <Button v-if="job.status === 'FAILED' && auth.can(P.ANALYSIS_RUN)" size="sm" @click="retry"><RotateCcw /> Qayta urinish</Button>
    </PageHeader>

    <Alert v-if="job.status === 'FAILED'" variant="destructive" class="mb-4">
      <AlertTitle>Tahlil bajarilmadi ({{ job.attempts }} urinish)</AlertTitle>
      <AlertDescription class="break-all">{{ job.error }}</AlertDescription>
    </Alert>
    <Alert v-else-if="job.status !== 'DONE'" class="mb-4">
      <AlertTitle>{{ job.status === 'RUNNING' ? 'Bajarilmoqda…' : 'Navbatda' }}</AlertTitle>
      <AlertDescription>Sahifa avtomatik yangilanadi. Urinishlar: {{ job.attempts }}{{ job.error ? ` · oxirgi xato: ${job.error}` : '' }}</AlertDescription>
    </Alert>

    <div v-if="r" class="space-y-4">
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card><CardHeader><CardDescription>Maydon</CardDescription><CardTitle class="text-2xl tabular-nums">{{ fmtNum(r.area_ha, 2) }} ga</CardTitle></CardHeader></Card>
        <Card><CardHeader><CardDescription>NDVI choʻqqisi</CardDescription><CardTitle class="text-2xl tabular-nums">{{ fmtNum(r.peak_ndvi, 2) }}
          <span class="text-muted-foreground text-sm font-normal">{{ fmtDate(r.peak_date) }}</span></CardTitle></CardHeader></Card>
        <Card><CardHeader><CardDescription>Oʻtgan yilga nisbatan</CardDescription><CardTitle class="text-2xl tabular-nums"
          :class="prevDiff != null && prevDiff <= -5 ? 'text-destructive' : ''">{{ prevDiff == null ? '—' : `${prevDiff > 0 ? '+' : ''}${prevDiff}%` }}</CardTitle></CardHeader></Card>
        <Card><CardHeader><CardDescription>Sugʻorish ehtiyoji</CardDescription><CardTitle class="text-2xl tabular-nums">
          {{ r.water ? `${fmtNum(r.water.irrigation_need_low_m3_ha)}–${fmtNum(r.water.irrigation_need_high_m3_ha)}` : '—' }}
          <span class="text-muted-foreground text-sm font-normal">m³/ga</span></CardTitle></CardHeader></Card>
      </div>

      <div class="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader><CardTitle class="text-base">Zonalar xaritasi</CardTitle>
            <CardDescription>Yashil — yaxshi {{ r.zones.good_pct }}%, sariq — oʻrta {{ r.zones.mid_pct }}%, binafsha — zaif {{ r.zones.weak_pct }}%
              <template v-if="r.zones.weak_side"> ({{ SIDE[r.zones.weak_side] }} qism)</template></CardDescription></CardHeader>
          <CardContent>
            <button type="button" class="mx-auto block cursor-zoom-in" title="Kattalashtirish" @click="viewing = 0">
              <img :src="analysesApi.mapUrl(job.id)" alt="Zonalar xaritasi" class="max-h-96 rounded-md transition hover:brightness-95" />
            </button>
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle class="text-base">Mavsum grafigi</CardTitle><CardDescription>NDVI: bu yil va oʻtgan yil</CardDescription></CardHeader>
          <CardContent>
            <button type="button" class="block w-full cursor-zoom-in" title="Kattalashtirish" @click="viewing = 1">
              <img :src="analysesApi.chartUrl(job.id)" alt="NDVI grafigi" class="w-full rounded-md transition hover:brightness-95" />
            </button>
          </CardContent>
        </Card>
      </div>

      <div class="grid gap-4 lg:grid-cols-3">
        <Card>
          <CardHeader><CardTitle class="text-base">Mavsum va stress</CardTitle></CardHeader>
          <CardContent class="space-y-2 text-sm">
            <div class="flex justify-between"><span class="text-muted-foreground">Oʻsuv mavsumi</span>
              <span>{{ r.growing_season ? `${fmtDate(r.growing_season.from)} – ${fmtDate(r.growing_season.to)}` : 'aniqlanmadi' }}</span></div>
            <div class="flex justify-between"><span class="text-muted-foreground">Suv stressi</span>
              <span>{{ r.stress_periods.length ? r.stress_periods.map((p) => `${fmtDate(p.from)} – ${fmtDate(p.to)}`).join(', ') : 'yoʻq' }}</span></div>
            <div class="flex justify-between"><span class="text-muted-foreground">Suratlar</span>
              <span>{{ r.scenes_used }} ta (bulutli: {{ r.scenes_rejected_cloud }})</span></div>
            <div class="flex justify-between"><span class="text-muted-foreground">Mamlakat</span><span>{{ r.country_code ?? '—' }}</span></div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle class="text-base">Suv balansi</CardTitle></CardHeader>
          <CardContent v-if="r.water" class="space-y-2 text-sm">
            <div class="flex justify-between"><span class="text-muted-foreground">Bugʻlanish (ET₀)</span><span>{{ fmtNum(r.water.et0_sum_mm) }} mm</span></div>
            <div class="flex justify-between"><span class="text-muted-foreground">Ekin ehtiyoji (ETc)</span><span>{{ fmtNum(r.water.etc_sum_mm) }} mm</span></div>
            <div class="flex justify-between"><span class="text-muted-foreground">Foydali yogʻin</span><span>{{ fmtNum(r.water.effective_rain_mm) }} mm</span></div>
            <div class="flex justify-between font-medium"><span>Sugʻorish (taxminiy)</span><span>{{ fmtNum(r.water.irrigation_need_m3_ha) }} m³/ga</span></div>
          </CardContent>
          <CardContent v-else class="text-muted-foreground text-sm">Ekin aniqlanmagani uchun hisoblanmadi</CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle class="text-base">Tavsiyalar</CardTitle><CardDescription>Fermerga uning tilida yuborilgan</CardDescription></CardHeader>
          <CardContent>
            <ul class="list-disc space-y-1 pl-4 text-sm">
              <li v-for="(rec, i) in r.recommendations" :key="i">{{ recommendation(rec.code, rec.params) }}</li>
              <li v-if="!r.recommendations.length" class="text-muted-foreground list-none">Tavsiya yoʻq</li>
            </ul>
          </CardContent>
        </Card>
      </div>
      <Card v-if="dbg">
        <CardHeader>
          <CardTitle class="text-base">Tahlil asoslari (debug)</CardTitle>
          <CardDescription>Fermerga koʻrsatilmaydi — xulosa nimaga tayangani. Zonalar dala medianasiga nisbatan:
            yaxshi ≥ {{ dbg.good_rel }}, sust &lt; {{ dbg.weak_rel }} (mediana NDVI {{ fmtNum(dbg.peak_ndvi_median, 2) }})</CardDescription>
        </CardHeader>
        <CardContent class="grid gap-6 lg:grid-cols-2">
          <div class="space-y-3">
            <button v-if="!ndviMissing" type="button" class="mx-auto block cursor-zoom-in" title="Kattalashtirish" @click="viewing = 2">
              <img :src="analysesApi.ndviUrl(job.id)" alt="NDVI xaritasi" class="max-h-96 rounded-md transition hover:brightness-95"
                   @error="ndviMissing = true" />
            </button>
            <p v-else class="text-muted-foreground rounded-md border border-dashed p-6 text-center text-sm">NDVI xaritasi saqlanmagan</p>
            <dl class="grid grid-cols-2 gap-x-4 gap-y-1.5 text-sm">
              <dt class="text-muted-foreground">Piksellar (10 m)</dt><dd class="text-right tabular-nums">{{ fmtNum(dbg.pixels) }}</dd>
              <dt class="text-muted-foreground">Sust boʻlak markazi</dt>
              <dd class="text-right tabular-nums">
                <a v-if="dbg.weak_lat != null" class="hover:underline" target="_blank" rel="noopener noreferrer"
                   :href="`https://www.google.com/maps?q=${dbg.weak_lat},${dbg.weak_lon}`">{{ dbg.weak_lat }}, {{ dbg.weak_lon }}</a>
                <span v-else>—</span>
              </dd>
              <dt class="text-muted-foreground">Qidiruv</dt><dd class="text-right">sahna buluti &lt; {{ dbg.search_cloud_lt }}%</dd>
              <dt class="text-muted-foreground">Surat qabul qilinadi</dt><dd class="text-right">dala ≥ {{ dbg.min_clear_pct }}% ochiq</dd>
            </dl>
          </div>
          <div class="space-y-2">
            <div class="flex items-center justify-between text-sm">
              <span><b>{{ dbg.scenes.filter((s) => s.used).length }}</b> ishlatildi ·
                <b>{{ dbg.scenes.filter((s) => !s.used).length }}</b> rad etildi</span>
              <Button variant="ghost" size="sm" @click="showAllScenes = !showAllScenes">
                {{ showAllScenes ? 'Faqat rad etilganlar' : 'Hammasini koʻrsatish' }}</Button>
            </div>
            <div class="max-h-96 overflow-y-auto rounded-md border">
              <table class="w-full text-sm">
                <thead class="bg-muted/50 text-muted-foreground sticky top-0 text-xs">
                  <tr><th class="px-3 py-1.5 text-left font-medium">Sana</th><th class="px-3 py-1.5 text-right font-medium">Sahna buluti</th>
                    <th class="px-3 py-1.5 text-right font-medium">Dala ochiq</th><th class="px-3 py-1.5 text-left font-medium">Holat</th></tr>
                </thead>
                <tbody class="divide-y">
                  <tr v-for="s in scenes" :key="s.date">
                    <td class="px-3 py-1.5">{{ fmtDate(s.date) }}</td>
                    <td class="px-3 py-1.5 text-right tabular-nums">{{ s.cloud_pct == null ? '—' : `${fmtNum(s.cloud_pct, 0)}%` }}</td>
                    <td class="px-3 py-1.5 text-right tabular-nums">{{ fmtNum(s.clear_pct, 0) }}%</td>
                    <td class="px-3 py-1.5" :class="s.used ? 'text-green-700 dark:text-green-500' : 'text-muted-foreground'">
                      {{ s.used ? 'Ishlatildi' : 'Ochiq kam — rad' }}</td>
                  </tr>
                  <tr v-if="!scenes.length"><td colspan="4" class="text-muted-foreground px-3 py-6 text-center">Rad etilgan surat yoʻq</td></tr>
                </tbody>
              </table>
            </div>
          </div>
        </CardContent>
      </Card>
      <p class="text-muted-foreground text-xs">Boshlangan {{ fmtDateTime(job.startedAt) }} · davomiyligi {{ duration(job.startedAt, job.finishedAt) }}</p>
    </div>
  </template>
  <ImageLightbox v-model:index="viewing" :images="gallery" />
</template>
