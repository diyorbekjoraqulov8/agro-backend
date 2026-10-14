<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import { Loader2, RotateCcw, Save, TriangleAlert } from '@lucide/vue'
import PageHeader from '@/components/app/PageHeader.vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { billingApi, settingsApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { AppSetting, Plan } from '@/api/types'
import { fmtDateTime } from '@/lib/format'
import { P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

/**
 * Xizmatlar sozlamalari — deploysiz, darhol kuchga kiradi (bot va job'lar har safar bazadan o'qiydi).
 * Kalitlar va chegaralar backend'da (Setting enum); bu yerda faqat qiymat.
 */
const auth = useAuth()
const canEdit = computed(() => auth.can(P.SETTINGS_MANAGE))
const items = ref<AppSetting[] | null>(null)
const plans = ref<Plan[]>([])
const draft = reactive<Record<string, string>>({})
const saving = ref<string | null>(null)

const groups = computed(() => {
  const m = new Map<string, AppSetting[]>()
  for (const s of items.value ?? []) m.set(s.group, [...(m.get(s.group) ?? []), s])
  return [...m.entries()]
})
const paidPlans = computed(() => plans.value.filter((p) => !p.systemPlan && p.active))

async function load() {
  try {
    items.value = await settingsApi.list()
    for (const s of items.value) draft[s.key] = s.value
  } catch (e) {
    toast.error(errorMessage(e))
  }
}
onMounted(async () => {
  load()
  if (auth.can(P.BILLING_READ)) {
    try { plans.value = await billingApi.plans() } catch { /* tarif ro'yxati ixtiyoriy */ }
  }
})

function replace(s: AppSetting) {
  if (!items.value) return
  items.value = items.value.map((x) => (x.key === s.key ? s : x))
  draft[s.key] = s.value
}

async function save(s: AppSetting, value = draft[s.key] ?? '') {
  saving.value = s.key
  try {
    replace(await settingsApi.update(s.key, String(value)))
    toast.success('Saqlandi — darhol kuchga kirdi')
  } catch (e) {
    toast.error(errorMessage(e))
    draft[s.key] = s.value
  } finally {
    saving.value = null
  }
}

async function reset(s: AppSetting) {
  saving.value = s.key
  try {
    replace(await settingsApi.reset(s.key))
    toast.success('Default qiymatga qaytarildi')
  } catch (e) {
    toast.error(errorMessage(e))
  } finally {
    saving.value = null
  }
}

function planName(code: string) {
  return plans.value.find((p) => p.code === code)?.name ?? code
}
</script>

<template>
  <PageHeader title="Sozlamalar" description="Bot xizmatlari: yoqish/oʻchirish, limitlar va chegaralar. Oʻzgarish deploysiz, darhol kuchga kiradi" />

  <div v-if="!items" class="space-y-4"><Skeleton v-for="i in 2" :key="i" class="h-48 rounded-xl" /></div>

  <div v-else class="space-y-4">
    <Card v-for="[group, list] in groups" :key="group">
      <CardHeader>
        <CardTitle class="text-base">{{ group }}</CardTitle>
        <CardDescription v-if="!canEdit">Faqat koʻrish — oʻzgartirish uchun <code>settings:manage</code> huquqi kerak</CardDescription>
      </CardHeader>
      <CardContent class="divide-y p-0">
        <div v-for="s in list" :key="s.key" class="flex flex-col gap-3 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div class="min-w-0">
            <p class="text-sm font-medium">{{ s.description }}</p>
            <p class="text-muted-foreground mt-0.5 text-xs">
              <code>{{ s.key }}</code> · default: {{ s.type === 'PLAN' ? planName(s.defaultValue) : s.defaultValue }}
              <template v-if="s.min !== null"> · {{ s.min }}–{{ s.max }}</template>
              <template v-if="s.custom"> · oʻzgartirgan: {{ s.updatedByEmail ?? '—' }}, {{ fmtDateTime(s.updatedDate) }}</template>
            </p>
            <p v-if="s.warning" class="mt-1.5 flex items-start gap-1.5 rounded-md bg-amber-50 px-2 py-1 text-xs text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
              <TriangleAlert class="mt-0.5 size-3.5 shrink-0" />{{ s.warning }}
            </p>
          </div>

          <div class="flex shrink-0 items-center gap-2">
            <template v-if="s.type === 'BOOL'">
              <span class="text-muted-foreground text-sm">{{ s.value === 'true' ? 'Yoqilgan' : 'Oʻchirilgan' }}</span>
              <Switch :model-value="s.value === 'true'" :disabled="!canEdit || saving === s.key"
                      @update:model-value="(v: boolean) => save(s, String(v))" />
            </template>

            <template v-else-if="s.type === 'PLAN' && paidPlans.length && paidPlans.some((p) => p.code === s.value)">
              <Select :model-value="s.value" :disabled="!canEdit || saving === s.key" @update:model-value="(v) => save(s, String(v))">
                <SelectTrigger class="w-56"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="p in paidPlans" :key="p.code" :value="p.code">{{ p.name }} ({{ p.code }})</SelectItem>
                </SelectContent>
              </Select>
            </template>

            <form v-else class="flex items-center gap-2" @submit.prevent="save(s)">
              <Input v-model="draft[s.key]" :type="s.type === 'INT' ? 'number' : 'text'" :min="s.min ?? undefined" :max="s.max ?? undefined"
                     :disabled="!canEdit" :class="s.type === 'MODEL' ? 'w-56' : 'w-32'" :list="s.type === 'MODEL' ? 'claude-models' : undefined" />
              <Button v-if="canEdit" type="submit" size="sm" :disabled="saving === s.key || draft[s.key] === s.value">
                <Loader2 v-if="saving === s.key" class="animate-spin" /><Save v-else /> Saqlash
              </Button>
            </form>

            <Button v-if="canEdit && s.custom" variant="ghost" size="icon-sm" title="Default qiymatga qaytarish" :disabled="saving === s.key" @click="reset(s)">
              <RotateCcw />
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  </div>
  <!-- MODEL: tavsiyalar; boshqa (yangi) Claude model ID ham yozilishi mumkin — server shaklini tekshiradi -->
  <datalist id="claude-models">
    <option value="claude-opus-5-5">Claude Opus 5.5 — eng aniq</option>
    <option value="claude-sonnet-5-5">Claude Sonnet 5.5 — oʻrtacha</option>
    <option value="claude-haiku-5-5">Claude Haiku 5.5 — eng arzon</option>
  </datalist>
</template>
