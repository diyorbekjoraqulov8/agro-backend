<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, FlaskConical, Loader2, Satellite, Trash2 } from '@lucide/vue'
import { toast } from 'vue-sonner'
import PageHeader from '@/components/app/PageHeader.vue'
import FieldMap from '@/components/app/FieldMap.vue'
import MonitoringCard from '@/components/app/MonitoringCard.vue'
import FieldHistoryCard from '@/components/app/FieldHistoryCard.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { jobTone } from '@/components/app/badges'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { analysesApi, fieldsApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { AnalysisListItem, FieldResponse, WaterView } from '@/api/types'
import { fmtDate, fmtDateTime, fmtNum } from '@/lib/format'
import { CROP, IRRIGATION, IRRIGATION_SOURCE, JOB_KIND, JOB_STATUS, P, REMINDER, SOIL, SOWING } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const id = Number(route.params.id)
const field = ref<FieldResponse | null>(null)
const jobs = ref<AnalysisListItem[]>([])
const water = ref<WaterView | null>(null)
const runOpen = ref(false)
const running = ref(false)
const win = ref({ from: '', to: '' })

async function load() {
  try {
    field.value = await fieldsApi.get(id)
    fieldsApi.water(id).then((w) => (water.value = w)).catch(() => {})
    if (auth.can(P.ANALYSIS_READ)) {
      jobs.value = (await analysesApi.search({ fieldId: id, size: 50, sort: 'createdDate,desc' })).content
    }
  } catch (e) {
    toast.error(errorMessage(e))
  }
}

async function run() {
  running.value = true
  try {
    const body = win.value.from && win.value.to ? { seasonStart: win.value.from, seasonEnd: win.value.to } : undefined
    const job = await analysesApi.run(id, body)
    toast.success(`Tahlil #${job.id} navbatga qoʻyildi`)
    runOpen.value = false
    router.push(`/analyses/${job.id}`)
  } catch (e) {
    toast.error(errorMessage(e))
  } finally {
    running.value = false
  }
}

// Ikki bosish — tasdiq (OnboardingView uslubi)
const confirmRemove = ref(false)
async function removeLab() {
  if (!confirmRemove.value) {
    confirmRemove.value = true
    setTimeout(() => (confirmRemove.value = false), 4000)
    return
  }
  try {
    await fieldsApi.removeLab(id)
    toast.success('Dala oʻchirildi')
    router.push('/fields')
  } catch (e) {
    toast.error(errorMessage(e))
  }
}

onMounted(load)
</script>

