<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Eraser, Loader2, LocateFixed, Undo2 } from '@lucide/vue'
import { toast } from 'vue-sonner'
import PageHeader from '@/components/app/PageHeader.vue'
import FieldDrawMap from '@/components/app/FieldDrawMap.vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { fieldsApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Crop, IrrigationType } from '@/api/types'
import { fmtNum } from '@/lib/format'
import { CROP, IRRIGATION } from '@/lib/labels'

const router = useRouter()
const NONE = 'NONE'
const map = ref<InstanceType<typeof FieldDrawMap>>()
const points = ref<[number, number][]>([])
const form = ref({ name: '', crop: 'WHEAT' as Crop, sownOn: '', irrigation: NONE as IrrigationType | typeof NONE })
const place = ref('')
const geojson = ref('')
const saving = ref(false)

// Sferik maydon (backend FieldIntakeService.areaHa bilan bir xil) — chizayotganda taxminiy ko'rsatish uchun
const areaHa = computed(() => {
  const p = points.value
  if (p.length < 3) return 0
  const r = 6378137, rad = Math.PI / 180
  let s = 0
  for (let i = 0; i < p.length; i++) {
    const a = p[i]!, b = p[(i + 1) % p.length]!, c = p[(i + 2) % p.length]!
    s += (c[0] - a[0]) * rad * Math.sin(b[1] * rad)
  }
  return Math.abs((s * r * r) / 2) / 10_000
})

function locate() {
  const m = place.value.match(/^\s*(-?\d+(?:\.\d+)?)\s*[,\s]\s*(-?\d+(?:\.\d+)?)\s*$/)
  if (!m) return toast.error('Koordinata: "41.31, 69.24" (kenglik, uzunlik)')
  map.value?.goTo(Number(m[1]), Number(m[2]))
}

/** GeoJSON Polygon/Feature yoki [[lon, lat], ...] — boshqa joyda chizilgan dalani ko'chirish */
async function importGeojson() {
  try {
    let g = JSON.parse(geojson.value)
    if (g.type === 'FeatureCollection') g = g.features?.[0]
    if (g.type === 'Feature') g = g.geometry
    const ring: unknown = Array.isArray(g) ? g : g.type === 'Polygon' ? g.coordinates?.[0] : null
    if (!Array.isArray(ring) || ring.length < 3) throw new Error()
    const pts = ring.map((x) => [Number(x[0]), Number(x[1])] as [number, number])
    const [f, l] = [pts[0]!, pts[pts.length - 1]!]
    if (f[0] === l[0] && f[1] === l[1]) pts.pop()              // yopiq halqa — oxirgisi takror
    if (pts.some(([lon, lat]) => !Number.isFinite(lon) || !Number.isFinite(lat))) throw new Error()
    points.value = pts
    await nextTick()
    map.value?.fit()
  } catch {
    toast.error('GeoJSON Polygon kutilgan: {"type":"Polygon","coordinates":[[[lon,lat],...]]}')
  }
}

async function save() {
  if (points.value.length < 3) return toast.error('Xaritada kamida 3 ta nuqta belgilang')
  if (!form.value.name.trim()) return toast.error('Dala nomini yozing')
  saving.value = true
  try {
    const f = await fieldsApi.createLab({
      name: form.value.name.trim(), crop: form.value.crop, coordinates: points.value,
      sownOn: form.value.sownOn || undefined,
      irrigationMethod: form.value.irrigation === NONE ? undefined : form.value.irrigation,
    })
    toast.success('Dala qoʻshildi, birinchi tahlil navbatda')
    router.push(`/fields/${f.id}`)
  } catch (e) {
    toast.error(errorMessage(e))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Button variant="ghost" size="sm" class="mb-2 -ml-2" @click="router.back()"><ArrowLeft /> Orqaga</Button>
  <PageHeader title="Lab dala qoʻshish"
              description="Dala fermer dalasi kabi kuzatiladi: tahlil, monitoring, ob-havo. Xabarlar Telegram’ga yuborilmaydi" />
  <div class="grid gap-4 lg:grid-cols-3">
    <Card class="lg:col-span-2">
      <CardContent class="space-y-3">
        <div class="flex flex-wrap items-center gap-2">
          <Input v-model="place" placeholder="41.31, 69.24" class="w-48" @keydown.enter="locate" />
          <Button variant="outline" size="sm" @click="locate"><LocateFixed /> Borish</Button>
          <div class="flex-1" />
          <span class="text-muted-foreground text-sm tabular-nums">{{ points.length }} nuqta · ≈ {{ fmtNum(areaHa, 2) }} ga</span>
          <Button variant="outline" size="sm" :disabled="!points.length" @click="points = points.slice(0, -1)"><Undo2 /> Orqaga</Button>
          <Button variant="outline" size="sm" :disabled="!points.length" @click="points = []"><Eraser /> Tozalash</Button>
        </div>
        <FieldDrawMap ref="map" v-model="points" />
        <p class="text-muted-foreground text-xs">Dala chegarasi boʻylab bosib chiqing — poligon avtomatik yopiladi.</p>
      </CardContent>
    </Card>
    <div class="space-y-4">
      <Card>
        <CardHeader><CardTitle class="text-base">Maʼlumotlar</CardTitle></CardHeader>
        <CardContent class="space-y-3">
          <div class="space-y-1.5"><Label for="name">Nomi</Label><Input id="name" v-model="form.name" maxlength="80" placeholder="Jizzax, bugʻdoy 1" /></div>
          <div class="space-y-1.5"><Label>Ekin</Label>
            <Select v-model="form.crop">
              <SelectTrigger class="w-full"><SelectValue /></SelectTrigger>
              <SelectContent><SelectItem v-for="(l, k) in CROP" :key="k" :value="k">{{ l }}</SelectItem></SelectContent>
            </Select>
          </div>
          <div class="space-y-1.5"><Label for="sown">Ekish sanasi <span class="text-muted-foreground">(ixtiyoriy)</span></Label>
            <Input id="sown" v-model="form.sownOn" type="date" />
          </div>
          <div class="space-y-1.5"><Label>Sugʻorish usuli</Label>
            <Select v-model="form.irrigation">
              <SelectTrigger class="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem :value="NONE">Nomaʼlum</SelectItem>
                <SelectItem v-for="(l, k) in IRRIGATION" :key="k" :value="k">{{ l }}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <Button class="w-full" :disabled="saving" @click="save"><Loader2 v-if="saving" class="animate-spin" /> Saqlash va tahlil qilish</Button>
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle class="text-base">GeoJSON’dan</CardTitle>
          <CardDescription>Boshqa joyda chizilgan chegarani qoʻying</CardDescription></CardHeader>
        <CardContent class="space-y-2">
          <Textarea v-model="geojson" rows="4" class="font-mono text-xs" placeholder='{"type":"Polygon","coordinates":[[[69.2,41.3],...]]}' />
          <Button variant="outline" size="sm" :disabled="!geojson.trim()" @click="importGeojson">Xaritaga qoʻyish</Button>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
