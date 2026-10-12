<script setup lang="ts">
import { Star } from '@lucide/vue'

// Faqat ko'rsatish: baho 1–5 (null — baholanmagan)
const props = withDefaults(defineProps<{ value: number | null | undefined; size?: string }>(), { size: 'size-3.5' })
</script>

<template>
  <span v-if="props.value" class="inline-flex items-center gap-0.5" :title="`${props.value} / 5`">
    <Star v-for="i in 5" :key="i" :class="[props.size, i <= Math.round(props.value) ? 'fill-amber-400 text-amber-400' : 'text-muted-foreground/30']" />
  </span>
  <span v-else class="text-muted-foreground text-xs">—</span>
</template>
