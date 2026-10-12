<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import { ArrowDown, ArrowUp, Flag, Loader2, MessageSquareText, Pencil, Plus, Save, Trash2, HelpCircle } from '@lucide/vue'
import PageHeader from '@/components/app/PageHeader.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { onboardingApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { OnboardingStep, StepKind, StepOption, StepTarget, Texts } from '@/api/types'
import { fmtDateTime } from '@/lib/format'
import { CROP, IRRIGATION, P, WATER } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

/**
 * Botdagi /start'dan keyingi xabar va savollar — qadamma-qadam. O'ngda — botda qanday ko'rinishi (jonli).
 * O'rnatilgan qadamlar (🔒) o'chirilmaydi, faqat matni; Yakuniy xabar doim oxirida (ostida "🗺 Dalani belgilash" tugmasi).
 */
type Lang = 'uz' | 'ru' | 'en'
const LANGS: { key: Lang; label: string }[] = [{ key: 'uz', label: 'Oʻzbekcha' }, { key: 'ru', label: 'Русский' }, { key: 'en', label: 'English' }]
const TARGET: Record<StepTarget, string> = { MAIN_CROP: 'Asosiy ekin', IRRIGATION_TYPE: 'Sugʻorish usuli', WATER_SOURCE: 'Suv manbai' }
const TARGET_OPTIONS: Record<StepTarget, Record<string, string>> = { MAIN_CROP: CROP, IRRIGATION_TYPE: IRRIGATION, WATER_SOURCE: WATER }

const auth = useAuth()
const canEdit = computed(() => auth.can(P.ONBOARDING_MANAGE))
const steps = ref<OnboardingStep[] | null>(null)
const busy = ref(false)
const previewLang = ref<Lang>('uz')

async function load() {
  try { steps.value = await onboardingApi.steps() } catch (e) { toast.error(errorMessage(e)) }
}
onMounted(load)

const questions = computed(() => (steps.value ?? []).filter((s) => s.active && s.kind === 'QUESTION'))
const isFinish = (s: OnboardingStep) => s.code === 'FINISH'
function text(s: OnboardingStep, lang: Lang) {
  return (s.texts[lang] || s.texts.uz || '').replace(', {name}', ', Aziz').replace('{name}', 'Aziz')
}
function optionLabels(s: OnboardingStep, lang: Lang): string[] {
  if (s.target) return Object.values(TARGET_OPTIONS[s.target])          // o'rnatilgan — enum (o'zbekcha nomlari)
  return s.options.map((o) => o.labels[lang] || o.labels.uz || o.value)
}

async function move(s: OnboardingStep, dir: 'up' | 'down') {
  busy.value = true
  try { steps.value = await onboardingApi.move(s.id, dir) } catch (e) { toast.error(errorMessage(e)) } finally { busy.value = false }
}
async function toggle(s: OnboardingStep, active: boolean) {
  try {
    const r = await onboardingApi.update(s.id, { texts: s.texts, options: s.options, active })
    steps.value = steps.value!.map((x) => (x.id === r.id ? r : x))
    toast.success(active ? 'Yoqildi' : 'Oʻchirildi — botda koʻrinmaydi')
  } catch (e) { toast.error(errorMessage(e)) }
}
async function remove(s: OnboardingStep) {
  if (!confirmDelete.value || confirmDelete.value.id !== s.id) { confirmDelete.value = s; return }
  busy.value = true
  try {
    await onboardingApi.remove(s.id)
    confirmDelete.value = null
    await load()
    toast.success('Oʻchirildi')
  } catch (e) { toast.error(errorMessage(e)) } finally { busy.value = false }
}
const confirmDelete = ref<OnboardingStep | null>(null)

// ── Tahrirlash / yaratish ──
const open = ref(false)
const editing = ref<OnboardingStep | null>(null)
const form = reactive<{ code: string; kind: StepKind; texts: Record<Lang, string>; options: { value: string; labels: Record<Lang, string> }[]; active: boolean }>({
  code: '', kind: 'MESSAGE', texts: { uz: '', ru: '', en: '' }, options: [], active: true,
})
const formLang = ref<Lang>('uz')

