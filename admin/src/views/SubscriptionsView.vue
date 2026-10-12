<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import PageHeader from '@/components/app/PageHeader.vue'
import Paginator from '@/components/app/Paginator.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { Card, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { billingApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Page, Plan, Subscription } from '@/api/types'
import { daysFromNow, fmtDate, fmtDateTime, fmtMoney } from '@/lib/format'
import { PAYMENT } from '@/lib/labels'

const router = useRouter()
const ALL = 'ALL'
const f = reactive({ status: ALL, planId: ALL, expiring: ALL })
const page = ref(0)
const data = ref<Page<Subscription> | null>(null)
const plans = ref<Plan[]>([])

async function load() {
  try {
    data.value = await billingApi.subscriptions({
      status: f.status === ALL ? undefined : f.status,
      planId: f.planId === ALL ? undefined : f.planId,
      expiringBefore: f.expiring === ALL ? undefined : daysFromNow(Number(f.expiring)),
      page: page.value, size: 25, sort: f.expiring === ALL ? 'paidAt,desc' : 'validUntil,asc',
    })
  } catch (e) {
    toast.error(errorMessage(e))
  }
}
watch(() => ({ ...f }), () => { page.value = 0; load() })
watch(page, load)
onMounted(async () => {
  load()
  try { plans.value = (await billingApi.plans()).filter((p) => !p.systemPlan) } catch { /* ro'yxat filtri uchun */ }
})
</script>

<template>
  <PageHeader title="Toʻlovlar" description="Barcha fermerlarning obunalari. Toʻlov qoʻshish — fermer kartochkasida" />
  <Card>
    <CardContent class="space-y-3">
      <div class="flex flex-wrap gap-2">
        <Select v-model="f.expiring">
          <SelectTrigger class="w-56"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem :value="ALL">Hammasi</SelectItem>
            <SelectItem value="7">7 kunda tugaydigan</SelectItem>
            <SelectItem value="30">30 kunda tugaydigan</SelectItem>
          </SelectContent>
        </Select>
        <Select v-model="f.status">
          <SelectTrigger class="w-44"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem :value="ALL">Barcha holat</SelectItem>
            <SelectItem value="ACTIVE">Faol</SelectItem>
            <SelectItem value="CANCELLED">Bekor qilingan</SelectItem>
          </SelectContent>
        </Select>
        <Select v-model="f.planId">
          <SelectTrigger class="w-44"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem :value="ALL">Barcha tarif</SelectItem>
            <SelectItem v-for="p in plans" :key="p.id" :value="String(p.id)">{{ p.name }}</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <Table>
        <TableHeader>
          <TableRow><TableHead>Fermer</TableHead><TableHead>Tarif</TableHead><TableHead>Davr</TableHead><TableHead class="text-right">Summa</TableHead>
            <TableHead>Usul</TableHead><TableHead>Toʻlangan</TableHead><TableHead>Holat</TableHead></TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="s in data?.content ?? []" :key="s.id" class="cursor-pointer" @click="router.push(`/farmers/${s.farmerId}`)">
            <TableCell class="font-medium">#{{ s.farmerId }}</TableCell>
            <TableCell>{{ s.planName }}</TableCell>
            <TableCell class="tabular-nums">{{ fmtDate(s.validFrom) }} – {{ fmtDate(s.validUntil) }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ fmtMoney(s.amount, s.currency) }}</TableCell>
            <TableCell>{{ PAYMENT[s.paymentMethod] }}</TableCell>
            <TableCell class="text-muted-foreground">{{ fmtDateTime(s.paidAt) }}</TableCell>
            <TableCell>
              <StatusBadge v-if="s.status === 'CANCELLED'" tone="red" label="Bekor qilingan" />
              <StatusBadge v-else-if="s.activeToday" tone="green" label="Amalda" />
              <StatusBadge v-else tone="gray" :label="new Date(s.validFrom) > new Date() ? 'Kelajakda' : 'Tugagan'" />
            </TableCell>
          </TableRow>
          <TableEmpty v-if="data && !data.content.length" :colspan="7">Toʻlov topilmadi</TableEmpty>
        </TableBody>
      </Table>
      <Paginator v-if="data" :page="data.page" :total-pages="data.totalPages" :total-elements="data.totalElements" @change="page = $event" />
    </CardContent>
  </Card>
</template>
