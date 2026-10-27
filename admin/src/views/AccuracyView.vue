<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { toast } from 'vue-sonner'
import PageHeader from '@/components/app/PageHeader.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { accuracyApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { AccuracyReport, Match, MatchResult, ReviewVerdict } from '@/api/types'
import { fmtDate, fmtNum } from '@/lib/format'
import { CAUSE, CONDITION, MATCH, MATCH_REASON, REVIEW } from '@/lib/labels'

/** L2: tizim xulosalari haqiqatga qanchalik mos — xodim baholari va kuzatuvlar bilan avtomatik solishtirish */
const report = ref<AccuracyReport | null>(null)
const scope = ref<'ALL' | 'LAB' | 'REAL'>('ALL')

let req = 0                                            // filtr tez almashsa — eski javob yangisini bosmasin
async function load() {
  const my = ++req
  try {
    const r = await accuracyApi.report(scope.value === 'ALL' ? undefined : scope.value === 'LAB')
    if (my === req) report.value = r
  } catch (e) {
    if (my === req) toast.error(errorMessage(e))
  }
}
onMounted(load)
watch(scope, load)

const TONE: Record<MatchResult, 'green' | 'red' | 'gray' | 'amber'> = { AGREE: 'green', DISAGREE: 'red', NOT_APPLICABLE: 'gray', NO_DATA: 'amber' }
const KIND: Record<string, string> = { ANALYSIS: 'Tahlil (zonalar)', MONITORING: 'Monitoring (sustlashuv)' }

/** To'g'ri ulushi: qisman — yarim ball. Baholanganlar soni bilan birga (kam bo'lsa foiz ishonchsiz) */
function manualStat(m: Partial<Record<ReviewVerdict, number>> | undefined) {
  const c = m?.CORRECT ?? 0, p = m?.PARTIAL ?? 0, w = m?.WRONG ?? 0, n = c + p + w
  return { n, pct: n ? Math.round(((c + p / 2) / n) * 100) : null, c, p, w }
}
/** Mos ulushi faqat solishtirib bo'lganlar orasida (qo'llanmaydi / ma'lumot yo'q — chetda) */
function autoStat(m: Partial<Record<MatchResult, number>> | undefined) {
  const a = m?.AGREE ?? 0, d = m?.DISAGREE ?? 0
  return { n: a + d, pct: a + d ? Math.round((a / (a + d)) * 100) : null, a, d, na: m?.NOT_APPLICABLE ?? 0, nd: m?.NO_DATA ?? 0 }
}
const kinds = ['ANALYSIS', 'MONITORING'] as const
const stats = computed(() => kinds.map((k) => ({ k, manual: manualStat(report.value?.manual[k]), auto: autoStat(report.value?.auto[k]) })))

function matchTitle(m: Match) {
  return `${MATCH_REASON[m.reason] ?? m.reason}${m.distanceM != null ? ` (${fmtNum(m.distanceM)} m)` : ''}`
}
</script>

