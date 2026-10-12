<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { Award, Search, UserCheck, UserPlus, Users } from '@lucide/vue'
import PageHeader from '@/components/app/PageHeader.vue'
import Paginator from '@/components/app/Paginator.vue'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { referralsApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Page, ReferralStats, ReferrerRow } from '@/api/types'
import { fmtDateTime, fmtNum } from '@/lib/format'
import { P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

/** Do'stlarni taklif qilish: kim nechta do'st olib kelgan, nechtasi faol, nechta mukofot olgan */
const router = useRouter()
const auth = useAuth()
const q = ref('')
const page = ref(0)
const data = ref<Page<ReferrerRow> | null>(null)
const stats = ref<ReferralStats | null>(null)

async function load() {
  try {
    data.value = await referralsApi.referrers({ q: q.value.trim() || undefined, page: page.value, size: 25 })
  } catch (e) {
    toast.error(errorMessage(e))
  }
}
let timer: ReturnType<typeof setTimeout> | undefined
watch(q, () => { clearTimeout(timer); timer = setTimeout(() => { page.value = 0; load() }, 350) })
watch(page, load)
onMounted(async () => {
  load()
  try { stats.value = await referralsApi.stats() } catch { /* ixtiyoriy */ }
})

function who(r: ReferrerRow) {
  return r.farmerName || (r.farmerUsername ? '@' + r.farmerUsername : `Fermer #${r.farmerId}`)
}
const pct = (a: number, b: number) => (b ? Math.round((a / b) * 100) : 0)
</script>

<template>
  <PageHeader title="Takliflar" description="Doʻstlarni taklif qilish dasturi. Qoidani (nechta doʻst, necha kun, qaysi tarif) — Sozlamalar → Takliflar" />

  <div class="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
    <div v-for="c in [
      { icon: Users, label: 'Taklif qilganlar', value: stats?.referrers, sub: 'fermer' },
      { icon: UserPlus, label: 'Kelgan doʻstlar', value: stats?.invited, sub: 'bot orqali qoʻshilgan' },
      { icon: UserCheck, label: 'Faollashgan', value: stats?.activated, sub: stats ? `${pct(stats.activated, stats.invited)}% — birinchi tahlil olgan` : '' },
      { icon: Award, label: 'Berilgan sovgʻalar', value: stats?.rewards, sub: 'bepul obuna' },
    ]" :key="c.label" class="bg-background rounded-xl border p-4 shadow-xs">
      <div class="text-muted-foreground flex items-center gap-2 text-xs"><component :is="c.icon" class="size-4" />{{ c.label }}</div>
      <p v-if="c.value !== undefined" class="mt-1.5 text-2xl font-semibold tabular-nums">{{ fmtNum(c.value) }}</p>
      <Skeleton v-else class="mt-2 h-7 w-16" />
      <p class="text-muted-foreground mt-0.5 text-xs">{{ c.sub }}&nbsp;</p>
    </div>
  </div>

  <Card>
    <CardContent class="space-y-3">
      <div class="relative w-full sm:w-72">
        <Search class="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
        <Input v-model="q" placeholder="Ism, username yoki kod…" class="pl-8" />
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Fermer</TableHead><TableHead>Kod</TableHead><TableHead class="text-right">Taklif qilgan</TableHead>
            <TableHead class="text-right">Faol</TableHead><TableHead class="text-right">Sovgʻalar</TableHead><TableHead>Oxirgi taklif</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <template v-if="!data">
            <TableRow v-for="i in 5" :key="i"><TableCell :colspan="6"><Skeleton class="h-5 w-full" /></TableCell></TableRow>
          </template>
          <TableRow v-for="r in data?.content ?? []" :key="r.farmerId" :class="auth.can(P.FARMER_READ) ? 'cursor-pointer' : ''"
                    @click="auth.can(P.FARMER_READ) && router.push(`/farmers/${r.farmerId}`)">
            <TableCell class="font-medium">{{ who(r) }}</TableCell>
            <TableCell class="text-muted-foreground font-mono text-xs">{{ r.code ?? '—' }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ r.invited }}</TableCell>
            <TableCell class="text-right tabular-nums">
              {{ r.activated }} <span class="text-muted-foreground text-xs">({{ pct(r.activated, r.invited) }}%)</span>
            </TableCell>
            <TableCell class="text-right tabular-nums">{{ r.rewards }}</TableCell>
            <TableCell class="text-muted-foreground whitespace-nowrap">{{ fmtDateTime(r.lastInvitedAt) }}</TableCell>
          </TableRow>
          <TableEmpty v-if="data && !data.content.length" :colspan="6">Hali hech kim doʻst taklif qilmagan</TableEmpty>
        </TableBody>
      </Table>
      <Paginator v-if="data" :page="data.page" :total-pages="data.totalPages" :total-elements="data.totalElements" @change="page = $event" />
    </CardContent>
  </Card>
</template>
