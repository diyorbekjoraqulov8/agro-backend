<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Loader2, Pencil, Plus } from '@lucide/vue'
import { toast } from 'vue-sonner'
import PageHeader from '@/components/app/PageHeader.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { billingApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Plan, PlanRequest } from '@/api/types'
import { fmtMoney, fmtNum } from '@/lib/format'
import { P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

const auth = useAuth()
const plans = ref<Plan[]>([])
const open = ref(false)
const saving = ref(false)
const editing = ref<Plan | null>(null)
const form = reactive({
  code: '', name: '', description: '', price: '0', currency: 'UZS', durationDays: '30',
  maxFieldHa: '100', maxFields: '50', maxAnalyses: '', active: true,
})

async function load() {
  try { plans.value = await billingApi.plans() } catch (e) { toast.error(errorMessage(e)) }
}

function edit(p: Plan | null) {
  editing.value = p
  Object.assign(form, p ? {
    code: p.code, name: p.name, description: p.description ?? '', price: String(p.price), currency: p.currency,
    durationDays: p.durationDays != null ? String(p.durationDays) : '', maxFieldHa: String(p.maxFieldHa),
    maxFields: p.maxFields != null ? String(p.maxFields) : '', maxAnalyses: p.maxAnalyses != null ? String(p.maxAnalyses) : '', active: p.active,
  } : { code: '', name: '', description: '', price: '0', currency: 'UZS', durationDays: '30', maxFieldHa: '100', maxFields: '50', maxAnalyses: '', active: true })
  open.value = true
}

async function save() {
  saving.value = true
  const body: PlanRequest = {
    code: editing.value ? undefined : form.code.trim().toUpperCase(),
    name: form.name, description: form.description || null, price: Number(form.price), currency: form.currency.toUpperCase(),
    durationDays: form.durationDays === '' ? null : Number(form.durationDays),
    maxFieldHa: Number(form.maxFieldHa), maxFields: form.maxFields === '' ? null : Number(form.maxFields),
    maxAnalyses: form.maxAnalyses === '' ? null : Number(form.maxAnalyses), active: form.active,
  }
  try {
    editing.value ? await billingApi.updatePlan(editing.value.id, body) : await billingApi.createPlan(body)
    toast.success('Tarif saqlandi')
    open.value = false
    load()
  } catch (e) {
    toast.error(errorMessage(e))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <PageHeader title="Tariflar" description="Limitlar bazada — oʻzgarish darhol kuchga kiradi (deploysiz)">
    <Button v-if="auth.can(P.BILLING_MANAGE)" @click="edit(null)"><Plus /> Yangi tarif</Button>
  </PageHeader>
  <Card>
    <CardContent>
      <Table>
        <TableHeader>
          <TableRow><TableHead>Tarif</TableHead><TableHead class="text-right">Narx</TableHead><TableHead class="text-right">Muddat</TableHead>
            <TableHead class="text-right">Maks. maydon</TableHead><TableHead class="text-right">Dalalar</TableHead><TableHead class="text-right">Tahlillar</TableHead>
            <TableHead>Holat</TableHead><TableHead class="bg-card sticky right-0" /></TableRow>
        </TableHeader>
        <TableBody>
          <!-- Qatorni bosish — tahrirlash (tor ekranda ✏️ ustuni jadval ichida o'ngda qolib ketmasin) -->
          <TableRow v-for="p in plans" :key="p.id" :class="auth.can(P.BILLING_MANAGE) ? 'cursor-pointer' : ''"
                    :title="auth.can(P.BILLING_MANAGE) ? 'Tahrirlash' : undefined" @click="auth.can(P.BILLING_MANAGE) && edit(p)">
            <TableCell>
              <div class="font-medium">{{ p.name }} <span class="text-muted-foreground font-mono text-xs">{{ p.code }}</span></div>
              <div class="text-muted-foreground max-w-64 truncate text-xs">{{ p.description }}</div>
            </TableCell>
            <TableCell class="text-right tabular-nums">{{ p.systemPlan ? '—' : fmtMoney(p.price, p.currency) }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ p.durationDays ? `${p.durationDays} kun` : '—' }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ fmtNum(p.maxFieldHa) }} ga</TableCell>
            <TableCell class="text-right tabular-nums">{{ p.maxFields ?? '∞' }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ p.maxAnalyses ?? '∞' }}{{ p.systemPlan ? ' (umrbod)' : '' }}</TableCell>
            <TableCell>
              <StatusBadge v-if="p.systemPlan" tone="blue" label="Toʻlovsiz rejim" />
              <StatusBadge v-else :tone="p.active ? 'green' : 'gray'" :label="p.active ? 'Faol' : 'Nofaol'" />
            </TableCell>
            <TableCell class="bg-card sticky right-0 text-right">
              <Button v-if="auth.can(P.BILLING_MANAGE)" variant="ghost" size="icon-sm" aria-label="Tahrirlash" @click.stop="edit(p)"><Pencil /></Button>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </CardContent>
  </Card>

  <Dialog v-model:open="open">
    <DialogContent class="sm:max-w-lg">
      <DialogHeader>
        <DialogTitle>{{ editing ? `Tarif: ${editing.code}` : 'Yangi tarif' }}</DialogTitle>
        <DialogDescription v-if="editing?.systemPlan">Toʻlov qilmagan fermerlarga qoʻllanadi — faqat nom va limitlar oʻzgaradi.</DialogDescription>
      </DialogHeader>
      <form class="grid grid-cols-2 gap-3" @submit.prevent="save">
        <div v-if="!editing" class="space-y-1.5"><Label>Kod</Label><Input v-model="form.code" placeholder="YEARLY" required class="uppercase" /></div>
        <div class="space-y-1.5" :class="editing ? 'col-span-2' : ''"><Label>Nomi</Label><Input v-model="form.name" required maxlength="80" /></div>
        <div class="col-span-2 space-y-1.5"><Label>Tavsif</Label><Textarea v-model="form.description" rows="2" maxlength="1000" /></div>
        <template v-if="!editing?.systemPlan">
          <div class="space-y-1.5"><Label>Narx</Label><Input v-model="form.price" type="number" min="0" step="0.01" required /></div>
          <div class="space-y-1.5"><Label>Valyuta</Label><Input v-model="form.currency" maxlength="3" required class="uppercase" /></div>
          <div class="space-y-1.5"><Label>Muddat (kun)</Label><Input v-model="form.durationDays" type="number" min="1" required /></div>
          <div class="flex items-end gap-2 pb-1.5"><Switch v-model="form.active" /><Label>Faol</Label></div>
        </template>
        <div class="space-y-1.5"><Label>Maks. maydon (ga)</Label><Input v-model="form.maxFieldHa" type="number" min="0.5" max="500" step="0.5" required /></div>
        <div class="space-y-1.5"><Label>Maks. dalalar (boʻsh — cheksiz)</Label><Input v-model="form.maxFields" type="number" min="1" /></div>
        <div class="col-span-2 space-y-1.5"><Label>Tahlillar soni (boʻsh — cheksiz)</Label><Input v-model="form.maxAnalyses" type="number" min="0" /></div>
        <DialogFooter class="col-span-2">
          <Button type="button" variant="outline" @click="open = false">Bekor</Button>
          <Button type="submit" :disabled="saving"><Loader2 v-if="saving" class="animate-spin" /> Saqlash</Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
