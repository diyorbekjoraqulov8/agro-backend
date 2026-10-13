<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { refDebounced } from '@vueuse/core'
import { CreditCard, ExternalLink, History, Map as MapIcon, Search, Send } from '@lucide/vue'
import IconAction from '@/components/app/IconAction.vue'
import PageHeader from '@/components/app/PageHeader.vue'
import PaymentDialog from '@/components/billing/PaymentDialog.vue'
import Paginator from '@/components/app/Paginator.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { leadTone } from '@/components/app/badges'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { farmersApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { FarmerListItem, Page } from '@/api/types'
import { fmtDate } from '@/lib/format'
import { CROP, LEAD, P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'
import { toast } from 'vue-sonner'

const router = useRouter()
const auth = useAuth()
const payFor = ref<FarmerListItem | null>(null)          // "To'lov qildi" oynasi — qaysi fermer uchun
const payOpen = computed({ get: () => !!payFor.value, set: (v) => { if (!v) payFor.value = null } })
const name = (r: FarmerListItem) => r.fullName ?? (r.telegramUsername ? '@' + r.telegramUsername : `#${r.id}`)
const ALL = 'ALL'                       // reka Select bo'sh qiymatni qabul qilmaydi
const q = ref('')
const qDebounced = refDebounced(q, 350)
const f = reactive({ leadStatus: ALL, crop: ALL, country: '', source: '' })
const page = ref(0)
const data = ref<Page<FarmerListItem> | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    data.value = await farmersApi.search({
      q: qDebounced.value || undefined,
      leadStatus: f.leadStatus === ALL ? undefined : f.leadStatus,
      crop: f.crop === ALL ? undefined : f.crop,
      country: f.country || undefined,
      source: f.source || undefined,
      page: page.value, size: 25, sort: 'createdDate,desc',
    })
  } catch (e) {
    toast.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

watch([qDebounced, () => ({ ...f })], () => { page.value = 0; load() })
watch(page, load)
onMounted(load)
</script>

<template>
  <PageHeader title="Fermerlar" description="Botdan roʻyxatdan oʻtgan fermerlar va lead’lar" />
  <Card>
    <CardContent class="space-y-3">
      <div class="flex flex-wrap gap-2">
        <div class="relative min-w-56 flex-1">
          <Search class="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
          <Input v-model="q" placeholder="Ism, username yoki telefon" class="pl-8" />
        </div>
        <Select v-model="f.leadStatus">
          <SelectTrigger class="w-40"><SelectValue placeholder="Lead holati" /></SelectTrigger>
          <SelectContent>
            <SelectItem :value="ALL">Barcha lead</SelectItem>
            <SelectItem v-for="(l, k) in LEAD" :key="k" :value="k">{{ l }}</SelectItem>
          </SelectContent>
        </Select>
        <Select v-model="f.crop">
          <SelectTrigger class="w-40"><SelectValue placeholder="Ekin" /></SelectTrigger>
          <SelectContent>
            <SelectItem :value="ALL">Barcha ekin</SelectItem>
            <SelectItem v-for="(l, k) in CROP" :key="k" :value="k">{{ l }}</SelectItem>
          </SelectContent>
        </Select>
        <Input v-model.lazy="f.country" placeholder="Mamlakat (UZ)" maxlength="2" class="w-36 uppercase placeholder:normal-case" />
        <Input v-model.lazy="f.source" placeholder="Manba" class="w-36" />
      </div>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Fermer</TableHead><TableHead>Telefon</TableHead><TableHead>Mamlakat</TableHead>
            <TableHead>Ekin</TableHead><TableHead>Manba</TableHead><TableHead>Lead</TableHead><TableHead>Qoʻshilgan</TableHead>
            <TableHead class="bg-card sticky right-0 w-px" />
          </TableRow>
        </TableHeader>
        <TableBody :class="{ 'opacity-60': loading }">
          <TableRow v-for="r in data?.content ?? []" :key="r.id" class="cursor-pointer" @click="router.push(`/farmers/${r.id}`)">
            <TableCell>
              <div class="font-medium">{{ r.fullName ?? '—' }}</div>
              <div class="text-muted-foreground text-xs">{{ r.telegramUsername ? '@' + r.telegramUsername : `#${r.id}` }}
                <span v-if="r.blockedBot" class="text-destructive"> · botni bloklagan</span></div>
            </TableCell>
            <TableCell class="tabular-nums">{{ r.phone ?? '—' }}</TableCell>
            <TableCell>{{ r.countryCode ?? '—' }}</TableCell>
            <TableCell>{{ r.mainCrop ? CROP[r.mainCrop] : '—' }}</TableCell>
            <TableCell>{{ r.source ?? '—' }}</TableCell>
            <TableCell><StatusBadge :tone="leadTone[r.leadStatus]" :label="LEAD[r.leadStatus]" /></TableCell>
            <TableCell class="text-muted-foreground">{{ fmtDate(r.createdDate) }}</TableCell>
            <TableCell class="bg-card sticky right-0">
              <div class="flex justify-end gap-0.5">
                <IconAction v-if="auth.can(P.NOTIFICATION_SEND)" :icon="Send" :disabled="r.blockedBot"
                            :label="r.blockedBot ? 'Botni bloklagan — xabar yetmaydi' : 'Xabar yozish'"
                            :to="`/notifications?tab=compose&farmerId=${r.id}`" />
                <IconAction v-if="auth.can(P.BILLING_MANAGE)" :icon="CreditCard" label="Toʻlov qildi" @click="payFor = r" />
                <IconAction v-if="auth.can(P.NOTIFICATION_READ)" :icon="History" label="Xabarlar jurnali"
                            :to="`/notifications?tab=log&farmerId=${r.id}`" />
                <IconAction v-if="auth.can(P.FIELD_READ)" :icon="MapIcon" label="Dalalari" :to="`/fields?farmerId=${r.id}`" />
                <IconAction v-if="r.telegramUsername" :icon="ExternalLink" label="Telegramda ochish"
                            :href="`https://t.me/${r.telegramUsername}`" />
              </div>
            </TableCell>
          </TableRow>
          <TableEmpty v-if="data && !data.content.length" :colspan="8">Fermer topilmadi</TableEmpty>
        </TableBody>
      </Table>
      <Paginator v-if="data" :page="data.page" :total-pages="data.totalPages" :total-elements="data.totalElements" @change="page = $event" />
    </CardContent>
  </Card>
  <PaymentDialog v-model:open="payOpen" :farmer-id="payFor?.id ?? null" :farmer-name="payFor ? name(payFor) : null" />
</template>
