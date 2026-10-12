<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import PageHeader from '@/components/app/PageHeader.vue'
import Paginator from '@/components/app/Paginator.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { jobTone } from '@/components/app/badges'
import { Card, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { analysesApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { AnalysisListItem, Page } from '@/api/types'
import { duration, fmtDate, fmtDateTime } from '@/lib/format'
import { JOB_KIND, JOB_STATUS } from '@/lib/labels'

const router = useRouter()
const ALL = 'ALL'
const f = reactive({ status: ALL, kind: ALL })
const page = ref(0)
const data = ref<Page<AnalysisListItem> | null>(null)

async function load() {
  try {
    data.value = await analysesApi.search({
      status: f.status === ALL ? undefined : f.status, kind: f.kind === ALL ? undefined : f.kind,
      page: page.value, size: 25, sort: 'createdDate,desc',
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
  <PageHeader title="Tahlillar" description="Sunʼiy yoʻldosh tahlili navbati va natijalari" />
  <Card>
    <CardContent class="space-y-3">
      <div class="flex flex-wrap gap-2">
        <Select v-model="f.status">
          <SelectTrigger class="w-44"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem :value="ALL">Barcha holat</SelectItem>
            <SelectItem v-for="(l, k) in JOB_STATUS" :key="k" :value="k">{{ l }}</SelectItem>
          </SelectContent>
        </Select>
        <Select v-model="f.kind">
          <SelectTrigger class="w-40"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem :value="ALL">Barcha tur</SelectItem>
            <SelectItem v-for="(l, k) in JOB_KIND" :key="k" :value="k">{{ l }}</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <Table>
        <TableHeader>
          <TableRow><TableHead>#</TableHead><TableHead>Dala</TableHead><TableHead>Turi</TableHead><TableHead>Holat</TableHead>
            <TableHead>Urinish</TableHead><TableHead>Davomiylik</TableHead><TableHead>Davr</TableHead><TableHead>Yaratilgan</TableHead></TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="r in data?.content ?? []" :key="r.id" class="cursor-pointer" @click="router.push(`/analyses/${r.id}`)">
            <TableCell class="font-medium">#{{ r.id }}</TableCell>
            <TableCell><RouterLink :to="`/fields/${r.fieldId}`" class="hover:underline" @click.stop>#{{ r.fieldId }}</RouterLink></TableCell>
            <TableCell>{{ JOB_KIND[r.kind] }}</TableCell>
            <TableCell>
              <StatusBadge :tone="jobTone[r.status]" :label="JOB_STATUS[r.status]" />
              <div v-if="r.error" class="text-destructive mt-1 max-w-64 truncate text-xs" :title="r.error">{{ r.error }}</div>
            </TableCell>
            <TableCell class="tabular-nums">{{ r.attempts }}</TableCell>
            <TableCell class="tabular-nums">{{ duration(r.startedAt, r.finishedAt) }}</TableCell>
            <TableCell class="tabular-nums">{{ fmtDate(r.seasonStart) }} – {{ fmtDate(r.seasonEnd) }}</TableCell>
            <TableCell class="text-muted-foreground">{{ fmtDateTime(r.createdDate) }}</TableCell>
          </TableRow>
          <TableEmpty v-if="data && !data.content.length" :colspan="8">Tahlil topilmadi</TableEmpty>
        </TableBody>
      </Table>
      <Paginator v-if="data" :page="data.page" :total-pages="data.totalPages" :total-elements="data.totalElements" @change="page = $event" />
    </CardContent>
  </Card>
</template>
