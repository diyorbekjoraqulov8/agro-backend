<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Loader2 } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { billingApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { PaymentMethod, Plan, PlanPrice, SeasonOffer } from '@/api/types'
import { fmtDate, fmtMoney } from '@/lib/format'
import { PAYMENT } from '@/lib/labels'

/** "To'lov qildi" — fermer sahifasi va fermerlar ro'yxatidan. Tariflar ochilganda yuklanadi */
const props = defineProps<{ farmerId: number | null; farmerName?: string | null }>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ saved: [] }>()

const plans = ref<Plan[]>([])
const paying = ref(false)
const pay = reactive({ planId: '', paymentMethod: 'CASH' as PaymentMethod, amount: '', validFrom: '', validUntil: '', note: '' })
const paidPlans = computed(() => plans.value.filter((p) => !p.systemPlan && p.active))
const selectedPlan = computed(() => plans.value.find((p) => String(p.id) === pay.planId))
// Shu fermerga narx (eng foydali chegirma bilan) — summa bo'sh qolsa server shuni yozadi
const prices = ref<PlanPrice[]>([])
const selectedPrice = computed(() => prices.value.find((x) => String(x.planId) === pay.planId))
const season = ref<SeasonOffer | null>(null)
// Mavsumiy obuna — bir bosishda: tarif, summa va mavsum oxirigacha muddat
function applySeason() {
  const o = season.value
  if (!o) return
  Object.assign(pay, { planId: String(o.planId), amount: String(o.finalPrice), validFrom: o.from, validUntil: o.until,
    note: pay.note || `Mavsumiy obuna (−${o.discountPct}%)` })
}

watch(open, async (v) => {
  if (!v) return
  try {
    if (!plans.value.length) plans.value = await billingApi.plans()
    prices.value = props.farmerId != null ? await billingApi.prices(props.farmerId) : []
    season.value = props.farmerId != null ? (await billingApi.seasonOffer(props.farmerId)) ?? null : null
  } catch (e) { toast.error(errorMessage(e)) }
  const first = paidPlans.value[0]
  Object.assign(pay, { planId: first ? String(first.id) : '', paymentMethod: 'CASH', amount: '', validFrom: '', validUntil: '', note: '' })
})

async function submit() {
  if (!pay.planId || props.farmerId == null) return
  paying.value = true
  try {
    const s = await billingApi.recordPayment(props.farmerId, {
      planId: Number(pay.planId),
      paymentMethod: pay.paymentMethod,
      amount: pay.amount === '' ? null : Number(pay.amount),
      validFrom: pay.validFrom || null,
      validUntil: pay.validUntil || null,
      note: pay.note || null,
      // Summa qo'lda yozilsa ham — ko'rsatilgan chegirma obunaga yoziladi (statistika)
      discountId: pay.amount !== '' && selectedPrice.value?.discountId && Number(pay.amount) === selectedPrice.value.finalPrice
        ? selectedPrice.value.discountId : null,
    })
    toast.success(`Toʻlov qayd etildi: ${fmtDate(s.validFrom)} – ${fmtDate(s.validUntil)}`)
    open.value = false
    emit('saved')
  } catch (e) {
    toast.error(errorMessage(e))
  } finally {
    paying.value = false
  }
}
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>Toʻlovni qayd etish<template v-if="farmerName"> — {{ farmerName }}</template></DialogTitle>
        <DialogDescription>Boʻsh maydonlar tarifdan olinadi. Muddat joriy obuna tugagan kundan davom etadi.</DialogDescription>
      </DialogHeader>
      <form class="space-y-3" @submit.prevent="submit">
        <div class="space-y-1.5">
          <Label>Tarif</Label>
          <Select v-model="pay.planId">
            <SelectTrigger class="w-full"><SelectValue placeholder="Tanlang" /></SelectTrigger>
            <SelectContent>
              <SelectItem v-for="p in paidPlans" :key="p.id" :value="String(p.id)">
                {{ p.name }} · {{ p.contactSales ? 'narx kelishiladi' : fmtMoney(p.price, p.currency) }} / {{ p.durationDays }} kun
              </SelectItem>
            </SelectContent>
          </Select>
          <p v-if="plans.length && !paidPlans.length" class="text-destructive text-xs">Faol pullik tarif yoʻq — Tariflar sahifasida yoqing</p>
          <button v-if="season" type="button" class="w-full rounded-md border border-dashed px-2 py-1.5 text-left text-xs hover:bg-muted"
                  @click="applySeason">
            🌱 Mavsumiy: {{ season.planName }} {{ fmtDate(season.from) }} — {{ fmtDate(season.until) }} ({{ season.days }} kun) ·
            <s>{{ fmtMoney(season.basePrice, season.currency) }}</s> <b>{{ fmtMoney(season.finalPrice, season.currency) }}</b> (−{{ season.discountPct }}%) — toʻldirish
          </button>
          <p v-if="selectedPrice?.discountId" class="rounded-md bg-emerald-50 px-2 py-1 text-xs text-emerald-800 dark:bg-emerald-500/10 dark:text-emerald-300">
            🏷 {{ selectedPrice.discountName }} ({{ selectedPrice.discountLabel }}):
            <s>{{ fmtMoney(selectedPrice.basePrice, selectedPrice.currency) }}</s> → <b>{{ fmtMoney(selectedPrice.finalPrice, selectedPrice.currency) }}</b>
          </p>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div class="space-y-1.5">
            <Label>Summa{{ selectedPlan?.contactSales ? ' (kelishilgan)' : '' }}</Label>
            <Input v-model="pay.amount" type="number" min="0" step="0.01" :required="selectedPlan?.contactSales"
                   :placeholder="selectedPlan && !selectedPlan.contactSales ? String(selectedPrice?.finalPrice ?? selectedPlan.price) : ''" />
          </div>
          <div class="space-y-1.5">
            <Label>Usul</Label>
            <Select v-model="pay.paymentMethod">
              <SelectTrigger class="w-full"><SelectValue /></SelectTrigger>
              <SelectContent><template v-for="(l, k) in PAYMENT" :key="k"><SelectItem v-if="k !== 'REFERRAL' && k !== 'TRIAL'" :value="k">{{ l }}</SelectItem></template></SelectContent>
            </Select>
          </div>
          <div class="space-y-1.5"><Label>Boshlanish (ixtiyoriy)</Label><Input v-model="pay.validFrom" type="date" /></div>
          <div class="space-y-1.5"><Label>Tugash (ixtiyoriy)</Label><Input v-model="pay.validUntil" type="date" /></div>
        </div>
        <div class="space-y-1.5"><Label>Izoh</Label><Textarea v-model="pay.note" rows="2" maxlength="1000" placeholder="Chek raqami, kim toʻladi..." /></div>
        <DialogFooter>
          <Button type="button" variant="outline" @click="open = false">Bekor</Button>
          <Button type="submit" :disabled="paying || !pay.planId"><Loader2 v-if="paying" class="animate-spin" /> Saqlash</Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
