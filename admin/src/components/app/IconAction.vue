<script setup lang="ts">
import type { Component } from 'vue'
import { RouterLink } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

/**
 * Faqat ikonkali tugma + tooltip (jadval qatoridagi amallar). label — tooltip va aria-label (ekran o'quvchi uchun).
 * to — ichki havola, href — tashqi (yangi oynada); ikkalasi yo'q — oddiy tugma (click).
 * Qator bosilganda sahifa ochiladi — shuning uchun click qatorgacha ko'tarilmaydi (.stop).
 */
defineProps<{ icon: Component; label: string; to?: string; href?: string; disabled?: boolean }>()
const emit = defineEmits<{ click: [] }>()
</script>

<template>
  <Tooltip>
    <TooltipTrigger as-child>
      <Button v-if="to && !disabled" variant="ghost" size="icon-sm" :aria-label="label" as-child @click.stop>
        <RouterLink :to="to"><component :is="icon" /></RouterLink>
      </Button>
      <Button v-else-if="href && !disabled" variant="ghost" size="icon-sm" :aria-label="label" as-child @click.stop>
        <a :href="href" target="_blank" rel="noopener noreferrer"><component :is="icon" /></a>
      </Button>
      <!-- disabled Button'da pointer-events yo'q: bosish qatorga o'tib sahifani ochardi va tooltip chiqmasdi — o'ramda ushlaymiz -->
      <span v-else-if="disabled" tabindex="0" class="inline-flex" :aria-label="label" @click.stop>
        <Button variant="ghost" size="icon-sm" disabled tabindex="-1"><component :is="icon" /></Button>
      </span>
      <Button v-else variant="ghost" size="icon-sm" :aria-label="label" @click.stop="emit('click')">
        <component :is="icon" />
      </Button>
    </TooltipTrigger>
    <TooltipContent>{{ label }}</TooltipContent>
  </Tooltip>
</template>
