<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { LogOut } from '@lucide/vue'
import { toast } from 'vue-sonner'
import PageHeader from '@/components/app/PageHeader.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { authApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { SessionInfo } from '@/api/types'
import { fmtDateTime } from '@/lib/format'
import { useAuth } from '@/stores/auth'

const auth = useAuth()
const sessions = ref<SessionInfo[]>([])

async function load() {
  try { sessions.value = await authApi.sessions() } catch (e) { toast.error(errorMessage(e)) }
}
async function revoke(s: SessionInfo) {
  try {
    await authApi.revokeSession(s.id)
    toast.success('Sessiya yakunlandi')
    load()
  } catch (e) {
    toast.error(errorMessage(e))
  }
}
onMounted(load)
</script>

<template>
  <PageHeader title="Profil" :description="auth.me?.email" />
  <div class="grid gap-4 lg:grid-cols-3">
    <Card>
      <CardHeader><CardTitle class="text-base">Rollar va huquqlar</CardTitle></CardHeader>
      <CardContent class="space-y-3">
        <div class="flex flex-wrap gap-1"><Badge v-for="r in auth.me?.roles" :key="r">{{ r }}</Badge></div>
        <div class="flex flex-wrap gap-1">
          <Badge v-for="p in [...(auth.me?.permissions ?? [])].sort()" :key="p" variant="secondary" class="font-mono text-xs">{{ p }}</Badge>
        </div>
      </CardContent>
    </Card>
    <Card class="lg:col-span-2">
      <CardHeader><CardTitle class="text-base">Faol sessiyalar</CardTitle><CardDescription>Boshqa qurilmadagi sessiyani yakunlash mumkin</CardDescription></CardHeader>
      <CardContent>
        <Table>
          <TableHeader><TableRow><TableHead>Qurilma</TableHead><TableHead>IP</TableHead><TableHead>Oxirgi faollik</TableHead><TableHead class="bg-card sticky right-0" /></TableRow></TableHeader>
          <TableBody>
            <TableRow v-for="s in sessions" :key="s.id">
              <TableCell class="max-w-64 truncate" :title="s.device">{{ s.device }}</TableCell>
              <TableCell>{{ s.ip }}</TableCell>
              <TableCell class="text-muted-foreground">{{ fmtDateTime(s.lastSeenAt) }}</TableCell>
              <TableCell class="bg-card sticky right-0 text-right">
                <StatusBadge v-if="s.current" tone="green" label="Joriy" />
                <Button v-else variant="ghost" size="sm" @click="revoke(s)"><LogOut /> Yakunlash</Button>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  </div>
</template>
