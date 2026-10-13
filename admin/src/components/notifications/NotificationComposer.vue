<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { CalendarClock, Link2, Loader2, Search, Send, Users, X } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { farmersApi, notificationsApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Audience, AudiencePreview, Crop, FarmerListItem, LeadStatus } from '@/api/types'
import { fmtNum } from '@/lib/format'
import { CROP, LANG, LEAD } from '@/lib/labels'

/** Yangi xabar: kimga (hammaga / filtr / bitta fermer) → matn (3 til) → hozir yoki rejalashtirib. O'ngda — botdagi ko'rinish */
const props = defineProps<{ farmerId?: number | null }>()
const emit = defineEmits<{ sent: [] }>()

type Lang = 'uz' | 'ru' | 'en'
const LANGS: Lang[] = ['uz', 'ru', 'en']
const ANY = 'ANY'
const TRI = { [ANY]: 'Farqi yoʻq', true: 'Ha', false: 'Yoʻq' } as const

const form = reactive({
  title: '',
  type: 'ALL' as Audience['type'],
  languages: [] as string[],
  crops: [] as Crop[],
  leads: [] as LeadStatus[],
  countries: '',
  paid: ANY as string, hasFields: ANY as string, activated: ANY as string,
  from: '', to: '',
  texts: { uz: '', ru: '', en: '' } as Record<Lang, string>,
  withButton: false,
  buttonTexts: { uz: '', ru: '', en: '' } as Record<Lang, string>,
  buttonUrl: '',
  schedule: false,
  scheduledAt: '',
})
const lang = ref<Lang>('uz')
const farmer = ref<FarmerListItem | null>(null)

const toggle = <T,>(list: T[], v: T) => { const i = list.indexOf(v); if (i >= 0) list.splice(i, 1); else list.push(v) }
const tri = (v: string) => (v === ANY ? null : v === 'true')

const audience = computed<Audience>(() => ({
  type: form.type,
  farmerId: form.type === 'SINGLE' ? farmer.value?.id ?? null : null,
  languages: form.languages.length ? form.languages : undefined,
  crops: form.crops.length ? form.crops : undefined,
  leadStatuses: form.leads.length ? form.leads : undefined,
  countries: form.countries.trim() ? form.countries.split(/[\s,]+/).filter(Boolean).map((c) => c.toUpperCase()) : undefined,
  paid: tri(form.paid), hasFields: tri(form.hasFields), activated: tri(form.activated),
  registeredFrom: form.from || null, registeredTo: form.to || null,
}))

// ── Auditoriya hisobi (o'zgarganda, 400 ms kechikish bilan) ──
const preview = ref<AudiencePreview | null>(null)
const counting = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined
let seq = 0                       // eski (sekin) javob yangisining ustiga yozilmasin
watch(audience, () => {
  clearTimeout(timer)
  const my = ++seq
  if (form.type === 'SINGLE' && !farmer.value) { preview.value = null; return }
  timer = setTimeout(async () => {
    counting.value = true
    try {
      const r = await notificationsApi.preview(audience.value)
      if (my === seq) preview.value = r
    } catch (e) { if (my === seq) toast.error(errorMessage(e)) } finally { if (my === seq) counting.value = false }
  }, 400)
}, { deep: true, immediate: true })

// ── Bitta fermer qidiruvi ──
const q = ref('')
const found = ref<FarmerListItem[]>([])
let qTimer: ReturnType<typeof setTimeout> | undefined
watch(q, (v) => {
  clearTimeout(qTimer)
  if (v.trim().length < 2) { found.value = []; return }
  qTimer = setTimeout(async () => {
    try { found.value = (await farmersApi.search({ q: v.trim(), size: 8 })).content } catch { found.value = [] }
  }, 300)
})
// Fermer sahifasidan "Xabar yozish" — o'sha fermer tanlangan holda
onMounted(async () => {
  if (!props.farmerId) return
  try {
    farmer.value = await farmersApi.get(props.farmerId)
    form.type = 'SINGLE'
  } catch (e) { toast.error(errorMessage(e)) }
})
const farmerName = (f: FarmerListItem) => f.fullName || (f.telegramUsername ? '@' + f.telegramUsername : `Fermer #${f.id}`)

