<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Info } from '@lucide/vue'
import { toast } from 'vue-sonner'
import PageHeader from '@/components/app/PageHeader.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { usersApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { UserResponse } from '@/api/types'
import { fmtDate } from '@/lib/format'

const users = ref<UserResponse[]>([])
onMounted(async () => {
  try { users.value = await usersApi.list() } catch (e) { toast.error(errorMessage(e)) }
})
</script>

<template>
  <PageHeader title="Xodimlar" description="Admin panelga kiradigan foydalanuvchilar (fermerlar emas)" />
  <Alert class="mb-4">
    <Info />
    <AlertDescription>Backend’da hozircha xodim yaratish va rol biriktirish API’si yoʻq — roʻyxat faqat koʻrish uchun.</AlertDescription>
  </Alert>
  <Card>
    <CardContent>
      <Table>
        <TableHeader><TableRow><TableHead>Email</TableHead><TableHead>Rollar</TableHead><TableHead>Holat</TableHead><TableHead>Qoʻshilgan</TableHead></TableRow></TableHeader>
        <TableBody>
          <TableRow v-for="u in users" :key="u.id">
            <TableCell class="font-medium">{{ u.email }}</TableCell>
            <TableCell class="space-x-1"><Badge v-for="r in u.roles" :key="r" variant="secondary">{{ r }}</Badge></TableCell>
            <TableCell><StatusBadge :tone="u.active ? 'green' : 'gray'" :label="u.active ? 'Faol' : 'Nofaol'" /></TableCell>
            <TableCell class="text-muted-foreground">{{ fmtDate(u.createdDate) }}</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </CardContent>
  </Card>
</template>
