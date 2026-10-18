<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { toast } from 'vue-sonner'
import { Loader2, Trash2, Upload } from '@lucide/vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { onboardingApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { TutorialVideo } from '@/api/types'
import { fmtDateTime } from '@/lib/format'

/**
 * Yo'riqnoma videosi — har til uchun bitta MP4 (≤ 10 MB). Bot yangi fermerga salomlashishdan keyin va ❓ Qo'llanma'da
 * fermer tilida yuboradi; til uchun video yo'q bo'lsa — o'zbekcha, keyin inglizcha.
 */
const props = defineProps<{ canEdit: boolean }>()
const LABEL: Record<string, string> = { uz: 'Oʻzbekcha', ru: 'Русский', en: 'English' }
const MAX_MB = 10

const videos = ref<TutorialVideo[] | null>(null)
const busy = ref<string | null>(null)
const playing = ref<string | null>(null)

async function load() {
  try { videos.value = await onboardingApi.videos() } catch (e) { toast.error(errorMessage(e)) }
}
onMounted(load)

async function upload(lang: string, ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > MAX_MB * 1024 * 1024) {
    toast.error(`Video ${(file.size / 1048576).toFixed(1)} MB — koʻpi bilan ${MAX_MB} MB. Pastdagi ffmpeg buyrugʻi bilan siqing`)
    return
  }
  busy.value = lang
  try {
    await onboardingApi.uploadVideo(lang, file)
    toast.success(`${LABEL[lang]} video yuklandi — keyingi fermerlarga shu boradi`)
    await load()
  } catch (e) { toast.error(errorMessage(e)) } finally { busy.value = null }
}
async function remove(lang: string) {
  busy.value = lang
  try {
    await onboardingApi.removeVideo(lang)
    toast.success('Video oʻchirildi')
    await load()
  } catch (e) { toast.error(errorMessage(e)) } finally { busy.value = null }
}
const mb = (b: number | null) => (b == null ? '' : `${(b / 1048576).toFixed(1)} MB`)
</script>

<template>
  <Card class="mb-6">
    <CardHeader>
      <CardTitle class="text-base">Yoʻriqnoma videosi</CardTitle>
      <CardDescription>
        Yangi fermerga salomlashishdan keyin va «❓ Qoʻllanma»da fermer tilida yuboriladi. MP4, koʻpi bilan {{ MAX_MB }} MB
        (30–60 soniya, 720p — odatda 3–6 MB). Til uchun video boʻlmasa — oʻzbekchasi yuboriladi.
      </CardDescription>
    </CardHeader>
    <CardContent class="space-y-3">
      <div class="grid gap-3 md:grid-cols-3">
        <div v-for="v in videos ?? []" :key="v.language" class="rounded-lg border p-3 text-sm">
          <div class="flex items-center justify-between gap-2">
            <span class="font-medium">{{ LABEL[v.language] }}</span>
            <StatusBadge :tone="v.fileHashId ? 'green' : 'gray'" :label="v.fileHashId ? 'Yuklangan' : 'Yoʻq'" />
          </div>
          <template v-if="v.fileHashId">
            <p class="text-muted-foreground mt-1 truncate text-xs" :title="v.name ?? ''">{{ v.name }} · {{ mb(v.size) }}</p>
            <p class="text-muted-foreground text-xs">{{ fmtDateTime(v.updatedDate) }} · {{ v.updatedByEmail }}</p>
            <video v-if="playing === v.language" :src="`/api/v1/files/${v.fileHashId}`" controls class="mt-2 w-full rounded-md border" />
            <Button v-else size="sm" variant="ghost" class="mt-1 h-7 px-2" @click="playing = v.language">▶ Koʻrish</Button>
          </template>
          <div v-if="props.canEdit" class="mt-2 flex gap-2">
            <label :class="['inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-md border px-3 text-xs font-medium hover:bg-muted', busy && 'pointer-events-none opacity-50']">
              <Loader2 v-if="busy === v.language" class="size-3.5 animate-spin" /><Upload v-else class="size-3.5" />
              {{ v.fileHashId ? 'Almashtirish' : 'Yuklash' }}
              <input type="file" accept="video/mp4" class="hidden" @change="upload(v.language, $event)" />
            </label>
            <Button v-if="v.fileHashId" size="sm" variant="ghost" class="h-8 text-red-600" :disabled="!!busy" @click="remove(v.language)">
              <Trash2 class="size-3.5" />
            </Button>
          </div>
        </div>
      </div>
      <details class="text-muted-foreground text-xs">
        <summary class="cursor-pointer">Videoni kichraytirish (ffmpeg)</summary>
        <pre class="bg-muted/50 mt-2 overflow-x-auto rounded-md p-2">ffmpeg -i video.mov -vf "scale=-2:720,fps=30" -c:v libx264 -crf 28 -preset slow -c:a aac -b:a 64k -movflags +faststart dropy-uz.mp4</pre>
      </details>
    </CardContent>
  </Card>
</template>
