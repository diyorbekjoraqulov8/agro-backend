<script setup lang="ts">
import { ref, watch } from 'vue'
import { Check, Minus, X } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { accuracyApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Review, ReviewTarget, ReviewVerdict } from '@/api/types'
import { REVIEW } from '@/lib/labels'

/** L2: tizim xulosasiga xodim bahosi. Faol tugmani qayta bosish — bahoni olib tashlaydi; izoh — ixtiyoriy, blur'da saqlanadi */
const props = defineProps<{ type: ReviewTarget; id: number; review: Review | null; readonly?: boolean }>()
const current = ref<Review | null>(props.review)
const note = ref(props.review?.note ?? '')
const busy = ref(false)
watch(() => props.review, (r) => { current.value = r; note.value = r?.note ?? '' })

const OPTS: { v: ReviewVerdict; icon: unknown; on: string }[] = [
  { v: 'CORRECT', icon: Check, on: 'border-green-600 bg-green-600 text-white' },
  { v: 'PARTIAL', icon: Minus, on: 'border-amber-500 bg-amber-500 text-white' },
  { v: 'WRONG', icon: X, on: 'border-red-600 bg-red-600 text-white' },
]

// Izohdan tugmaga bosilganda avval blur keladi — izohni darhol emas, biroz kutib saqlaymiz; tugma uni bekor qiladi
// va izohni o'z so'roviga qo'shadi (aks holda ikki PUT/DELETE tartibsiz yetib, baho qaytib tirilardi)
let noteTimer: ReturnType<typeof setTimeout> | undefined

async function set(v: ReviewVerdict) {
  clearTimeout(noteTimer)
  if (busy.value) return
  busy.value = true
  try {
    if (current.value?.verdict === v) {
      await accuracyApi.removeReview(props.type, props.id)
      current.value = null
      note.value = ''
    } else {
      current.value = await accuracyApi.review(props.type, props.id, { verdict: v, note: note.value.trim() || null })
    }
  } catch (e) {
    toast.error(errorMessage(e))
  } finally {
    busy.value = false
  }
}

function scheduleNote() {
  clearTimeout(noteTimer)
  noteTimer = setTimeout(saveNote, 200)
}

async function saveNote() {
  if (busy.value || !current.value || (current.value.note ?? '') === note.value.trim()) return
  busy.value = true
  try {
    current.value = await accuracyApi.review(props.type, props.id, { verdict: current.value.verdict, note: note.value.trim() || null })
  } catch (e) {
    toast.error(errorMessage(e))
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="flex flex-wrap items-center gap-1.5">
    <span class="text-muted-foreground text-xs">Xulosa:</span>
    <template v-if="!readonly">
      <button v-for="o in OPTS" :key="o.v" type="button" :aria-pressed="current?.verdict === o.v" :title="REVIEW[o.v]"
              :aria-label="REVIEW[o.v]" :disabled="busy"
              class="grid size-6 place-items-center rounded-md border transition-colors disabled:opacity-50"
              :class="current?.verdict === o.v ? o.on : 'text-muted-foreground hover:bg-accent'" @click="set(o.v)">
        <component :is="o.icon" class="size-3.5" />
      </button>
      <input v-if="current" v-model="note" maxlength="1000" placeholder="Izoh: aslida nima edi?"
             class="placeholder:text-muted-foreground h-6 min-w-40 flex-1 rounded-md border bg-transparent px-2 text-xs outline-none focus:border-ring"
             @blur="scheduleNote" @keydown.enter="($event.target as HTMLInputElement).blur()" />
    </template>
    <span v-else class="text-xs">{{ current ? REVIEW[current.verdict] : 'baholanmagan' }}<template v-if="current?.note"> — {{ current.note }}</template></span>
  </div>
</template>