// ── Yuborish ──
const confirm = ref(false)
const sending = ref(false)
const canSend = computed(() => form.title.trim() && form.texts.uz.trim() && (preview.value?.count ?? 0) > 0
  && (!form.withButton || (form.buttonUrl.startsWith('https://') && form.buttonTexts.uz.trim()))
  && (!form.schedule || form.scheduledAt))

async function send() {
  sending.value = true
  const clean = (t: Record<Lang, string>) => Object.fromEntries(Object.entries(t).filter(([, v]) => v.trim()))
  try {
    await notificationsApi.create({
      title: form.title.trim(), texts: clean(form.texts), audience: audience.value,
      buttonTexts: form.withButton ? clean(form.buttonTexts) : null, buttonUrl: form.withButton ? form.buttonUrl.trim() : null,
      // datetime-local — brauzer vaqti, zonasiz; ISO (UTC) qilib yuboramiz — server zonasi boshqa bo'lsa ham to'g'ri
      scheduledAt: form.schedule ? new Date(form.scheduledAt).toISOString() : null,
    })
    toast.success(form.schedule ? 'Rejalashtirildi' : 'Yuborish boshlandi — natija «Kampaniyalar»da')
    confirm.value = false
    Object.assign(form, { title: '', texts: { uz: '', ru: '', en: '' }, withButton: false, buttonTexts: { uz: '', ru: '', en: '' }, buttonUrl: '', schedule: false, scheduledAt: '' })
    emit('sent')
  } catch (e) { toast.error(errorMessage(e)) } finally { sending.value = false }
}

const previewText = computed(() => form.texts[lang.value] || form.texts.uz || 'Xabar matni…')
const previewButton = computed(() => form.withButton ? (form.buttonTexts[lang.value] || form.buttonTexts.uz || 'Tugma') : null)
</script>