function openEdit(s: OnboardingStep | null, kind: StepKind = 'MESSAGE') {
  editing.value = s
  form.code = s?.code ?? ''
  form.kind = s?.kind ?? kind
  form.texts = { uz: s?.texts.uz ?? '', ru: s?.texts.ru ?? '', en: s?.texts.en ?? '' }
  form.options = (s?.options ?? []).map((o) => ({ value: o.value, labels: { uz: o.labels.uz ?? '', ru: o.labels.ru ?? '', en: o.labels.en ?? '' } }))
  if (!s && kind === 'QUESTION') form.options = [{ value: 'HA', labels: { uz: 'Ha', ru: 'Да', en: 'Yes' } }, { value: 'YOQ', labels: { uz: 'Yoʻq', ru: 'Нет', en: 'No' } }]
  form.active = s?.active ?? true
  formLang.value = 'uz'
  open.value = true
}
const customQuestion = computed(() => form.kind === 'QUESTION' && !editing.value?.target)

function addOption() {
  form.options.push({ value: `V${form.options.length + 1}`, labels: { uz: '', ru: '', en: '' } })
}

async function save() {
  busy.value = true
  const clean = (t: Record<Lang, string>): Texts => Object.fromEntries(Object.entries(t).filter(([, v]) => v.trim())) as Texts
  const body = {
    code: editing.value ? undefined : form.code.trim().toUpperCase(),
    kind: editing.value ? undefined : form.kind,
    texts: clean(form.texts),
    options: customQuestion.value ? form.options.map((o): StepOption => ({ value: o.value.trim().toUpperCase(), labels: clean(o.labels) })) : undefined,
    active: form.active,
  }
  try {
    if (editing.value) await onboardingApi.update(editing.value.id, body)
    else await onboardingApi.create(body)
    open.value = false
    await load()
    toast.success('Saqlandi — botda darhol kuchga kirdi')
  } catch (e) { toast.error(errorMessage(e)) } finally { busy.value = false }
}
</script>

