<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useDocumentVisibility, useIntervalFn } from '@vueuse/core'
import { toast } from 'vue-sonner'
import { Clock, Eye, ImageIcon, Loader2, MessageCircleReply, PartyPopper, Search } from '@lucide/vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import Paginator from '@/components/app/Paginator.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import ChatPanel from '@/components/feedback/ChatPanel.vue'
import { feedbackApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Feedback, Page } from '@/api/types'
import { avatarClass, initials, kindTone, waitTone, waited, who } from '@/lib/feedback'
import { FEEDBACK_KIND, LANG, P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

/** Navbat: hali hech kim olmagan murojaatlar — eng uzoq kutgani tepada */
const emit = defineEmits<{ claimed: [number] }>()
const auth = useAuth()
const ALL = 'ALL'
const f = reactive({ kind: ALL, q: '' })
const page = ref(0)
const data = ref<Page<Feedback> | null>(null)
const now = ref(Date.now())
const claiming = ref<number | null>(null)
const preview = ref<number | null>(null)

async function load() {
  try {
    data.value = await feedbackApi.search({
      status: 'NEW', kind: f.kind === ALL ? undefined : f.kind, q: f.q.trim() || undefined,
      page: page.value, size: 20, sort: 'createdDate,asc',
    })
    now.value = Date.now()
  } catch (e) {
    toast.error(errorMessage(e))
  }
}

let timer: ReturnType<typeof setTimeout> | undefined
watch(() => f.kind, () => { page.value = 0; load() })
watch(() => f.q, () => { clearTimeout(timer); timer = setTimeout(() => { page.value = 0; load() }, 350) })
watch(page, load)
onMounted(load)

const visibility = useDocumentVisibility()
let tick = 0
const { pause } = useIntervalFn(() => { if (visibility.value === 'visible' || ++tick % 3 === 0) load() }, 10000)
onBeforeUnmount(pause)

async function claim(x: Feedback) {
  claiming.value = x.id
  try {
    await feedbackApi.claim(x.id)
    window.dispatchEvent(new Event('feedback-changed'))
    emit('claimed', x.id)
  } catch (e) {
    toast.error(errorMessage(e))           // 409 — boshqa operator oldi
    load()
  } finally {
    claiming.value = null
  }
}

function onPreviewChanged(x: Feedback) {
  if (x.status !== 'NEW') {                // ko'rish oynasidan olindi yoki yopildi
    preview.value = null
    if (x.status === 'OPEN' && x.assigneeId === auth.me?.id) emit('claimed', x.id)
    else load()
  }
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap gap-2">
      <div class="relative w-full sm:w-72">
        <Search class="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
        <Input v-model="f.q" placeholder="Fermer yoki xabar…" class="pl-8" />
      </div>
      <Select v-model="f.kind">
        <SelectTrigger class="w-44"><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectItem :value="ALL">Barcha tur</SelectItem>
          <SelectItem v-for="(l, k) in FEEDBACK_KIND" :key="k" :value="k">{{ l }}</SelectItem>
        </SelectContent>
      </Select>
    </div>

    <div v-if="!data" class="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
      <Skeleton v-for="i in 6" :key="i" class="h-40 rounded-xl" />
    </div>

    <div v-else-if="!data.content.length" class="text-muted-foreground flex flex-col items-center gap-3 rounded-xl border border-dashed py-16 text-center">
      <PartyPopper class="size-10 opacity-50" />
      <p class="text-foreground font-medium">Navbat boʻsh</p>
      <p class="text-sm">Barcha murojaatlarga javob berilmoqda. Yangisi kelsa shu yerda paydo boʻladi.</p>
    </div>

    <div v-else class="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
      <article
        v-for="x in data.content" :key="x.id"
        class="bg-background group flex flex-col rounded-xl border p-4 shadow-xs transition-shadow hover:shadow-md"
      >
        <header class="flex items-start gap-3">
          <div :class="['flex size-10 shrink-0 items-center justify-center rounded-full text-sm font-semibold', avatarClass(x.farmerId)]">
            {{ initials(who(x)) }}
          </div>
          <div class="min-w-0 flex-1">
            <p class="truncate font-medium">{{ who(x) }}</p>
            <p class="text-muted-foreground text-xs">№{{ x.id }} · {{ LANG[x.farmerLanguage] ?? x.farmerLanguage }}<template v-if="x.unreadStaff > 1"> · {{ x.unreadStaff }} xabar</template></p>
          </div>
          <StatusBadge :tone="kindTone[x.kind]" :label="FEEDBACK_KIND[x.kind]" />
        </header>

        <p class="mt-3 line-clamp-3 flex-1 text-sm break-words">
          <ImageIcon v-if="['📷', '📎'].includes(x.subject)" class="text-muted-foreground mr-1 inline size-4" />{{ ['📷', '📎'].includes(x.subject) ? 'Fayl yubordi' : x.subject }}
        </p>

        <footer class="mt-4 flex items-center justify-between gap-2">
          <span :class="['flex items-center gap-1 text-xs font-medium whitespace-nowrap', waitTone(x.createdDate, now)]" title="Navbatda kutmoqda">
            <Clock class="size-3.5" /> {{ waited(x.createdDate, now) }}
          </span>
          <div class="flex gap-1.5">
            <Button size="sm" variant="ghost" aria-label="Koʻrish" @click="preview = x.id"><Eye /></Button>
            <Button v-if="auth.can(P.FEEDBACK_REPLY)" size="sm" :disabled="claiming !== null" @click="claim(x)">
              <Loader2 v-if="claiming === x.id" class="animate-spin" /><MessageCircleReply v-else /> Javob berish
            </Button>
          </div>
        </footer>
      </article>
    </div>

    <Paginator v-if="data && data.totalPages > 1" :page="data.page" :total-pages="data.totalPages" :total-elements="data.totalElements" @change="page = $event" />

    <Sheet :open="preview !== null" @update:open="(v: boolean) => { if (!v) preview = null }">
      <SheetContent :show-close-button="false" class="flex w-full flex-col gap-0 p-0 sm:max-w-xl">
        <SheetHeader class="sr-only"><SheetTitle>Murojaat</SheetTitle><SheetDescription>Suhbatni koʻrish</SheetDescription></SheetHeader>
        <ChatPanel v-if="preview" :id="preview" closable class="flex-1" @changed="onPreviewChanged" @back="preview = null" @gone="preview = null; load()" />
      </SheetContent>
    </Sheet>
  </div>
</template>