<template>
  <Button variant="ghost" size="sm" class="mb-2 -ml-2" @click="router.back()"><ArrowLeft /> Orqaga</Button>
  <div v-if="!field" class="space-y-4"><Skeleton class="h-10 w-72" /><Skeleton class="h-80 w-full" /></div>
  <template v-else>
    <PageHeader :title="field.name" :description="`${CROP[field.crop]} · ${field.areaHa != null ? fmtNum(field.areaHa, 2) + ' ga' : 'maydon tahlildan keyin'}`">
      <Badge v-if="field.lab" variant="secondary"><FlaskConical /> Lab dala</Badge>
      <Button v-if="field.lab && auth.can(P.FIELD_WRITE)" variant="outline" @click="removeLab"><Trash2 /> {{ confirmRemove ? 'Yana bir bosing' : 'Oʻchirish' }}</Button>
      <Button v-if="auth.can(P.ANALYSIS_RUN)" @click="runOpen = true"><Satellite /> Tahlilga yuborish</Button>
    </PageHeader>
    <div class="grid gap-4 lg:grid-cols-3">
      <Card class="lg:col-span-2">
        <CardContent><FieldMap :polygon="field.boundaryGeojson" /></CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle class="text-base">Maʼlumotlar</CardTitle></CardHeader>
        <CardContent>
          <dl class="space-y-3 text-sm">
            <div><dt class="text-muted-foreground">Fermer</dt>
              <dd><RouterLink :to="`/farmers/${field.farmerId}`" class="font-medium hover:underline">{{ field.farmerName ?? `#${field.farmerId}` }}</RouterLink></dd></div>
            <div><dt class="text-muted-foreground">Markaz</dt><dd class="tabular-nums">{{ field.centroidLat ?? '—' }}, {{ field.centroidLon ?? '—' }}</dd></div>
            <div><dt class="text-muted-foreground">Nuqtalar</dt><dd>{{ (field.boundaryGeojson.coordinates[0]?.length ?? 1) - 1 }}</dd></div>
            <div><dt class="text-muted-foreground">Qoʻshilgan</dt><dd>{{ fmtDateTime(field.createdDate) }}</dd></div>
            <div><dt class="text-muted-foreground">Ekish sanasi</dt>
              <dd>{{ !field.season ? '—' : field.season.sowing === 'SOWN' ? `${fmtDate(field.season.sownOn)}${field.season.sownOnApprox ? ' (taxminan)' : ''}` : SOWING[field.season.sowing] }}</dd></div>
            <div><dt class="text-muted-foreground">Sugʻorish usuli</dt><dd>{{ field.irrigationMethod ? IRRIGATION[field.irrigationMethod] : '—' }}</dd></div>
          </dl>
        </CardContent>
      </Card>
      <Card v-if="water" class="lg:col-span-3">
        <CardHeader>
          <CardTitle class="flex items-center gap-2 text-base">Sugʻorish eslatmasi
            <StatusBadge v-if="!water.configured" tone="gray" label="Sozlanmagan" />
            <StatusBadge v-else-if="!water.enabled" tone="gray" label="Oʻchirilgan" />
            <StatusBadge v-else tone="green" label="Yoqilgan" />
          </CardTitle>
          <CardDescription>Tuproq namligi balansi (FAO-56) · eslatma dala mahalliy vaqti bilan 07:00 da{{ water.timezone ? ` (${water.timezone})` : '' }}</CardDescription>
        </CardHeader>
        <CardContent class="grid gap-6 text-sm md:grid-cols-3">
          <dl class="space-y-2">
            <div class="flex justify-between"><dt class="text-muted-foreground">Oxirgi sugʻorish</dt><dd>{{ fmtDate(water.lastIrrigatedOn) }}</dd></div>
            <div class="flex justify-between"><dt class="text-muted-foreground">Keyingi sugʻorish</dt><dd class="font-medium">{{ water.nextIrrigationOn ? fmtDate(water.nextIrrigationOn) : (water.configured ? '7 kunda kerak emas' : '—') }}</dd></div>
            <div class="flex justify-between"><dt class="text-muted-foreground">Suv sarflangan (RAW’ga nisbatan)</dt><dd class="tabular-nums">{{ water.depletionPct != null ? `${fmtNum(water.depletionPct, 1)}%` : '—' }}</dd></div>
            <div class="flex justify-between"><dt class="text-muted-foreground">Oxirgi eslatma</dt><dd>{{ water.lastReminderOn ? `${fmtDate(water.lastReminderOn)} · ${REMINDER[water.lastReminderKind ?? ''] ?? ''}` : '—' }}</dd></div>
          </dl>
          <dl class="space-y-2">
            <div class="flex justify-between"><dt class="text-muted-foreground">Tuproq (fermer)</dt><dd>{{ water.soil ? SOIL[water.soil] : 'bilmaydi' }}</dd></div>
            <div class="flex justify-between"><dt class="text-muted-foreground">Tuproq (SoilGrids)</dt><dd>{{ water.soilAuto ? `${SOIL[water.soilAuto]} · qum ${fmtNum(water.sandPct, 0)}%, loy ${fmtNum(water.clayPct, 0)}%` : '—' }}</dd></div>
            <div class="flex justify-between"><dt class="text-muted-foreground">Hisobda</dt><dd class="font-medium">{{ water.soilUsed ? SOIL[water.soilUsed] : '—' }}</dd></div>
            <StatusBadge v-if="water.soilMismatch" tone="amber" label="Fermer javobi xaritadan farq qiladi" />
          </dl>
          <dl class="space-y-2">
            <div class="flex justify-between"><dt class="text-muted-foreground">Oʻsish koʻrsatkichi (NDVI)</dt><dd class="tabular-nums">{{ water.ndvi != null ? `${fmtNum(water.ndvi, 2)} · ${fmtDate(water.ndviOn)}` : '—' }}</dd></div>
            <div class="text-muted-foreground">Sugʻorish tarixi</div>
            <ul class="space-y-1">
              <li v-for="(e, i) in water.events" :key="i" class="flex justify-between"><span>{{ fmtDate(e.irrigatedOn) }}</span><span class="text-muted-foreground">{{ IRRIGATION_SOURCE[e.source] }}</span></li>
              <li v-if="!water.events.length" class="text-muted-foreground">—</li>
            </ul>
          </dl>
        </CardContent>
      </Card>

      <FieldHistoryCard v-if="auth.can(P.FIELD_READ) && auth.can(P.ANALYSIS_READ)" :field-id="id" />
      <MonitoringCard :field-id="id" />

      <Card v-if="auth.can(P.ANALYSIS_READ)" class="lg:col-span-3">
        <CardHeader><CardTitle class="text-base">Tahlillar</CardTitle><CardDescription>Shu dala boʻyicha barcha tahlillar</CardDescription></CardHeader>
        <CardContent>
          <Table>
            <TableHeader><TableRow><TableHead>#</TableHead><TableHead>Turi</TableHead><TableHead>Holat</TableHead><TableHead>Davr</TableHead><TableHead>Yaratilgan</TableHead></TableRow></TableHeader>
            <TableBody>
              <TableRow v-for="j in jobs" :key="j.id" class="cursor-pointer" @click="router.push(`/analyses/${j.id}`)">
                <TableCell class="font-medium">#{{ j.id }}</TableCell>
                <TableCell>{{ JOB_KIND[j.kind] }}</TableCell>
                <TableCell><StatusBadge :tone="jobTone[j.status]" :label="JOB_STATUS[j.status]" /></TableCell>
                <TableCell class="tabular-nums">{{ fmtDate(j.seasonStart) }} – {{ fmtDate(j.seasonEnd) }}</TableCell>
                <TableCell class="text-muted-foreground">{{ fmtDateTime(j.createdDate) }}</TableCell>
              </TableRow>
              <TableEmpty v-if="!jobs.length" :colspan="5">Tahlil yoʻq</TableEmpty>
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  </template>

  <Dialog v-model:open="runOpen">
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>Tahlilga yuborish</DialogTitle>
        <DialogDescription>Boʻsh qoldirsangiz — oxirgi 12 oy. Xodim tahlili fermerga avtomatik yuborilmaydi va uning kvotasiga kirmaydi.</DialogDescription>
      </DialogHeader>
      <div class="grid grid-cols-2 gap-3">
        <div class="space-y-1.5"><Label>Boshlanish</Label><Input v-model="win.from" type="date" /></div>
        <div class="space-y-1.5"><Label>Tugash</Label><Input v-model="win.to" type="date" /></div>
      </div>
      <DialogFooter>
        <Button variant="outline" @click="runOpen = false">Bekor</Button>
        <Button :disabled="running" @click="run"><Loader2 v-if="running" class="animate-spin" /> Yuborish</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