<template>
  <PageHeader title="Aniqlik" description="Tizim xulosalari haqiqatga qanchalik mos: xodim baholari va dala kuzatuvlari bilan solishtirish">
    <div class="flex gap-1.5" role="group" aria-label="Dalalar">
      <button v-for="(l, k) in { ALL: 'Hammasi', LAB: 'Lab', REAL: 'Real' }" :key="k" type="button" :aria-pressed="scope === k"
              class="h-8 rounded-md border px-3 text-sm font-medium transition-colors"
              :class="scope === k ? 'border-primary bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-accent'"
              @click="scope = k">{{ l }}</button>
    </div>
  </PageHeader>

  <div v-if="!report" class="space-y-4"><Skeleton class="h-32 w-full" /><Skeleton class="h-64 w-full" /></div>
  <div v-else class="space-y-4">
    <div class="grid gap-4 md:grid-cols-2">
      <Card v-for="s in stats" :key="s.k">
        <CardHeader><CardTitle class="text-base">{{ KIND[s.k] }}</CardTitle></CardHeader>
        <CardContent class="grid grid-cols-2 gap-4">
          <div>
            <div class="text-muted-foreground text-xs">Xodim bahosi</div>
            <div class="text-3xl font-semibold tabular-nums">{{ s.manual.pct == null ? '—' : `${s.manual.pct}%` }}</div>
            <div class="text-muted-foreground text-xs tabular-nums">
              {{ s.manual.n }} ta: {{ s.manual.c }} {{ REVIEW.CORRECT.toLowerCase() }} · {{ s.manual.p }} qisman · {{ s.manual.w }} {{ REVIEW.WRONG.toLowerCase() }}</div>
          </div>
          <div>
            <div class="text-muted-foreground text-xs">Kuzatuv bilan mos</div>
            <div class="text-3xl font-semibold tabular-nums">{{ s.auto.pct == null ? '—' : `${s.auto.pct}%` }}</div>
            <div class="text-muted-foreground text-xs tabular-nums">
              {{ s.auto.a }} mos · {{ s.auto.d }} mos emas<template v-if="s.auto.na || s.auto.nd"> · {{ s.auto.na + s.auto.nd }} solishtirilmadi</template></div>
          </div>
        </CardContent>
      </Card>
    </div>
    <p class="text-muted-foreground text-xs">
      Foiz: qisman — yarim ball. 10 tadan kam baho yoki kuzatuvda foiz hali ishonchsiz.
      Avtomatik solishtirish taxminiy: tahlil faqat eng katta sust boʻlak markazini saqlaydi.
    </p>

    <Card>
      <CardHeader>
        <CardTitle class="text-base">Kuzatuvlar</CardTitle>
        <CardDescription>Har kuzatuv mavsum choʻqqisi eng yaqin tahlil (±45 kun) va monitoring (−21…+7 kun) bilan solishtiriladi.
          Kuzatuv dala sahifasida qoʻshiladi<template v-if="report.truncated"> · faqat eng yangi 1000 tasi koʻrsatilgan</template></CardDescription>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Sana</TableHead><TableHead>Dala</TableHead><TableHead>Aslida</TableHead><TableHead>Joy</TableHead>
              <TableHead>Tahlil</TableHead><TableHead>Monitoring</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="r in report.rows" :key="r.observation.id">
              <TableCell class="whitespace-nowrap">{{ fmtDate(r.observation.observedOn) }}</TableCell>
              <TableCell>
                <RouterLink :to="`/fields/${r.observation.fieldId}`" class="hover:underline">{{ r.fieldName }}</RouterLink>
                <span v-if="r.lab" class="text-muted-foreground ml-1 text-xs">Lab</span>
              </TableCell>
              <TableCell>
                {{ CONDITION[r.observation.condition] }}
                <span v-if="r.observation.cause" class="text-muted-foreground block text-xs">{{ CAUSE[r.observation.cause] }}</span>
              </TableCell>
              <TableCell class="text-muted-foreground text-xs">{{ r.observation.lat != null ? 'Nuqta' : 'Butun dala' }}</TableCell>
              <TableCell>
                <div class="flex items-center gap-1.5">
                  <StatusBadge :tone="TONE[r.analysis.result]" :label="MATCH[r.analysis.result]" />
                  <RouterLink v-if="r.analysis.refId" :to="`/analyses/${r.analysis.refId}`" class="text-muted-foreground text-xs hover:underline">#{{ r.analysis.refId }}</RouterLink>
                </div>
                <span class="text-muted-foreground block max-w-64 text-xs">{{ matchTitle(r.analysis) }}</span>
              </TableCell>
              <TableCell>
                <StatusBadge :tone="TONE[r.monitoring.result]" :label="MATCH[r.monitoring.result]" />
                <span class="text-muted-foreground block max-w-64 text-xs">{{ matchTitle(r.monitoring) }}</span>
              </TableCell>
            </TableRow>
            <TableEmpty v-if="!report.rows.length" :colspan="6">Kuzatuv yoʻq — dala sahifasida "Holat tarixi" → "Kuzatuv"</TableEmpty>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  </div>
</template>