<template>
  <PageHeader title="Onboarding" description="Fermer /start bosgandan keyin bot yuboradigan xabar va savollar — tartib bilan. Oʻzgarish darhol kuchga kiradi">
    <template v-if="canEdit">
      <Button variant="outline" @click="openEdit(null, 'MESSAGE')"><MessageSquareText /> Xabar qoʻshish</Button>
      <Button @click="openEdit(null, 'QUESTION')"><Plus /> Savol qoʻshish</Button>
    </template>
  </PageHeader>

  <div class="grid gap-4 xl:grid-cols-[1fr_420px]">
    <!-- Qadamlar -->
    <Card>
      <CardHeader>
        <CardTitle class="text-base">Qadamlar</CardTitle>
        <CardDescription>Bot shu tartibda yuboradi. Savollar avtomatik raqamlanadi (1/{{ questions.length }}, 2/{{ questions.length }}…). Matnda <code>{name}</code> — fermer ismi</CardDescription>
      </CardHeader>
      <CardContent class="space-y-2">
        <template v-if="!steps"><Skeleton v-for="i in 5" :key="i" class="h-20 rounded-lg" /></template>
        <div v-for="(s, i) in steps ?? []" :key="s.id"
             :class="['flex gap-3 rounded-xl border p-3 transition', s.active ? 'bg-background' : 'bg-muted/40 opacity-70']">
          <div :class="['flex size-9 shrink-0 items-center justify-center rounded-lg',
                        isFinish(s) ? 'bg-primary text-primary-foreground' : s.kind === 'QUESTION' ? 'bg-sky-100 text-sky-700 dark:bg-sky-500/15 dark:text-sky-300' : 'bg-muted text-muted-foreground']">
            <Flag v-if="isFinish(s)" class="size-4" /><HelpCircle v-else-if="s.kind === 'QUESTION'" class="size-4" /><MessageSquareText v-else class="size-4" />
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-1.5">
              <span class="font-medium">{{ isFinish(s) ? 'Yakuniy xabar' : s.kind === 'QUESTION' ? 'Savol' : 'Xabar' }}</span>
              <code class="text-muted-foreground text-xs">{{ s.code }}</code>
              <StatusBadge v-if="s.system" tone="gray" label="🔒 oʻrnatilgan" />
              <StatusBadge v-if="s.target" tone="blue" :label="TARGET[s.target]" />
              <StatusBadge v-if="s.kind === 'QUESTION'" tone="green" :label="`${s.answers} javob`" />
              <StatusBadge v-if="!s.texts.ru || !s.texts.en" tone="amber" :label="`tarjima yoʻq: ${[!s.texts.ru && 'ru', !s.texts.en && 'en'].filter(Boolean).join(', ')}`" />
            </div>
            <p class="text-muted-foreground mt-1 line-clamp-2 text-sm whitespace-pre-line">{{ s.texts.uz }}</p>
            <p v-if="s.kind === 'QUESTION'" class="mt-1.5 flex flex-wrap gap-1">
              <span v-for="l in optionLabels(s, 'uz')" :key="l" class="bg-muted rounded-md px-1.5 py-0.5 text-xs">{{ l }}</span>
            </p>
          </div>
          <div v-if="canEdit" class="flex shrink-0 flex-col items-end gap-1.5">
            <Switch :model-value="s.active" :disabled="s.system && (!!s.target || isFinish(s))" title="Botda koʻrsatish" @update:model-value="(v: boolean) => toggle(s, v)" />
            <div class="flex gap-0.5">
              <Button variant="ghost" size="icon-sm" :disabled="busy || isFinish(s) || i === 0" title="Yuqoriga" @click="move(s, 'up')"><ArrowUp /></Button>
              <Button variant="ghost" size="icon-sm" :disabled="busy || isFinish(s) || i >= (steps?.length ?? 0) - 2" title="Pastga" @click="move(s, 'down')"><ArrowDown /></Button>
              <Button variant="ghost" size="icon-sm" title="Tahrirlash" @click="openEdit(s)"><Pencil /></Button>
              <Button v-if="!s.system" variant="ghost" size="icon-sm" :class="confirmDelete?.id === s.id ? 'text-red-600' : ''"
                      :title="confirmDelete?.id === s.id ? 'Yana bir bosing — oʻchiriladi' : 'Oʻchirish'" @click="remove(s)"><Trash2 /></Button>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- Botdagi ko'rinish -->
    <Card class="h-fit xl:sticky xl:top-20">
      <CardHeader class="flex-row items-center justify-between space-y-0">
        <CardTitle class="text-base">Botda qanday koʻrinadi</CardTitle>
        <div class="bg-muted inline-flex rounded-lg p-0.5 text-xs">
          <button v-for="l in LANGS" :key="l.key" type="button"
                  :class="['rounded-md px-2 py-1', previewLang === l.key ? 'bg-background shadow-xs' : 'text-muted-foreground']"
                  @click="previewLang = l.key">{{ l.key.toUpperCase() }}</button>
        </div>
      </CardHeader>
      <CardContent>
        <div class="max-h-[70vh] space-y-2 overflow-y-auto rounded-xl bg-[#c9dcb5] p-3 dark:bg-[#1f2b1e]">
          <div class="ml-auto w-fit rounded-2xl rounded-br-md bg-[#e1ffc7] px-3 py-1.5 text-sm shadow-xs dark:bg-[#2b5b2b]">/start</div>
          <template v-for="s in (steps ?? []).filter((x) => x.active)" :key="s.id">
            <div class="max-w-[92%] rounded-2xl rounded-bl-md bg-white px-3 py-2 text-sm whitespace-pre-line shadow-xs dark:bg-zinc-800">
              <template v-if="s.kind === 'QUESTION'">{{ questions.indexOf(s) + 1 }}/{{ questions.length }}. </template>{{ text(s, previewLang) }}
            </div>
            <div v-if="s.kind === 'QUESTION'" class="grid max-w-[92%] grid-cols-2 gap-1">
              <span v-for="l in optionLabels(s, previewLang)" :key="l"
                    class="truncate rounded-lg bg-white/60 px-2 py-1.5 text-center text-xs font-medium dark:bg-white/10">{{ l }}</span>
            </div>
            <div v-if="isFinish(s)" class="max-w-[92%] rounded-lg bg-white/60 px-2 py-1.5 text-center text-xs font-semibold text-[#137d41] dark:bg-white/10">
              🗺 Dalani xaritada belgilash
            </div>
          </template>
        </div>
        <p class="text-muted-foreground mt-2 text-xs">Savolga javob tugmasi bosilgach keyingi qadam yuboriladi. Oʻrnatilgan savollar variantlari botning oʻz tilida chiqadi.</p>
      </CardContent>
    </Card>
  </div>

  <Dialog v-model:open="open">
    <DialogContent class="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
      <DialogHeader>
        <DialogTitle>{{ editing ? (isFinish(editing) ? 'Yakuniy xabar' : `${editing.kind === 'QUESTION' ? 'Savol' : 'Xabar'}: ${editing.code}`) : form.kind === 'QUESTION' ? 'Yangi savol' : 'Yangi xabar' }}</DialogTitle>
        <DialogDescription>
          Matnda <code>{name}</code> — fermer ismi bilan almashtiriladi. Tarjima boʻsh boʻlsa — oʻzbekchasi chiqadi.
          <template v-if="editing?.target"> Bu savol javobi fermer profiliga yoziladi — variantlar oʻzgarmaydi.</template>
        </DialogDescription>
      </DialogHeader>

      <form class="space-y-4" @submit.prevent="save">
        <div v-if="!editing" class="space-y-1.5">
          <Label>Kod (lotin, katta harf)</Label>
          <Input v-model="form.code" placeholder="FARM_SIZE" required pattern="[A-Za-z][A-Za-z0-9_]{1,31}" class="uppercase" />
          <p class="text-muted-foreground text-xs">Javoblar shu kod bilan saqlanadi (keyin oʻzgarmaydi)</p>
        </div>

        <div>
          <div class="mb-2 flex gap-1">
            <button v-for="l in LANGS" :key="l.key" type="button"
                    :class="['rounded-md border px-2.5 py-1 text-sm', formLang === l.key ? 'border-primary bg-primary/10 font-medium' : 'text-muted-foreground']"
                    @click="formLang = l.key">{{ l.label }}<span v-if="!form.texts[l.key].trim()" class="ml-1 text-amber-600">•</span></button>
          </div>
          <Textarea v-model="form.texts[formLang]" rows="8" maxlength="3500" :required="formLang === 'uz'"
                    :placeholder="formLang === 'uz' ? 'Majburiy' : 'Boʻsh boʻlsa — oʻzbekchasi'" />
          <p class="text-muted-foreground mt-1 text-right text-xs">{{ form.texts[formLang].length }} / 3500</p>
        </div>

        <div v-if="customQuestion" class="space-y-2">
          <div class="flex items-center justify-between">
            <Label>Javob variantlari (2–12)</Label>
            <Button type="button" size="sm" variant="outline" :disabled="form.options.length >= 12" @click="addOption"><Plus /> Variant</Button>
          </div>
          <div v-for="(o, i) in form.options" :key="i" class="grid grid-cols-[110px_1fr_auto] items-center gap-2">
            <Input v-model="o.value" placeholder="QIYMAT" class="font-mono text-xs uppercase" maxlength="24" required />
            <Input v-model="o.labels[formLang]" :placeholder="`Tugma matni (${formLang})`" maxlength="40" :required="formLang === 'uz'" />
            <Button type="button" variant="ghost" size="icon-sm" :disabled="form.options.length <= 2" @click="form.options.splice(i, 1)"><Trash2 /></Button>
          </div>
        </div>

        <div v-if="!(editing?.system && (editing?.target || isFinish(editing)))" class="flex items-center gap-2">
          <Switch v-model="form.active" /><Label>Botda koʻrsatish</Label>
        </div>
        <p v-if="editing" class="text-muted-foreground text-xs">Oxirgi oʻzgarish: {{ editing.updatedByEmail ?? 'tizim' }}, {{ fmtDateTime(editing.updatedDate) }}</p>

        <DialogFooter class="gap-2">
          <Button type="button" variant="outline" @click="open = false">Bekor</Button>
          <Button type="submit" :disabled="busy"><Loader2 v-if="busy" class="animate-spin" /><Save v-else /> Saqlash</Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
