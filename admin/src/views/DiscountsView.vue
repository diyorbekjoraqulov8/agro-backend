<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Loader2, Plus } from '@lucide/vue'
import { toast } from 'vue-sonner'
import PageHeader from '@/components/app/PageHeader.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { billingApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Discount, DiscountAudience, DiscountKind, DiscountRequest, DiscountValueType, Plan } from '@/api/types'
import { fmtDate, fmtMoney } from '@/lib/format'
import { DISCOUNT_AUDIENCE, DISCOUNT_KIND, P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

/**
 * Chegirmalar: fermerga mos kelganlarning ENG foydalisi qo'llanadi (qo'shilmaydi) — botdagi tariflar ro'yxati,
 * tarif so'rovi, support guruhidagi "💳 Tarif berish" va "To'lov qildi" oynasida. O'chirish yo'q — "Faol" o'chiriladi.
 */
const auth = useAuth()
const canEdit = computed(() => auth.can(P.BILLING_MANAGE))
const items = ref<Discount[] | null>(null)
const plans = ref<Plan[]>([])
const open = ref(false)
const saving = ref(false)
const editing = ref<Discount | null>(null)
const AUDIENCES: DiscountAudience[] = ['FREE', 'TRIAL', 'PAID', 'EXPIRED']
const pricedPlans = computed(() => plans.value.filter((p) => !p.systemPlan && !p.contactSales))
// Mahalliy sana (toISOString — UTC: Toshkentda 00:00–05:00 "bugun" kechagi bo'lib qolardi)
const now = new Date()
const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
const form = reactive({
  name: '', kind: 'PROMO' as DiscountKind, valueType: 'PERCENT' as DiscountValueType, value: '20',
  audiences: ['FREE', 'TRIAL', 'EXPIRED'] as DiscountAudience[], planIds: [] as number[], startsOn: today, endsOn: '', active: true,
})

async function load() {
  try {
    items.value = await billingApi.discounts()
    if (!plans.value.length) plans.value = await billingApi.plans()
  } catch (e) {
    toast.error(errorMessage(e))
  }
}
onMounted(load)

function edit(d: Discount | null) {
  editing.value = d
  Object.assign(form, d ? {
    name: d.name, kind: d.kind, valueType: d.valueType, value: String(d.value), audiences: [...d.audiences],
    planIds: [...d.planIds], startsOn: d.startsOn, endsOn: d.endsOn ?? '', active: d.active,
  } : { name: '', kind: 'PROMO', valueType: 'PERCENT', value: '20', audiences: ['FREE', 'TRIAL', 'EXPIRED'], planIds: [], startsOn: today, endsOn: '', active: true })
  open.value = true
}

function toggle<T>(list: T[], v: T) {
  const i = list.indexOf(v)
  if (i >= 0) list.splice(i, 1)
  else list.push(v)
}

async function save() {
  saving.value = true
  const body: DiscountRequest = {
    name: form.name, kind: form.kind, valueType: form.valueType, value: Number(form.value), audiences: form.audiences,
    planIds: form.planIds, startsOn: form.startsOn, endsOn: form.endsOn || null, active: form.active,
  }
  try {
    editing.value ? await billingApi.updateDiscount(editing.value.id, body) : await billingApi.createDiscount(body)
    toast.success('Chegirma saqlandi — botda darhol koʻrinadi')
    open.value = false
    load()
  } catch (e) {
    toast.error(errorMessage(e))
  } finally {
    saving.value = false
  }
}

function valueText(d: Discount) {
  return d.valueType === 'PERCENT' ? `−${d.value}%` : `−${fmtMoney(d.value, 'UZS')}`
}
function status(d: Discount): { tone: 'green' | 'amber' | 'gray'; label: string } {
  if (!d.active) return { tone: 'gray', label: 'Oʻchiq' }
  if (d.activeToday) return { tone: 'green', label: 'Amalda' }
  return d.startsOn > today ? { tone: 'amber', label: 'Rejalashtirilgan' } : { tone: 'gray', label: 'Tugagan' }
}
function audienceNames(d: Discount) {
  return d.audiences.map((x) => DISCOUNT_AUDIENCE[x]).join(', ')
}
function planNames(ids: number[]) {
  if (!ids.length) return 'Barcha narxli'
  return ids.map((id) => plans.value.find((p) => p.id === id)?.name ?? `#${id}`).join(', ')
}
</script>

<template>
  <PageHeader title="Chegirmalar" description="Fermerga mos kelganlarning eng foydalisi qoʻllanadi (qoʻshilmaydi). Oʻzgarish botda darhol koʻrinadi">
    <Button v-if="canEdit" @click="edit(null)"><Plus /> Yangi chegirma</Button>
  </PageHeader>
  <Card>
    <CardContent class="p-0">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="sm:min-w-56">Chegirma</TableHead><TableHead>Qiymat</TableHead><TableHead>Kimlarga</TableHead>
            <TableHead class="hidden lg:table-cell">Tariflar</TableHead><TableHead>Muddat</TableHead>
            <TableHead class="text-right">Ishlatilgan</TableHead><TableHead>Holat</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="d in items ?? []" :key="d.id" :class="canEdit ? 'cursor-pointer' : ''" @click="canEdit && edit(d)">
            <TableCell>
              <div class="font-medium">{{ d.name }}</div>
              <div class="text-muted-foreground text-xs">{{ DISCOUNT_KIND[d.kind] }}</div>
            </TableCell>
            <TableCell class="tabular-nums font-medium">{{ valueText(d) }}</TableCell>
            <TableCell class="text-xs whitespace-normal">{{ audienceNames(d) }}</TableCell>
            <TableCell class="hidden text-xs lg:table-cell">{{ planNames(d.planIds) }}</TableCell>
            <TableCell class="text-xs whitespace-nowrap">{{ fmtDate(d.startsOn) }} — {{ d.endsOn ? fmtDate(d.endsOn) : 'muddatsiz' }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ d.usedCount }}</TableCell>
            <TableCell><StatusBadge :tone="status(d).tone" :label="status(d).label" /></TableCell>
          </TableRow>
          <TableRow v-if="items && !items.length">
            <TableCell colspan="7" class="text-muted-foreground py-8 text-center text-sm">Hali chegirma yoʻq</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </CardContent>
  </Card>

  <Dialog v-model:open="open">
    <DialogContent class="sm:max-w-lg">
      <DialogHeader>
        <DialogTitle>{{ editing ? 'Chegirma' : 'Yangi chegirma' }}</DialogTitle>
        <DialogDescription>Narx kelishiladigan tarif (Agro Biznes) va bepul tarifga qoʻllanmaydi</DialogDescription>
      </DialogHeader>
      <form class="grid grid-cols-1 gap-3 sm:grid-cols-2" @submit.prevent="save">
        <div class="space-y-1.5 sm:col-span-2"><Label>Nomi</Label><Input v-model="form.name" required maxlength="100" placeholder="Navroʻz aksiyasi" /></div>
        <div class="space-y-1.5">
          <Label>Turi</Label>
          <Select v-model="form.kind">
            <SelectTrigger class="w-full"><SelectValue /></SelectTrigger>
            <SelectContent><SelectItem v-for="(l, k) in DISCOUNT_KIND" :key="k" :value="k">{{ l }}</SelectItem></SelectContent>
          </Select>
        </div>
        <div class="space-y-1.5">
          <Label>Qiymat</Label>
          <div class="flex gap-2">
            <Input v-model="form.value" type="number" min="0.01" :max="form.valueType === 'PERCENT' ? 90 : undefined" step="0.01" required class="min-w-0" />
            <Select v-model="form.valueType">
              <SelectTrigger class="w-24"><SelectValue /></SelectTrigger>
              <SelectContent><SelectItem value="PERCENT">%</SelectItem><SelectItem value="FIXED">soʻm</SelectItem></SelectContent>
            </Select>
          </div>
        </div>
        <p v-if="form.kind === 'LOYALTY'" class="text-muted-foreground text-xs sm:col-span-2">Uzaytirish — odatda «Faol obunachi» va «Obunasi tugagan» uchun (tur faqat belgi, kimlarga — pastda)</p>
        <p v-if="form.kind === 'FIRST_PAYMENT'" class="text-muted-foreground text-xs sm:col-span-2">Birinchi toʻlov — faqat hali hech toʻlamagan fermerlarga (sinov va taklif mukofoti hisoblanmaydi)</p>
        <div class="space-y-1.5 sm:col-span-2">
          <Label>Kimlarga (bir nechtasi)</Label>
          <div class="flex flex-wrap gap-2">
            <Button v-for="a in AUDIENCES" :key="a" type="button" size="sm" :variant="form.audiences.includes(a) ? 'default' : 'outline'"
                    @click="toggle(form.audiences, a)">{{ DISCOUNT_AUDIENCE[a] }}</Button>
          </div>
        </div>
        <div class="space-y-1.5 sm:col-span-2">
          <Label>Qaysi tariflarga (tanlanmasa — barcha narxli)</Label>
          <div class="flex flex-wrap gap-2">
            <Button v-for="p in pricedPlans" :key="p.id" type="button" size="sm" :variant="form.planIds.includes(p.id) ? 'default' : 'outline'"
                    @click="toggle(form.planIds, p.id)">{{ p.name }}</Button>
          </div>
        </div>
        <div class="space-y-1.5"><Label>Boshlanish</Label><Input v-model="form.startsOn" type="date" required /></div>
        <div class="space-y-1.5"><Label>Tugash (boʻsh — muddatsiz)</Label><Input v-model="form.endsOn" type="date" :min="form.startsOn" /></div>
        <div class="flex items-center gap-2 sm:col-span-2"><Switch v-model="form.active" /><Label>Faol</Label></div>
        <DialogFooter class="sm:col-span-2">
          <Button type="button" variant="outline" @click="open = false">Bekor</Button>
          <Button type="submit" :disabled="saving || !form.audiences.length"><Loader2 v-if="saving" class="animate-spin" /> Saqlash</Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
