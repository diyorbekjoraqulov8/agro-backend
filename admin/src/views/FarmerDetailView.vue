<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Ban, Loader2, Megaphone, Plus, Save, Send } from '@lucide/vue'
import { toast } from 'vue-sonner'
import PageHeader from '@/components/app/PageHeader.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { leadTone } from '@/components/app/badges'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { billingApi, farmersApi, referralsApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { FarmerDetail, FarmerReferral, LeadStatus, PaymentMethod, Plan, Subscription } from '@/api/types'
import { fmtDate, fmtDateTime, fmtMoney, fmtNum } from '@/lib/format'
import { CROP, IRRIGATION, LANG, LEAD, P, PAYMENT, WATER } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const id = Number(route.params.id)

const farmer = ref<FarmerDetail | null>(null)
const referral = ref<FarmerReferral | null>(null)
const subs = ref<Subscription[]>([])
const plans = ref<Plan[]>([])

const lead = reactive<{ status: LeadStatus; note: string }>({ status: 'NONE', note: '' })
const savingLead = ref(false)

const payOpen = ref(false)
const paying = ref(false)
const pay = reactive({ planId: '', paymentMethod: 'CASH' as PaymentMethod, amount: '', validFrom: '', validUntil: '', note: '' })
const paidPlans = computed(() => plans.value.filter((p) => !p.systemPlan && p.active))
const selectedPlan = computed(() => plans.value.find((p) => String(p.id) === pay.planId))

const cancelTarget = ref<Subscription | null>(null)
const cancelReason = ref('')

async function load() {
  try {
    farmer.value = await farmersApi.get(id)
    if (auth.can(P.REFERRAL_READ)) referralsApi.forFarmer(id).then((r) => (referral.value = r)).catch(() => {})
    lead.status = farmer.value.leadStatus
    lead.note = farmer.value.leadNote ?? ''
    if (auth.can(P.BILLING_READ)) {
      ;[subs.value, plans.value] = await Promise.all([billingApi.history(id), billingApi.plans()])
    }
  } catch (e) {
    toast.error(errorMessage(e))
  }
}

async function saveLead() {
  savingLead.value = true
  try {
    farmer.value = await farmersApi.updateLead(id, { leadStatus: lead.status, leadNote: lead.note || null })
    toast.success('Lead holati saqlandi')
  } catch (e) {
    toast.error(errorMessage(e))
  } finally {
    savingLead.value = false
  }
}

function openPay() {
  const first = paidPlans.value[0]
  Object.assign(pay, { planId: first ? String(first.id) : '', paymentMethod: 'CASH', amount: '', validFrom: '', validUntil: '', note: '' })
  payOpen.value = true
}

async function submitPay() {
  if (!pay.planId) return
  paying.value = true
  try {
    const s = await billingApi.recordPayment(id, {
      planId: Number(pay.planId),
      paymentMethod: pay.paymentMethod,
      amount: pay.amount === '' ? null : Number(pay.amount),
      validFrom: pay.validFrom || null,
      validUntil: pay.validUntil || null,
      note: pay.note || null,
    })
    toast.success(`Toʻlov qayd etildi: ${fmtDate(s.validFrom)} – ${fmtDate(s.validUntil)}`)
    payOpen.value = false
    await load()
  } catch (e) {
    toast.error(errorMessage(e))
  } finally {
    paying.value = false
  }
}

async function submitCancel() {
  if (!cancelTarget.value || !cancelReason.value.trim()) return
  try {
    await billingApi.cancel(cancelTarget.value.id, cancelReason.value.trim())
    toast.success('Obuna bekor qilindi')
    cancelTarget.value = null
    cancelReason.value = ''
    await load()
  } catch (e) {
    toast.error(errorMessage(e))
  }
}

onMounted(load)
</script>

