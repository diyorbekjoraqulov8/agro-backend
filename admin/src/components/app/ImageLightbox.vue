<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, Download, ExternalLink, X, ZoomIn, ZoomOut } from '@lucide/vue'

/**
 * Rasmni sahifaning o'zida katta ko'rsatish. v-model:index — ochiq rasm (null — yopiq).
 * Klaviatura: Esc — yopish, ←/→ — oldingi/keyingi. Rasmni bosish — asl o'lcham / ekranga sig'dirish.
 */
interface LightboxImage { url: string; name: string; caption?: string }

const props = defineProps<{ images: LightboxImage[] }>()
const index = defineModel<number | null>('index', { default: null })

const zoom = ref(false)
const loaded = ref(false)
const cur = computed(() => (index.value === null ? null : props.images[index.value] ?? null))
const many = computed(() => props.images.length > 1)

function close() { index.value = null }
function go(d: number) {
  if (index.value === null || !many.value) return
  index.value = (index.value + d + props.images.length) % props.images.length
}

function onKey(e: KeyboardEvent) {
  if (index.value === null) return
  if (e.key === 'Escape') close()
  else if (e.key === 'ArrowLeft') go(-1)
  else if (e.key === 'ArrowRight') go(1)
  else return
  e.preventDefault()
  // Ochiq modal (Sheet/Dialog) ham Esc'ni tinglaydi — u emas, faqat lightbox yopilsin
  e.stopImmediatePropagation()
}

// Ochiq bo'lganda sahifa orqada aylanmasin; klaviatura faqat ochiq paytda tinglanadi
watch(() => index.value !== null, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
  // capture — modal'ning document tinglovchisidan OLDIN
  if (open) window.addEventListener('keydown', onKey, true)
  else window.removeEventListener('keydown', onKey, true)
})
watch(index, () => { zoom.value = false; loaded.value = false })
onBeforeUnmount(() => { document.body.style.overflow = ''; window.removeEventListener('keydown', onKey, true) })
</script>

<template>
  <Teleport to="body">
    <Transition enter-active-class="transition duration-150" enter-from-class="opacity-0" leave-active-class="transition duration-100" leave-to-class="opacity-0">
      <!-- pointer-events-auto: modal ochiq bo'lsa body'ga pointer-events:none qo'yiladi -->
      <div v-if="cur" class="pointer-events-auto fixed inset-0 z-[100] flex flex-col bg-black/90 text-white" role="dialog" aria-modal="true" :aria-label="cur.name">
        <!-- Yuqori panel -->
        <div class="flex items-center gap-2 px-3 py-2 sm:px-4">
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium">{{ cur.name }}</p>
            <p class="truncate text-xs text-white/60">
              <template v-if="many">{{ (index ?? 0) + 1 }} / {{ images.length }}</template>
              <template v-if="many && cur.caption"> · </template>{{ cur.caption }}
            </p>
          </div>
          <button type="button" class="rounded-full p-2 hover:bg-white/10" :title="zoom ? 'Ekranga sigʻdirish' : 'Asl oʻlcham'" @click="zoom = !zoom">
            <ZoomOut v-if="zoom" class="size-5" /><ZoomIn v-else class="size-5" />
          </button>
          <a :href="cur.url" target="_blank" rel="noopener" class="rounded-full p-2 hover:bg-white/10" title="Yangi tabda ochish"><ExternalLink class="size-5" /></a>
          <a :href="cur.url" :download="cur.name" class="rounded-full p-2 hover:bg-white/10" title="Yuklab olish"><Download class="size-5" /></a>
          <button type="button" class="rounded-full p-2 hover:bg-white/10" title="Yopish (Esc)" @click="close"><X class="size-5" /></button>
        </div>

        <!-- Rasm: fonni bosish — yopish -->
        <div :class="['relative flex min-h-0 flex-1 p-2 sm:p-6', zoom ? 'overflow-auto' : 'items-center justify-center overflow-hidden']" @click.self="close">
          <div v-if="!loaded" class="pointer-events-none absolute inset-0 flex items-center justify-center">
            <div class="size-8 animate-spin rounded-full border-2 border-white/30 border-t-white" />
          </div>
          <img
            :key="cur.url" :src="cur.url" :alt="cur.name"
            :class="['select-none', zoom ? 'm-auto max-w-none cursor-zoom-out' : 'max-h-full max-w-full cursor-zoom-in object-contain',
                     loaded ? 'opacity-100' : 'opacity-0', 'transition-opacity duration-150']"
            draggable="false" @load="loaded = true" @click="zoom = !zoom"
          />
          <template v-if="many">
            <button type="button" class="absolute top-1/2 left-2 -translate-y-1/2 rounded-full bg-black/40 p-2 hover:bg-black/70 sm:left-4" title="Oldingi (←)" @click.stop="go(-1)">
              <ChevronLeft class="size-6" />
            </button>
            <button type="button" class="absolute top-1/2 right-2 -translate-y-1/2 rounded-full bg-black/40 p-2 hover:bg-black/70 sm:right-4" title="Keyingi (→)" @click.stop="go(1)">
              <ChevronRight class="size-6" />
            </button>
          </template>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
