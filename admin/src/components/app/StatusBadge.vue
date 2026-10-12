<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

// Rang + matn (rang-ko'r uchun faqat rangga tayanmaymiz)
const props = defineProps<{ tone: 'green' | 'amber' | 'red' | 'blue' | 'gray' | 'violet'; label: string }>()
const cls = computed(() => ({
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20 dark:bg-emerald-500/10 dark:text-emerald-400',
  amber: 'bg-amber-50 text-amber-700 ring-amber-600/20 dark:bg-amber-500/10 dark:text-amber-400',
  red: 'bg-red-50 text-red-700 ring-red-600/20 dark:bg-red-500/10 dark:text-red-400',
  blue: 'bg-sky-50 text-sky-700 ring-sky-600/20 dark:bg-sky-500/10 dark:text-sky-400',
  violet: 'bg-violet-50 text-violet-700 ring-violet-600/20 dark:bg-violet-500/10 dark:text-violet-400',
  gray: 'bg-zinc-50 text-zinc-600 ring-zinc-500/20 dark:bg-zinc-500/10 dark:text-zinc-400',
})[props.tone])
</script>

<template>
  <span :class="cn('inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium ring-1 ring-inset whitespace-nowrap', cls)">
    {{ props.label }}
  </span>
</template>