<template>
  <Button variant="ghost" size="sm" class="mb-2 -ml-2" @click="router.back()"><ArrowLeft /> Orqaga</Button>

  <div v-if="!farmer" class="space-y-4"><Skeleton class="h-10 w-72" /><Skeleton class="h-64 w-full" /></div>

  <template v-else>
    <PageHeader :title="farmer.fullName ?? `Fermer #${farmer.id}`"
                :description="`${farmer.telegramUsername ? '@' + farmer.telegramUsername + ' · ' : ''}Telegram ID ${farmer.telegramUserId}`">
      <StatusBadge :tone="leadTone[farmer.leadStatus]" :label="LEAD[farmer.leadStatus]" />
      <StatusBadge v-if="farmer.blockedBot" tone="red" label="Botni bloklagan" />
      <Button v-if="auth.can(P.NOTIFICATION_READ)" variant="outline" size="sm" as-child>
        <RouterLink :to="`/notifications?tab=log&farmerId=${farmer.id}`"><Megaphone /> Xabarlar</RouterLink>
      </Button>
      <Button v-if="auth.can(P.NOTIFICATION_SEND) && !farmer.blockedBot" size="sm" as-child>
        <RouterLink :to="`/notifications?tab=compose&farmerId=${farmer.id}`"><Send /> Xabar yozish</RouterLink>
      </Button>
    </PageHeader>

    <div class="grid gap-4 lg:grid-cols-3">
      <Card class="lg:col-span-2">
        <CardHeader><CardTitle class="text-base">Maʼlumotlar</CardTitle></CardHeader>
        <CardContent>
          <dl class="grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-3">
            <div v-for="[k, v] in [
              ['Telefon', farmer.phone ?? '—'], ['Mamlakat', farmer.countryCode ?? '—'], ['Til', LANG[farmer.language] ?? farmer.language],
              ['Asosiy ekin', farmer.mainCrop ? CROP[farmer.mainCrop] : '—'], ['Sugʻorish', farmer.irrigationType ? IRRIGATION[farmer.irrigationType] : '—'],
              ['Suv manbai', farmer.waterSource ? WATER[farmer.waterSource] : '—'], ['Manba (reklama)', farmer.source ?? '—'],
              ['Qoʻshilgan', fmtDateTime(farmer.createdDate)], ['Yangilangan', fmtDateTime(farmer.updatedDate)],
            ]" :key="k">
              <dt class="text-muted-foreground">{{ k }}</dt><dd class="font-medium">{{ v }}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle class="text-base">Tarif</CardTitle>
          <CardDescription>{{ farmer.plan.paid ? `Amal qiladi: ${fmtDate(farmer.plan.validUntil)} gacha` : 'Toʻlov qilinmagan — bepul rejim' }}</CardDescription>
        </CardHeader>
        <CardContent class="space-y-3 text-sm">
          <div class="flex items-center gap-2">
            <span class="text-lg font-semibold">{{ farmer.plan.planName }}</span>
            <StatusBadge :tone="farmer.plan.paid ? 'green' : 'gray'" :label="farmer.plan.paid ? 'Pullik' : 'Bepul'" />
          </div>
          <div class="text-muted-foreground grid grid-cols-2 gap-1">
            <span>Maks. maydon</span><span class="text-foreground text-right">{{ fmtNum(farmer.plan.maxFieldHa) }} ga</span>
            <span>Dalalar</span><span class="text-foreground text-right">{{ farmer.fields.length }} / {{ farmer.plan.maxFields }}</span>
            <span>Tahlillar</span><span class="text-foreground text-right">{{ farmer.plan.usedAnalyses }} / {{ farmer.plan.maxAnalyses ?? '∞' }}</span>
          </div>
          <Button v-if="auth.can(P.BILLING_MANAGE)" class="w-full" @click="openPay"><Plus /> Toʻlov qildi</Button>
        </CardContent>
      </Card>

      <Card v-if="auth.can(P.FARMER_UPDATE)" class="lg:col-span-1">
        <CardHeader><CardTitle class="text-base">Lead</CardTitle></CardHeader>
        <CardContent class="space-y-3">
          <Select v-model="lead.status">
            <SelectTrigger class="w-full"><SelectValue /></SelectTrigger>
            <SelectContent><SelectItem v-for="(l, k) in LEAD" :key="k" :value="k">{{ l }}</SelectItem></SelectContent>
          </Select>
          <Textarea v-model="lead.note" placeholder="Izoh (qoʻngʻiroq natijasi, kelishuv...)" rows="3" maxlength="2000" />
          <Button variant="outline" class="w-full" :disabled="savingLead" @click="saveLead">
            <Loader2 v-if="savingLead" class="animate-spin" /><Save v-else /> Saqlash
          </Button>
        </CardContent>
      </Card>

      <Card :class="auth.can(P.FARMER_UPDATE) ? 'lg:col-span-2' : 'lg:col-span-3'">
        <CardHeader><CardTitle class="text-base">Dalalar</CardTitle></CardHeader>
        <CardContent>
          <Table>
            <TableHeader><TableRow><TableHead>Nomi</TableHead><TableHead>Ekin</TableHead><TableHead class="text-right">Maydon</TableHead><TableHead>Qoʻshilgan</TableHead></TableRow></TableHeader>
            <TableBody>
              <TableRow v-for="f in farmer.fields" :key="f.id" class="cursor-pointer" @click="router.push(`/fields/${f.id}`)">
                <TableCell class="font-medium">{{ f.name }}</TableCell>
                <TableCell>{{ CROP[f.crop] }}</TableCell>
                <TableCell class="text-right tabular-nums">{{ f.areaHa != null ? `${fmtNum(f.areaHa, 2)} ga` : '—' }}</TableCell>
                <TableCell class="text-muted-foreground">{{ fmtDate(f.createdDate) }}</TableCell>
              </TableRow>
              <TableEmpty v-if="!farmer.fields.length" :colspan="4">Dala yoʻq</TableEmpty>
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card v-if="referral && (referral.referredBy || referral.invited.length || referral.code)" class="lg:col-span-3">
        <CardHeader>
          <CardTitle class="text-base">Takliflar</CardTitle>
          <CardDescription>
            Kod: <span class="font-mono">{{ referral.code ?? '—' }}</span>
            <template v-if="referral.referredBy"> · Uni taklif qilgan:
              <RouterLink :to="`/farmers/${referral.referredBy.id}`" class="text-foreground font-medium hover:underline">
                {{ referral.referredBy.name ?? (referral.referredBy.username ? '@' + referral.referredBy.username : `#${referral.referredBy.id}`) }}
              </RouterLink>
            </template>
            <template v-if="referral.activatedAt"> · Faollashgan: {{ fmtDateTime(referral.activatedAt) }}</template>
          </CardDescription>
        </CardHeader>
        <CardContent class="grid gap-6 lg:grid-cols-2">
          <div>
            <p class="mb-2 text-sm font-medium">Taklif qilgan doʻstlari ({{ referral.invited.length }})</p>
            <ul v-if="referral.invited.length" class="divide-y rounded-lg border text-sm">
              <li v-for="i in referral.invited" :key="i.id" class="flex items-center justify-between gap-2 px-3 py-2">
                <RouterLink :to="`/farmers/${i.id}`" class="truncate hover:underline">{{ i.name ?? (i.username ? '@' + i.username : `#${i.id}`) }}</RouterLink>
                <StatusBadge :tone="i.activatedAt ? 'green' : 'gray'" :label="i.activatedAt ? 'Faol' : 'Tahlil olmagan'" />
              </li>
            </ul>
            <p v-else class="text-muted-foreground text-sm">Hali hech kimni taklif qilmagan</p>
          </div>
          <div>
            <p class="mb-2 text-sm font-medium">Olgan sovgʻalari ({{ referral.rewards.length }})</p>
            <ul v-if="referral.rewards.length" class="divide-y rounded-lg border text-sm">
              <li v-for="r in referral.rewards" :key="r.subscriptionId" class="flex items-center justify-between gap-2 px-3 py-2">
                <span>{{ r.planName }} · {{ fmtDate(r.validFrom) }} — {{ fmtDate(r.validUntil) }}</span>
                <span class="text-muted-foreground text-xs">{{ r.referralsUsed }} ta doʻst uchun</span>
              </li>
            </ul>
            <p v-else class="text-muted-foreground text-sm">Sovgʻa olmagan</p>
          </div>
        </CardContent>
      </Card>

      <Card v-if="auth.can(P.BILLING_READ)" class="lg:col-span-3">
        <CardHeader><CardTitle class="text-base">Toʻlovlar tarixi</CardTitle></CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Tarif</TableHead><TableHead>Davr</TableHead><TableHead class="text-right">Summa</TableHead>
                <TableHead>Usul</TableHead><TableHead>Toʻlangan</TableHead><TableHead>Kim yozgan</TableHead><TableHead>Holat</TableHead><TableHead class="bg-card sticky right-0" />
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="s in subs" :key="s.id">
                <TableCell class="font-medium">{{ s.planName }}</TableCell>
                <TableCell class="tabular-nums">{{ fmtDate(s.validFrom) }} – {{ fmtDate(s.validUntil) }}</TableCell>
                <TableCell class="text-right tabular-nums">{{ fmtMoney(s.amount, s.currency) }}</TableCell>
                <TableCell>{{ PAYMENT[s.paymentMethod] }}</TableCell>
                <TableCell class="text-muted-foreground">{{ fmtDateTime(s.paidAt) }}</TableCell>
                <TableCell class="text-muted-foreground">{{ s.recordedByEmail ?? '—' }}</TableCell>
                <TableCell>
                  <StatusBadge v-if="s.status === 'CANCELLED'" tone="red" label="Bekor qilingan" />
                  <StatusBadge v-else-if="s.activeToday" tone="green" label="Amalda" />
                  <StatusBadge v-else tone="gray" :label="new Date(s.validFrom) > new Date() ? 'Kelajakda' : 'Tugagan'" />
                </TableCell>
                <TableCell class="bg-card sticky right-0 text-right">
                  <Button v-if="auth.can(P.BILLING_MANAGE) && s.status === 'ACTIVE'" variant="ghost" size="sm" @click="cancelTarget = s">
                    <Ban /> Bekor
                  </Button>
                </TableCell>
              </TableRow>
              <TableEmpty v-if="!subs.length" :colspan="8">Toʻlovlar yoʻq</TableEmpty>
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  </template>

  <Dialog v-model:open="payOpen">
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>Toʻlovni qayd etish</DialogTitle>
        <DialogDescription>Boʻsh maydonlar tarifdan olinadi. Muddat joriy obuna tugagan kundan davom etadi.</DialogDescription>
      </DialogHeader>
      <form class="space-y-3" @submit.prevent="submitPay">
        <div class="space-y-1.5">
          <Label>Tarif</Label>
          <Select v-model="pay.planId">
            <SelectTrigger class="w-full"><SelectValue placeholder="Tanlang" /></SelectTrigger>
            <SelectContent>
              <SelectItem v-for="p in paidPlans" :key="p.id" :value="String(p.id)">
                {{ p.name }} · {{ fmtMoney(p.price, p.currency) }} / {{ p.durationDays }} kun
              </SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div class="space-y-1.5">
            <Label>Summa</Label>
            <Input v-model="pay.amount" type="number" min="0" step="0.01" :placeholder="selectedPlan ? String(selectedPlan.price) : ''" />
          </div>
          <div class="space-y-1.5">
            <Label>Usul</Label>
            <Select v-model="pay.paymentMethod">
              <SelectTrigger class="w-full"><SelectValue /></SelectTrigger>
              <SelectContent><template v-for="(l, k) in PAYMENT" :key="k"><SelectItem v-if="k !== 'REFERRAL'" :value="k">{{ l }}</SelectItem></template></SelectContent>
            </Select>
          </div>
          <div class="space-y-1.5"><Label>Boshlanish (ixtiyoriy)</Label><Input v-model="pay.validFrom" type="date" /></div>
          <div class="space-y-1.5"><Label>Tugash (ixtiyoriy)</Label><Input v-model="pay.validUntil" type="date" /></div>
        </div>
        <div class="space-y-1.5"><Label>Izoh</Label><Textarea v-model="pay.note" rows="2" maxlength="1000" placeholder="Chek raqami, kim toʻladi..." /></div>
        <DialogFooter>
          <Button type="button" variant="outline" @click="payOpen = false">Bekor</Button>
          <Button type="submit" :disabled="paying || !pay.planId"><Loader2 v-if="paying" class="animate-spin" /> Saqlash</Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>

  <Dialog :open="!!cancelTarget" @update:open="(v: boolean) => { if (!v) cancelTarget = null }">
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>Obunani bekor qilish</DialogTitle>
        <DialogDescription>Yozuv oʻchirilmaydi — tarixda “bekor qilingan” boʻlib qoladi.</DialogDescription>
      </DialogHeader>
      <Textarea v-model="cancelReason" placeholder="Sabab (majburiy)" rows="3" maxlength="500" />
      <DialogFooter>
        <Button variant="outline" @click="cancelTarget = null">Yopish</Button>
        <Button variant="destructive" :disabled="!cancelReason.trim()" @click="submitCancel">Bekor qilish</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
