<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import PageHeader from '@/components/app/PageHeader.vue'
import Paginator from '@/components/app/Paginator.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { auditApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { AuditLog, Page } from '@/api/types'
import { fmtDateTime } from '@/lib/format'

// AuditEvent.java bilan bir xil
const EVENTS = [
  'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'ACCOUNT_LOCKED', 'LOGOUT', 'SESSION_REVOKED', 'SESSION_REVOKED_ALL', 'REFRESH_TOKEN_REUSE',
  'ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED', 'USER_REGISTERED', 'USER_DELETED', 'ROLE_ASSIGNED', 'ROLE_REVOKED',
  'FARMER_REGISTERED', 'FIELD_CREATED', 'ANALYSIS_REQUESTED', 'ANALYSIS_COMPLETED', 'ANALYSIS_FAILED', 'ANALYSIS_RETRIED',
  'LEAD_REQUESTED', 'LEAD_STATUS_CHANGED', 'PLAN_CREATED', 'PLAN_UPDATED', 'PAYMENT_RECORDED', 'SUBSCRIPTION_CANCELLED',
  'FEEDBACK_CLAIMED', 'FEEDBACK_CLOSED', 'SETTING_CHANGED', 'REFERRAL_REWARDED', 'TRIAL_GRANTED',
]
const ALL = 'ALL'
const f = reactive({ eventType: ALL, actorId: '' })
const page = ref(0)
const data = ref<Page<AuditLog> | null>(null)

async function load() {
  try {
    data.value = await auditApi.search({
      eventType: f.eventType === ALL ? undefined : f.eventType, actorId: f.actorId || undefined,
      page: page.value, size: 50, sort: 'createdAt,desc',
    })
  } catch (e) {
    toast.error(errorMessage(e))
  }
}
watch(() => ({ ...f }), () => { page.value = 0; load() })
watch(page, load)
onMounted(load)
</script>

<template>
  <PageHeader title="Audit" description="Kim, qachon, nima qildi — oʻzgarmas jurnal" />
  <Card>
    <CardContent class="space-y-3">
      <div class="flex flex-wrap gap-2">
        <Select v-model="f.eventType">
          <SelectTrigger class="w-64"><SelectValue /></SelectTrigger>
          <SelectContent class="max-h-80">
            <SelectItem :value="ALL">Barcha hodisalar</SelectItem>
            <SelectItem v-for="e in EVENTS" :key="e" :value="e">{{ e }}</SelectItem>
          </SelectContent>
        </Select>
        <Input v-model.lazy="f.actorId" type="number" placeholder="Xodim ID" class="w-32" />
      </div>
      <Table>
        <TableHeader>
          <TableRow><TableHead>Vaqt</TableHead><TableHead>Hodisa</TableHead><TableHead>Kim</TableHead><TableHead>Obyekt</TableHead><TableHead>Tafsilot</TableHead><TableHead>IP</TableHead></TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="a in data?.content ?? []" :key="a.id">
            <TableCell class="text-muted-foreground whitespace-nowrap">{{ fmtDateTime(a.createdAt) }}</TableCell>
            <TableCell><StatusBadge :tone="a.outcome === 'SUCCESS' ? 'gray' : 'red'" :label="a.eventType" /></TableCell>
            <TableCell>{{ a.actorEmail ?? 'tizim / bot' }}</TableCell>
            <TableCell class="whitespace-nowrap">{{ a.targetType ? `${a.targetType} #${a.targetId}` : '—' }}</TableCell>
            <TableCell class="text-muted-foreground max-w-96 truncate font-mono text-xs" :title="JSON.stringify(a.details)">
              {{ a.details ? JSON.stringify(a.details) : '' }}</TableCell>
            <TableCell class="text-muted-foreground">{{ a.ip ?? '' }}</TableCell>
          </TableRow>
          <TableEmpty v-if="data && !data.content.length" :colspan="6">Yozuv yoʻq</TableEmpty>
        </TableBody>
      </Table>
      <Paginator v-if="data" :page="data.page" :total-pages="data.totalPages" :total-elements="data.totalElements" @change="page = $event" />
    </CardContent>
  </Card>
</template>
