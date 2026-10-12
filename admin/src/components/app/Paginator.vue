<script setup lang="ts">
import { ChevronLeft, ChevronRight } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { fmtNum } from '@/lib/format'

const props = defineProps<{ page: number; totalPages: number; totalElements: number }>()
const emit = defineEmits<{ change: [page: number] }>()
</script>

<template>
  <div class="text-muted-foreground flex items-center justify-between gap-2 px-1 pt-3 text-sm">
    <span>Jami: {{ fmtNum(props.totalElements) }}</span>
    <div class="flex items-center gap-2">
      <span>{{ props.totalPages ? props.page + 1 : 0 }} / {{ props.totalPages }}</span>
      <Button variant="outline" size="icon-sm" :disabled="props.page <= 0" @click="emit('change', props.page - 1)">
        <ChevronLeft />
      </Button>
      <Button variant="outline" size="icon-sm" :disabled="props.page + 1 >= props.totalPages" @click="emit('change', props.page + 1)">
        <ChevronRight />
      </Button>
    </div>
  </div>
</template>
