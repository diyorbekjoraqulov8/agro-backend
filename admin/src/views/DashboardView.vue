<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { LandPlot, Map, PhoneCall, Users } from '@lucide/vue'
import PageHeader from '@/components/app/PageHeader.vue'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { statsApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Overview, SourcesReport } from '@/api/types'
import { fmtNum, daysFromNow } from '@/lib/format'
import { JOB_STATUS, LEAD } from '@/lib/labels'
import { toast } from 'vue-sonner'

const overview = ref<Overview | null>(null)
const sources = ref<SourcesReport | null>(null)
const from = ref(daysFromNow(-90))
const to = ref(daysFromNow(0))

const kpis = computed(() => overview.value ? [
  { label: 'Fermerlar', value: fmtNum(overview.value.farmers), icon: Users },
  { label: 'Dalalar', value: fmtNum(overview.value.fields), icon: Map },
  { label: 'Jami maydon', value: `${fmtNum(overview.value.totalAreaHa, 1)} ga`, icon: LandPlot },
  { label: 'Lead’lar', value: fmtNum(overview.value.leads), icon: PhoneCall },
] : [])

function bars(m: Record<string, number>, labels: Record<string, string> = {}) {
  const max = Math.max(1, ...Object.values(m))
  return Object.entries(m).sort((a, b) => b[1] - a[1]).map(([k, v]) => ({ key: labels[k] ?? k, v, pct: (v / max) * 100 }))
}

async function loadSources() {
  try {
    sources.value = await statsApi.sources(from.value, to.value)
  } catch (e) {
    toast.error(errorMessage(e))
  }
}

onMounted(async () => {
  try {
    overview.value = await statsApi.overview()
  } catch (e) {
    toast.error(errorMessage(e))
  }
  loadSources()
})
watch([from, to], loadSources)
</script>

<template>
  <PageHeader title="Dashboard" description="Fermerlar, dalalar va tahlillar boʻyicha umumiy holat" />

  <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
    <template v-if="overview">
      <Card v-for="k in kpis" :key="k.label">
        <CardHeader>
          <CardDescription>{{ k.label }}</CardDescription>
          <CardAction><component :is="k.icon" class="text-muted-foreground size-4" /></CardAction>
          <CardTitle class="text-2xl font-semibold tabular-nums">{{ k.value }}</CardTitle>
        </CardHeader>
      </Card>
    </template>
    <Skeleton v-for="i in (overview ? 0 : 4)" :key="i" class="h-28 rounded-xl" />
  </div>

  <div v-if="overview" class="mt-4 grid gap-4 lg:grid-cols-3">
    <Card v-for="block in [
      { title: 'Tahlillar holati', data: bars(overview.analysesByStatus, JOB_STATUS) },
      { title: 'Lead holati', data: bars(overview.farmersByLeadStatus, LEAD) },
      { title: 'Mamlakatlar', data: bars(overview.farmersByCountry) },
    ]" :key="block.title">
      <CardHeader><CardTitle class="text-base">{{ block.title }}</CardTitle></CardHeader>
      <CardContent class="space-y-3">
        <p v-if="!block.data.length" class="text-muted-foreground text-sm">Maʼlumot yoʻq</p>
        <div v-for="b in block.data" :key="b.key" class="space-y-1">
          <div class="flex justify-between text-sm"><span>{{ b.key }}</span><span class="tabular-nums">{{ fmtNum(b.v) }}</span></div>
          <div class="bg-muted h-1.5 overflow-hidden rounded-full"><div class="bg-primary h-full rounded-full" :style="{ width: `${b.pct}%` }" /></div>
        </div>
      </CardContent>
    </Card>
  </div>

  <Card class="mt-4">
    <CardHeader class="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <CardTitle class="text-base">Reklama kanallari voronkasi</CardTitle>
        <CardDescription>Kelgan → dala chizgan → tahlil olgan → lead (fermer kelgan sana boʻyicha)</CardDescription>
      </div>
      <div class="flex items-center gap-2">
        <Input v-model="from" type="date" class="w-40" />
        <span class="text-muted-foreground">—</span>
        <Input v-model="to" type="date" class="w-40" />
      </div>
    </CardHeader>
    <CardContent>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Manba</TableHead><TableHead class="text-right">Kelgan</TableHead><TableHead class="text-right">Dala</TableHead>
            <TableHead class="text-right">Tahlil</TableHead><TableHead class="text-right">Lead</TableHead><TableHead class="text-right">Konversiya</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="s in sources?.sources ?? []" :key="s.source">
            <TableCell class="font-medium">{{ s.source }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ fmtNum(s.farmers) }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ fmtNum(s.withField) }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ fmtNum(s.analyzed) }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ fmtNum(s.leads) }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ s.leadRatePct }}%</TableCell>
          </TableRow>
          <TableEmpty v-if="sources && !sources.sources.length" :colspan="6">Bu davrda fermer kelmagan</TableEmpty>
        </TableBody>
      </Table>
    </CardContent>
  </Card>
</template>
