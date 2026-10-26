<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import { Loader2, RotateCcw, Save, Search, TriangleAlert } from '@lucide/vue'
import PageHeader from '@/components/app/PageHeader.vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
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

const ALL = 'ALL'
const group = ref(ALL)
const q = ref('')
const groupNames = computed(() => [...new Set((items.value ?? []).map((s) => s.group))])
// Jadval: guruh sarlavhasi qatori + sozlamalar; filtr — guruh va matn (tavsif yoki kalit)
const groups = computed(() => {
  const needle = q.value.trim().toLowerCase()
  const m = new Map<string, AppSetting[]>()
  for (const s of items.value ?? []) {
    if (group.value !== ALL && s.group !== group.value) continue
    if (needle && !s.description.toLowerCase().includes(needle) && !s.key.toLowerCase().includes(needle)) continue
    m.set(s.group, [...(m.get(s.group) ?? []), s])
  }
  return [...m.entries()]
})
const paidPlans = computed(() => plans.value.filter((p) => !p.systemPlan && p.active && !p.contactSales))

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

  <div v-if="!items" class="space-y-2"><Skeleton v-for="i in 6" :key="i" class="h-12 rounded-lg" /></div>

  <template v-else>
    <div class="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center">
      <div class="relative sm:w-72">
        <Search class="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
        <Input v-model="q" placeholder="Qidirish: tavsif yoki kalit" class="pl-8" />
      </div>
      <nav class="flex gap-1 overflow-x-auto" aria-label="Guruhlar">
        <Button v-for="g in [ALL, ...groupNames]" :key="g" size="sm" :variant="group === g ? 'default' : 'outline'" class="shrink-0" @click="group = g">
          {{ g === ALL ? 'Hammasi' : g }}
        </Button>
      </nav>
    </div>
    <p v-if="!canEdit" class="text-muted-foreground mb-2 text-xs">Faqat koʻrish — oʻzgartirish uchun <code>settings:manage</code> huquqi kerak</p>

    <Card>
      <CardContent class="p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead class="sm:min-w-72">Sozlama</TableHead>
              <TableHead class="sm:min-w-56">Qiymat</TableHead>
              <TableHead class="hidden lg:table-cell">Default</TableHead>
              <TableHead class="hidden xl:table-cell">Oʻzgartirgan</TableHead>
              <TableHead class="w-10" />
            </TableRow>
          </TableHeader>
          <TableBody>
            <template v-for="[g, list] in groups" :key="g">
              <TableRow class="bg-muted/50 hover:bg-muted/50">
                <TableCell colspan="5" class="py-2 text-xs font-semibold tracking-wide uppercase">{{ g }}</TableCell>
              </TableRow>
              <TableRow v-for="s in list" :key="s.key">
                <TableCell class="align-top whitespace-normal">
                  <p class="text-sm font-medium">{{ s.description }}</p>
                  <p class="text-muted-foreground mt-0.5 text-xs"><code>{{ s.key }}</code>
                    <template v-if="s.min !== null"> · {{ s.min }}–{{ s.max }}</template>
                    <span class="lg:hidden"> · default: {{ s.type === 'PLAN' ? planName(s.defaultValue) : s.defaultValue }}</span></p>
                  <p v-if="s.warning" class="mt-1.5 flex items-start gap-1.5 rounded-md bg-amber-50 px-2 py-1 text-xs text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
                    <TriangleAlert class="mt-0.5 size-3.5 shrink-0" />{{ s.warning }}
                  </p>
                </TableCell>

                <TableCell class="align-top">
                  <div v-if="s.type === 'BOOL'" class="flex items-center gap-2">
                    <Switch :model-value="s.value === 'true'" :disabled="!canEdit || saving === s.key"
                            @update:model-value="(v: boolean) => save(s, String(v))" />
                    <span class="text-muted-foreground text-sm">{{ s.value === 'true' ? 'Yoqilgan' : 'Oʻchirilgan' }}</span>
                  </div>

                  <Select v-else-if="s.type === 'PLAN' && paidPlans.length && paidPlans.some((p) => p.code === s.value)"
                          :model-value="s.value" :disabled="!canEdit || saving === s.key" @update:model-value="(v) => save(s, String(v))">
                    <SelectTrigger class="w-56"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem v-for="p in paidPlans" :key="p.code" :value="p.code">{{ p.name }} ({{ p.code }})</SelectItem>
                    </SelectContent>
                  </Select>

                  <form v-else class="flex items-center gap-2" @submit.prevent="save(s)">
                    <Input v-model="draft[s.key]" :type="s.type === 'INT' ? 'number' : s.type === 'DATE' ? 'date' : 'text'" :min="s.min ?? undefined" :max="s.max ?? undefined"
                           :disabled="!canEdit" :class="s.type === 'MODEL' ? 'w-56' : s.type === 'DATE' ? 'w-40' : 'w-28'" :list="s.type === 'MODEL' ? 'claude-models' : undefined" />
                    <Button v-if="canEdit" type="submit" size="sm" :disabled="saving === s.key || draft[s.key] === s.value">
                      <Loader2 v-if="saving === s.key" class="animate-spin" /><Save v-else /> Saqlash
                    </Button>
                  </form>
                </TableCell>

                <TableCell class="text-muted-foreground hidden align-top text-sm lg:table-cell">
                  {{ s.type === 'PLAN' ? planName(s.defaultValue) : s.type === 'BOOL' ? (s.defaultValue === 'true' ? 'Yoqilgan' : 'Oʻchirilgan') : s.defaultValue }}
                </TableCell>
                <TableCell class="text-muted-foreground hidden align-top text-xs xl:table-cell">
                  <template v-if="s.custom">{{ s.updatedByEmail ?? '—' }}<br>{{ fmtDateTime(s.updatedDate) }}</template>
                  <template v-else>—</template>
                </TableCell>
                <TableCell class="align-top">
                  <Button v-if="canEdit && s.custom" variant="ghost" size="icon-sm" title="Default qiymatga qaytarish" :disabled="saving === s.key" @click="reset(s)">
                    <RotateCcw />
                  </Button>
                </TableCell>
              </TableRow>
            </template>
            <TableRow v-if="!groups.length">
              <TableCell colspan="5" class="text-muted-foreground py-8 text-center text-sm">Hech narsa topilmadi</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  </template>
  <!-- MODEL: tavsiyalar; boshqa (yangi) Claude model ID ham yozilishi mumkin — server shaklini tekshiradi -->
  <datalist id="claude-models">
    <option value="claude-opus-5-5">Claude Opus 5.5 — eng aniq</option>
    <option value="claude-sonnet-5-5">Claude Sonnet 5.5 — oʻrtacha</option>
    <option value="claude-haiku-5-5">Claude Haiku 5.5 — eng arzon</option>
  </datalist>
</template>
