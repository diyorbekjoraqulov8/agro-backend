<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { Search, Star, UserRoundCog } from '@lucide/vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import Paginator from '@/components/app/Paginator.vue'
import ChatPanel from '@/components/feedback/ChatPanel.vue'
import Stars from '@/components/feedback/Stars.vue'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { feedbackApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Feedback, OperatorStats, Page } from '@/api/types'
import { fmtDateTime, fmtNum } from '@/lib/format'
import { CLOSED_BY, avatarClass, initials, kindTone, who } from '@/lib/feedback'
import { FEEDBACK_KIND, P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

/** Yakunlangan suhbatlar. Operator — o'zinikini, feedback:manage — hammasini va operatorlar statistikasini ko'radi */
const auth = useAuth()
const manage = computed(() => auth.can(P.FEEDBACK_MANAGE))
const ALL = 'ALL'
const f = reactive({ kind: ALL, operator: ALL, rating: ALL, q: '' })
const page = ref(0)
const data = ref<Page<Feedback> | null>(null)
const operators = ref<OperatorStats[]>([])
const open = ref<number | null>(null)

async function load() {
  try {
    data.value = await feedbackApi.search({
      status: 'CLOSED',
      kind: f.kind === ALL ? undefined : f.kind,
      assigneeId: manage.value && f.operator !== ALL ? f.operator : undefined,
      mine: !manage.value || undefined,
      rating: f.rating === ALL ? undefined : f.rating,
      q: f.q.trim() || undefined,
      page: page.value, size: 25, sort: 'closedAt,desc',
    })
  } catch (e) {
    toast.error(errorMessage(e))
  }
}

let timer: ReturnType<typeof setTimeout> | undefined
watch(() => [f.kind, f.operator, f.rating], () => { page.value = 0; load() })
watch(() => f.q, () => { clearTimeout(timer); timer = setTimeout(() => { page.value = 0; load() }, 350) })
watch(page, load)
onMounted(async () => {
  load()
  if (manage.value) {
    try { operators.value = await feedbackApi.operators() } catch { /* statistika ixtiyoriy */ }
  }
})
</script>

<template>
  <div class="space-y-4">
    <!-- Operatorlar (admin) -->
    <div v-if="manage && operators.length" class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <button
        v-for="o in operators" :key="o.staffId" type="button"
        :class="['bg-background rounded-xl border p-4 text-left shadow-xs transition hover:shadow-md',
                 f.operator === String(o.staffId) ? 'ring-primary ring-2' : '']"
        @click="f.operator = f.operator === String(o.staffId) ? ALL : String(o.staffId)"
      >
        <div class="flex items-center gap-2">
          <div :class="['flex size-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold', avatarClass(o.staffId)]">
            {{ initials(o.email) }}
          </div>
          <p class="min-w-0 truncate text-sm font-medium">{{ o.email }}</p>
        </div>
        <div class="mt-3 flex items-end justify-between">
          <div>
            <p class="flex items-center gap-1 text-2xl font-semibold tabular-nums">
              {{ o.avgRating ? fmtNum(o.avgRating, 1) : '—' }}<Star v-if="o.avgRating" class="size-5 fill-amber-400 text-amber-400" />
            </p>
            <p class="text-muted-foreground text-xs">{{ o.rated }} ta baho</p>
          </div>
          <div class="text-muted-foreground text-right text-xs leading-5">
            <p><span class="text-foreground font-medium tabular-nums">{{ o.closed }}</span> yakunlangan</p>
            <p><span class="text-foreground font-medium tabular-nums">{{ o.open }}</span> ochiq</p>
          </div>
        </div>
      </button>
    </div>

    <div class="flex flex-wrap gap-2">
      <div class="relative w-full sm:w-64">
        <Search class="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
        <Input v-model="f.q" placeholder="Fermer yoki xabar…" class="pl-8" />
      </div>
      <Select v-if="manage" v-model="f.operator">
        <SelectTrigger class="w-56"><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectItem :value="ALL">Barcha operatorlar</SelectItem>
          <SelectItem v-for="o in operators" :key="o.staffId" :value="String(o.staffId)">{{ o.email }}</SelectItem>
        </SelectContent>
      </Select>
      <Select v-model="f.kind">
        <SelectTrigger class="w-40"><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectItem :value="ALL">Barcha tur</SelectItem>
          <SelectItem v-for="(l, k) in FEEDBACK_KIND" :key="k" :value="k">{{ l }}</SelectItem>
        </SelectContent>
      </Select>
      <Select v-model="f.rating">
        <SelectTrigger class="w-44"><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectItem :value="ALL">Har qanday baho</SelectItem>
          <SelectItem v-for="n in [5, 4, 3, 2, 1]" :key="n" :value="String(n)">{{ '★'.repeat(n) }}</SelectItem>
        </SelectContent>
      </Select>
    </div>

    <div class="bg-background rounded-xl border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="w-16">№</TableHead><TableHead>Fermer</TableHead><TableHead>Tur</TableHead>
            <TableHead>Mavzu</TableHead><TableHead>Operator</TableHead><TableHead>Yakunladi</TableHead>
            <TableHead>Baho</TableHead><TableHead>Sana</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <template v-if="!data">
            <TableRow v-for="i in 5" :key="i"><TableCell :colspan="8"><Skeleton class="h-5 w-full" /></TableCell></TableRow>
          </template>
          <TableRow v-for="x in data?.content ?? []" :key="x.id" class="cursor-pointer" @click="open = x.id">
            <TableCell class="text-muted-foreground tabular-nums">{{ x.id }}</TableCell>
            <TableCell class="font-medium whitespace-nowrap">{{ who(x) }}</TableCell>
            <TableCell><StatusBadge :tone="kindTone[x.kind]" :label="FEEDBACK_KIND[x.kind]" /></TableCell>
            <TableCell class="max-w-xs"><span class="block truncate">{{ x.subject }}</span></TableCell>
            <TableCell class="text-muted-foreground whitespace-nowrap">
              <span v-if="x.assigneeEmail" class="inline-flex items-center gap-1"><UserRoundCog class="size-3.5" />{{ x.assigneeEmail }}</span>
              <span v-else>javobsiz</span>
            </TableCell>
            <TableCell class="text-muted-foreground">{{ CLOSED_BY[x.closedBy ?? ''] ?? '—' }}</TableCell>
            <TableCell><Stars :value="x.rating" /></TableCell>
            <TableCell class="text-muted-foreground whitespace-nowrap">{{ fmtDateTime(x.closedAt) }}</TableCell>
          </TableRow>
          <TableEmpty v-if="data && !data.content.length" :colspan="8">Yakunlangan suhbat yoʻq</TableEmpty>
        </TableBody>
      </Table>
    </div>
    <Paginator v-if="data" :page="data.page" :total-pages="data.totalPages" :total-elements="data.totalElements" @change="page = $event" />

    <Sheet :open="open !== null" @update:open="(v: boolean) => { if (!v) open = null }">
      <SheetContent :show-close-button="false" class="flex w-full flex-col gap-0 p-0 sm:max-w-xl">
        <SheetHeader class="sr-only"><SheetTitle>Suhbat</SheetTitle><SheetDescription>Yakunlangan suhbat tarixi</SheetDescription></SheetHeader>
        <ChatPanel v-if="open" :id="open" closable class="flex-1" @back="open = null" @gone="open = null" />
      </SheetContent>
    </Sheet>
  </div>
</template>
