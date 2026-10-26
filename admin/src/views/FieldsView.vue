<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus } from '@lucide/vue'
import { toast } from 'vue-sonner'
import PageHeader from '@/components/app/PageHeader.vue'
import Paginator from '@/components/app/Paginator.vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { fieldsApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { FieldSummary, Page } from '@/api/types'
import { fmtDate, fmtNum } from '@/lib/format'
import { CROP, P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

const router = useRouter()
const auth = useAuth()
const route = useRoute()
const ALL = 'ALL'
// ?farmerId=12 — fermerlar ro'yxatidagi "Dalalari" tugmasidan
// owner: FARMER — fermerlarniki, LAB — admin chizgan test dalalari
const f = reactive({ crop: ALL, country: '', owner: ALL, farmerId: typeof route.query.farmerId === 'string' ? route.query.farmerId : '' })
const page = ref(0)
const data = ref<Page<FieldSummary> | null>(null)

async function load() {
  try {
    data.value = await fieldsApi.search({
      crop: f.crop === ALL ? undefined : f.crop, country: f.country || undefined,
      farmerId: f.farmerId || undefined, lab: f.owner === ALL ? undefined : f.owner === 'LAB', page: page.value, size: 25, sort: 'createdDate,desc',
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
  <PageHeader title="Dalalar" description="Fermerlar Mini App’da chizgan dalalar va admin chizgan Lab dalalari">
    <Button v-if="auth.can(P.FIELD_WRITE)" @click="router.push('/fields/new')"><Plus /> Lab dala</Button>
  </PageHeader>
  <Card>
    <CardContent class="space-y-3">
      <div class="flex flex-wrap gap-2">
        <Select v-model="f.crop">
          <SelectTrigger class="w-44"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem :value="ALL">Barcha ekin</SelectItem>
            <SelectItem v-for="(l, k) in CROP" :key="k" :value="k">{{ l }}</SelectItem>
          </SelectContent>
        </Select>
        <Select v-model="f.owner">
          <SelectTrigger class="w-40"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem :value="ALL">Barcha dalalar</SelectItem>
            <SelectItem value="FARMER">Fermerlarniki</SelectItem>
            <SelectItem value="LAB">Lab dalalari</SelectItem>
          </SelectContent>
        </Select>
        <Input v-model.lazy="f.country" placeholder="Mamlakat (UZ)" maxlength="2" class="w-36 uppercase placeholder:normal-case" />
        <Input v-model.lazy="f.farmerId" placeholder="Fermer ID" type="number" class="w-32" />
      </div>
      <Table>
        <TableHeader>
          <TableRow><TableHead>Dala</TableHead><TableHead>Ekin</TableHead><TableHead class="text-right">Maydon</TableHead>
            <TableHead>Markaz</TableHead><TableHead>Fermer</TableHead><TableHead>Qoʻshilgan</TableHead></TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="r in data?.content ?? []" :key="r.id" class="cursor-pointer" @click="router.push(`/fields/${r.id}`)">
            <TableCell class="font-medium">{{ r.name }} <span class="text-muted-foreground font-normal">#{{ r.id }}</span></TableCell>
            <TableCell>{{ CROP[r.crop] }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ r.areaHa != null ? `${fmtNum(r.areaHa, 2)} ga` : '—' }}</TableCell>
            <TableCell class="text-muted-foreground tabular-nums">{{ r.centroidLat != null ? `${r.centroidLat}, ${r.centroidLon}` : '—' }}</TableCell>
            <TableCell><RouterLink :to="`/farmers/${r.farmerId}`" class="hover:underline" @click.stop>#{{ r.farmerId }}</RouterLink></TableCell>
            <TableCell class="text-muted-foreground">{{ fmtDate(r.createdDate) }}</TableCell>
          </TableRow>
          <TableEmpty v-if="data && !data.content.length" :colspan="6">Dala topilmadi</TableEmpty>
        </TableBody>
      </Table>
      <Paginator v-if="data" :page="data.page" :total-pages="data.totalPages" :total-elements="data.totalElements" @change="page = $event" />
    </CardContent>
  </Card>
</template>
