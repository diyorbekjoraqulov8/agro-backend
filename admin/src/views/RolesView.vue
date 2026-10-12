<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Loader2, Pencil, Plus } from '@lucide/vue'
import { toast } from 'vue-sonner'
import PageHeader from '@/components/app/PageHeader.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { rolesApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { PermissionResponse, RoleWithPermissions } from '@/api/types'
import { P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

const auth = useAuth()
const roles = ref<RoleWithPermissions[]>([])
const open = ref(false)
const saving = ref(false)
const editing = ref<RoleWithPermissions | null>(null)
const form = reactive({ name: '', description: '', perms: new Set<number>() })

// Alohida "permissions" endpoint'i yo'q — barcha rollardagi huquqlar birlashmasi (ADMIN'da hammasi bor)
const allPermissions = computed<PermissionResponse[]>(() => {
  const m = new Map<number, PermissionResponse>()
  roles.value.forEach((r) => r.permissions.forEach((p) => m.set(p.id, p)))
  return [...m.values()].sort((a, b) => a.name.localeCompare(b.name))
})
const grouped = computed(() => {
  const g: Record<string, PermissionResponse[]> = {}
  allPermissions.value.forEach((p) => (g[p.name.split(':')[0]!] ??= []).push(p))
  return g
})

async function load() {
  try { roles.value = await rolesApi.detailed() } catch (e) { toast.error(errorMessage(e)) }
}

function edit(r: RoleWithPermissions | null) {
  editing.value = r
  form.name = r?.name ?? ''
  form.description = r?.description ?? ''
  form.perms = new Set(r?.permissions.map((p) => p.id) ?? [])
  open.value = true
}

function toggle(id: number, on: boolean | 'indeterminate') {
  on === true ? form.perms.add(id) : form.perms.delete(id)
}

async function save() {
  saving.value = true
  try {
    const permissionIds = [...form.perms]
    editing.value
      ? await rolesApi.update(editing.value.id, { description: form.description, permissionIds })
      : await rolesApi.create({ name: form.name.trim().toUpperCase(), description: form.description, permissionIds })
    toast.success('Rol saqlandi — huquqlar keyingi soʻrovdayoq kuchga kiradi')
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
  <PageHeader title="Rollar" description="Endpoint’lar rolni emas, huquqni tekshiradi — huquqlarni kodsiz oʻzgartirish mumkin">
    <Button v-if="auth.can(P.ROLE_CREATE)" @click="edit(null)"><Plus /> Yangi rol</Button>
  </PageHeader>
  <div class="grid gap-4 md:grid-cols-2">
    <Card v-for="r in roles" :key="r.id">
      <CardHeader>
        <CardTitle class="flex items-center gap-2 text-base">{{ r.name }}
          <StatusBadge v-if="r.systemRole" tone="blue" label="Tizim" /></CardTitle>
        <CardDescription>{{ r.description || '—' }}</CardDescription>
        <CardAction v-if="auth.can(P.ROLE_UPDATE)">
          <Button variant="ghost" size="icon-sm" @click="edit(r)"><Pencil /></Button>
        </CardAction>
      </CardHeader>
      <CardContent class="flex flex-wrap gap-1">
        <Badge v-for="p in r.permissions" :key="p.id" variant="secondary" class="font-mono text-xs">{{ p.name }}</Badge>
        <span v-if="!r.permissions.length" class="text-muted-foreground text-sm">Huquq yoʻq</span>
      </CardContent>
    </Card>
  </div>

  <Dialog v-model:open="open">
    <DialogContent class="max-h-[90vh] overflow-y-auto sm:max-w-xl">
      <DialogHeader><DialogTitle>{{ editing ? `Rol: ${editing.name}` : 'Yangi rol' }}</DialogTitle></DialogHeader>
      <form class="space-y-4" @submit.prevent="save">
        <div v-if="!editing" class="space-y-1.5"><Label>Nomi</Label><Input v-model="form.name" required placeholder="ACCOUNTANT" class="uppercase" /></div>
        <div class="space-y-1.5"><Label>Tavsif</Label><Input v-model="form.description" /></div>
        <div class="space-y-3">
          <Label>Huquqlar</Label>
          <div v-for="(perms, group) in grouped" :key="group" class="rounded-lg border p-3">
            <div class="text-muted-foreground mb-2 text-xs font-medium uppercase">{{ group }}</div>
            <label v-for="p in perms" :key="p.id" class="flex items-start gap-2 py-1 text-sm">
              <Checkbox :model-value="form.perms.has(p.id)" @update:model-value="toggle(p.id, $event)" />
              <span><span class="font-mono text-xs">{{ p.name }}</span><span class="text-muted-foreground"> — {{ p.description }}</span></span>
            </label>
          </div>
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" @click="open = false">Bekor</Button>
          <Button type="submit" :disabled="saving"><Loader2 v-if="saving" class="animate-spin" /> Saqlash</Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
