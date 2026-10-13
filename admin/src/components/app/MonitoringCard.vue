<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import ImageLightbox from '@/components/app/ImageLightbox.vue'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { fieldsApi } from '@/api'
import type { FieldMonitoring, MonitoringVerdict } from '@/api/types'
import { fmtDate, fmtDateTime, fmtNum } from '@/lib/format'
import { SIDE } from '@/lib/labels'

/** C3: dala monitoringi — navbat holati va har yangi surat natijasi (sustlashganda — o'zgarish xaritasi) */
const props = defineProps<{ fieldId: number }>()
const data = ref<FieldMonitoring | null>(null)
const viewing = ref<number | null>(null)

const VERDICT: Record<MonitoringVerdict, { l: string; t: 'red' | 'green' | 'gray' | 'amber' | 'blue' }> = {
  DECLINE: { l: 'Sustlashdi — xabar ketdi', t: 'red' }, STABLE: { l: 'Oʻzgarish yoʻq', t: 'green' },
  HARVEST: { l: 'Oʻrim-yigʻim?', t: 'amber' }, NO_CROP: { l: 'Ekin yoʻq', t: 'gray' }, BASELINE: { l: 'Boshlandi', t: 'blue' },
}
const fileUrl = (h: string) => `/api/v1/files/${h}`
const maps = computed(() => (data.value?.events ?? []).filter((e) => e.mapFileHashId)
  .map((e) => ({ url: fileUrl(e.mapFileHashId!), name: `monitoring-${props.fieldId}-${e.sceneDate}.png`, caption: `${fmtDate(e.sceneDate)} · ${e.declinePct}% sustlashgan`, hashId: e.mapFileHashId })))
const open = (hashId: string) => { viewing.value = maps.value.findIndex((m) => m.hashId === hashId) }

onMounted(async () => {
  try { data.value = await fieldsApi.monitoring(props.fieldId) } catch { /* ixtiyoriy karta */ }
})
</script>

<template>
  <Card v-if="data" class="lg:col-span-3">
    <CardHeader>
      <CardTitle class="text-base">Avtomatik monitoring</CardTitle>
      <CardDescription>
        <template v-if="data.status">
          Oxirgi surat: {{ data.status.lastSceneDate ? fmtDate(data.status.lastSceneDate) : '—' }} ·
          keyingi tekshiruv: {{ fmtDateTime(data.status.nextCheckAt) }}
          <span v-if="data.status.failures" class="text-destructive"> · {{ data.status.failures }} marta xato</span>
        </template>
        <template v-else>Kuzatilmayapti — fermer tarifi (Sozlamalar → Monitoring) yoki dala hali navbatga tushmagan</template>
      </CardDescription>
    </CardHeader>
    <CardContent>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Surat</TableHead><TableHead>Solishtirildi</TableHead><TableHead class="text-right">NDVI</TableHead>
            <TableHead class="text-right">Sustlashgan</TableHead><TableHead class="text-right">Yaxshilangan</TableHead>
            <TableHead>Natija</TableHead><TableHead />
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="e in data.events" :key="e.id">
            <TableCell>{{ fmtDate(e.sceneDate) }}</TableCell>
            <TableCell class="text-muted-foreground">{{ e.refDate ? fmtDate(e.refDate) : '—' }}</TableCell>
            <TableCell class="text-right tabular-nums">
              {{ fmtNum(e.ndvi) }}<span v-if="e.ndviRef != null" class="text-muted-foreground text-xs"> ← {{ fmtNum(e.ndviRef) }}</span>
            </TableCell>
            <TableCell class="text-right tabular-nums">
              {{ e.declinePct != null ? `${e.declinePct}%` : '—' }}
              <span v-if="e.declineSide && e.declinePct" class="text-muted-foreground text-xs"> {{ SIDE[e.declineSide] }}</span>
            </TableCell>
            <TableCell class="text-right tabular-nums">{{ e.improvePct != null ? `${e.improvePct}%` : '—' }}</TableCell>
            <TableCell><StatusBadge :tone="VERDICT[e.verdict].t" :label="VERDICT[e.verdict].l" /></TableCell>
            <TableCell class="w-14">
              <button v-if="e.mapFileHashId" type="button" class="block" @click="open(e.mapFileHashId)">
                <img :src="`${fileUrl(e.mapFileHashId)}/thumb`" alt="Oʻzgarish xaritasi" class="size-10 rounded-md border object-cover" loading="lazy" />
              </button>
            </TableCell>
          </TableRow>
          <TableEmpty v-if="!data.events.length" :colspan="7">Hali surat koʻrilmagan</TableEmpty>
        </TableBody>
      </Table>
    </CardContent>
  </Card>
  <ImageLightbox v-model:index="viewing" :images="maps" />
</template>