<template>
  <div class="grid gap-4 xl:grid-cols-[1fr_380px]">
    <div class="space-y-4">
      <!-- Kimga -->
      <Card>
        <CardHeader>
          <CardTitle class="flex items-center gap-2 text-base"><Users class="size-4" /> Kimga</CardTitle>
          <CardDescription>Botni bloklaganlar va oʻchirilganlarga yuborilmaydi</CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <div class="bg-muted inline-flex rounded-lg p-0.5">
            <button v-for="t in [{ k: 'ALL', l: 'Hammaga' }, { k: 'FILTER', l: 'Filtr boʻyicha' }, { k: 'SINGLE', l: 'Bitta fermerga' }]" :key="t.k" type="button"
                    :class="['rounded-md px-3 py-1.5 text-sm', form.type === t.k ? 'bg-background font-medium shadow-xs' : 'text-muted-foreground']"
                    @click="form.type = t.k as Audience['type']">{{ t.l }}</button>
          </div>

          <div v-if="form.type === 'FILTER'" class="grid gap-4 sm:grid-cols-2">
            <div class="space-y-1.5 sm:col-span-2">
              <Label>Til</Label>
              <div class="flex flex-wrap gap-1.5">
                <button v-for="l in LANGS" :key="l" type="button" :class="['rounded-full border px-3 py-1 text-sm', form.languages.includes(l) ? 'border-primary bg-primary/10 font-medium' : 'text-muted-foreground']"
                        @click="toggle(form.languages, l)">{{ LANG[l] ?? l }}</button>
              </div>
            </div>
            <div class="space-y-1.5 sm:col-span-2">
              <Label>Asosiy ekin</Label>
              <div class="flex flex-wrap gap-1.5">
                <button v-for="(l, k) in CROP" :key="k" type="button" :class="['rounded-full border px-3 py-1 text-sm', form.crops.includes(k) ? 'border-primary bg-primary/10 font-medium' : 'text-muted-foreground']"
                        @click="toggle(form.crops, k)">{{ l }}</button>
              </div>
            </div>
            <div v-for="f in [{ k: 'paid', l: 'Pullik tarifda' }, { k: 'hasFields', l: 'Dalasi bor' }, { k: 'activated', l: 'Tahlil olgan (faol)' }]" :key="f.k" class="space-y-1.5">
              <Label>{{ f.l }}</Label>
              <Select v-model="(form as any)[f.k]">
                <SelectTrigger class="w-full"><SelectValue /></SelectTrigger>
                <SelectContent><SelectItem v-for="(l, k) in TRI" :key="k" :value="String(k)">{{ l }}</SelectItem></SelectContent>
              </Select>
            </div>
            <div class="space-y-1.5">
              <Label>Mamlakat (UZ, KZ…)</Label>
              <Input v-model="form.countries" placeholder="Hammasi" class="uppercase" />
            </div>
            <div class="space-y-1.5">
              <Label>Roʻyxatdan oʻtgan: dan</Label>
              <Input v-model="form.from" type="date" />
            </div>
            <div class="space-y-1.5">
              <Label>gacha</Label>
              <Input v-model="form.to" type="date" />
            </div>
            <div class="space-y-1.5 sm:col-span-2">
              <Label>Lead holati</Label>
              <div class="flex flex-wrap gap-1.5">
                <button v-for="(l, k) in LEAD" :key="k" type="button" :class="['rounded-full border px-3 py-1 text-sm', form.leads.includes(k) ? 'border-primary bg-primary/10 font-medium' : 'text-muted-foreground']"
                        @click="toggle(form.leads, k)">{{ l }}</button>
              </div>
            </div>
          </div>

          <div v-if="form.type === 'SINGLE'" class="space-y-2">
            <div v-if="farmer" class="bg-muted/50 flex items-center justify-between rounded-lg border px-3 py-2">
              <span><b>{{ farmerName(farmer) }}</b> <span class="text-muted-foreground text-sm">#{{ farmer.id }} · {{ LANG[farmer.language] ?? farmer.language }}</span></span>
              <Button variant="ghost" size="icon-sm" @click="farmer = null"><X /></Button>
            </div>
            <template v-else>
              <div class="relative">
                <Search class="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
                <Input v-model="q" placeholder="Ism, @username yoki telefon…" class="pl-8" />
              </div>
              <div v-if="found.length" class="divide-y rounded-lg border">
                <button v-for="f in found" :key="f.id" type="button" class="hover:bg-muted/60 flex w-full items-center justify-between px-3 py-2 text-left text-sm"
                        :disabled="f.blockedBot" @click="farmer = f; q = ''; found = []">
                  <span>{{ farmerName(f) }} <span class="text-muted-foreground">#{{ f.id }}</span></span>
                  <span v-if="f.blockedBot" class="text-xs text-red-600">botni bloklagan</span>
                </button>
              </div>
            </template>
          </div>

          <div class="bg-primary/5 flex items-center gap-3 rounded-lg border border-primary/20 px-3 py-2.5">
            <Loader2 v-if="counting" class="text-primary size-5 animate-spin" />
            <Users v-else class="text-primary size-5" />
            <div class="text-sm">
              <b class="text-base tabular-nums">{{ fmtNum(preview?.count ?? 0) }}</b> ta fermerga boradi
              <span v-if="preview && preview.count" class="text-muted-foreground">
                · {{ Object.entries(preview.byLanguage).map(([l, n]) => `${l}: ${n}`).join(', ') }}
                <template v-if="preview.sample.length"> · masalan: {{ preview.sample.join(', ') }}</template>
              </span>
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- Xabar -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">Xabar</CardTitle>
          <CardDescription>Fermer oʻz tilida oladi; tarjima boʻsh boʻlsa — oʻzbekchasi</CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <div class="space-y-1.5">
            <Label>Sarlavha (faqat admin uchun)</Label>
            <Input v-model="form.title" maxlength="120" placeholder="Masalan: Bahorgi aksiya — 20% chegirma" />
          </div>
          <div>
            <div class="mb-2 flex gap-1">
              <button v-for="l in LANGS" :key="l" type="button"
                      :class="['rounded-md border px-2.5 py-1 text-sm', lang === l ? 'border-primary bg-primary/10 font-medium' : 'text-muted-foreground']"
                      @click="lang = l">{{ LANG[l] ?? l }}<span v-if="!form.texts[l].trim()" class="ml-1 text-amber-600">•</span></button>
            </div>
            <Textarea v-model="form.texts[lang]" rows="7" maxlength="3500" :placeholder="lang === 'uz' ? 'Majburiy' : 'Boʻsh boʻlsa — oʻzbekchasi yuboriladi'" />
            <p class="text-muted-foreground mt-1 text-right text-xs">{{ form.texts[lang].length }} / 3500</p>
          </div>
          <div class="space-y-2">
            <div class="flex items-center gap-2"><Switch v-model="form.withButton" /><Label class="flex items-center gap-1"><Link2 class="size-4" /> Havola tugmasi</Label></div>
            <div v-if="form.withButton" class="grid gap-2 sm:grid-cols-2">
              <Input v-model="form.buttonTexts[lang]" maxlength="40" :placeholder="`Tugma matni (${lang})`" />
              <Input v-model="form.buttonUrl" placeholder="https://…" />
            </div>
          </div>
          <div class="space-y-2">
            <div class="flex items-center gap-2"><Switch v-model="form.schedule" /><Label class="flex items-center gap-1"><CalendarClock class="size-4" /> Keyinroq yuborish</Label></div>
            <Input v-if="form.schedule" v-model="form.scheduledAt" type="datetime-local" class="w-64" />
          </div>
          <Button class="w-full sm:w-auto" :disabled="!canSend" @click="confirm = true"><Send /> {{ form.schedule ? 'Rejalashtirish' : 'Yuborish' }}</Button>
        </CardContent>
      </Card>
    </div>

    <!-- Botdagi ko'rinish -->
    <Card class="h-fit xl:sticky xl:top-20">
      <CardHeader><CardTitle class="text-base">Botda qanday koʻrinadi ({{ lang }})</CardTitle></CardHeader>
      <CardContent>
        <div class="space-y-1.5 rounded-xl bg-[#c9dcb5] p-3 dark:bg-[#1f2b1e]">
          <div class="max-w-[92%] rounded-2xl rounded-bl-md bg-white px-3 py-2 text-sm whitespace-pre-line shadow-xs dark:bg-zinc-800">{{ previewText }}</div>
          <div v-if="previewButton" class="max-w-[92%] rounded-lg bg-white/60 px-2 py-1.5 text-center text-xs font-semibold text-sky-700 dark:bg-white/10">🔗 {{ previewButton }}</div>
        </div>
      </CardContent>
    </Card>
  </div>

  <Dialog v-model:open="confirm">
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>{{ form.schedule ? 'Rejalashtirasizmi?' : 'Yuborasizmi?' }}</DialogTitle>
        <DialogDescription>
          «{{ form.title }}» — <b>{{ fmtNum(preview?.count ?? 0) }}</b> ta fermerga{{ form.schedule ? ` ${form.scheduledAt.replace('T', ' ')} da` : '' }}.
          Yuborilgan xabarni qaytarib boʻlmaydi (kutayotganlarini bekor qilish mumkin).
        </DialogDescription>
      </DialogHeader>
      <DialogFooter class="gap-2">
        <Button variant="outline" :disabled="sending" @click="confirm = false">Bekor</Button>
        <Button :disabled="sending" @click="send"><Loader2 v-if="sending" class="animate-spin" /><Send v-else /> Ha, {{ form.schedule ? 'rejalashtirish' : 'yuborish' }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
